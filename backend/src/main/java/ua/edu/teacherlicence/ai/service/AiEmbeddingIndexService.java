package ua.edu.teacherlicence.ai.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import ua.edu.teacherlicence.achievement.model.Achievement;
import ua.edu.teacherlicence.achievement.service.AchievementService;
import ua.edu.teacherlicence.publication.model.Publication;
import ua.edu.teacherlicence.publication.service.PublicationService;
import ua.edu.teacherlicence.qualification.model.QualificationImprovement;
import ua.edu.teacherlicence.qualification.service.QualificationService;
import ua.edu.teacherlicence.teacher.model.CareerRecord;
import ua.edu.teacherlicence.teacher.model.Education;
import ua.edu.teacherlicence.teacher.model.LanguageSkill;
import ua.edu.teacherlicence.teacher.model.MilitaryEducation;
import ua.edu.teacherlicence.teacher.model.Teacher;
import ua.edu.teacherlicence.teacher.repository.CareerRecordRepository;
import ua.edu.teacherlicence.teacher.repository.EducationRepository;
import ua.edu.teacherlicence.teacher.repository.LanguageSkillRepository;
import ua.edu.teacherlicence.teacher.repository.MilitaryEducationRepository;
import ua.edu.teacherlicence.teacher.repository.TeacherRepository;
import ua.edu.teacherlicence.discipline.model.TeacherDiscipline;
import ua.edu.teacherlicence.discipline.repository.TeacherDisciplineRepository;
import ua.edu.teacherlicence.ppdata.model.AttestationActivity;
import ua.edu.teacherlicence.ppdata.model.EditorialActivity;
import ua.edu.teacherlicence.ppdata.model.ExpertCouncil;
import ua.edu.teacherlicence.ppdata.model.ForeignLanguageTeaching;
import ua.edu.teacherlicence.ppdata.model.InternationalProject;
import ua.edu.teacherlicence.ppdata.model.MilitaryMission;
import ua.edu.teacherlicence.ppdata.model.OlympiadGuidance;
import ua.edu.teacherlicence.ppdata.model.PracticalExperience;
import ua.edu.teacherlicence.ppdata.model.ProfessionalAssociation;
import ua.edu.teacherlicence.ppdata.model.ScientificConsulting;
import ua.edu.teacherlicence.ppdata.model.ScientificSupervision;
import ua.edu.teacherlicence.ppdata.repository.AttestationActivityRepository;
import ua.edu.teacherlicence.ppdata.repository.EditorialActivityRepository;
import ua.edu.teacherlicence.ppdata.repository.ExpertCouncilRepository;
import ua.edu.teacherlicence.ppdata.repository.ForeignLanguageTeachingRepository;
import ua.edu.teacherlicence.ppdata.repository.InternationalProjectRepository;
import ua.edu.teacherlicence.ppdata.repository.MilitaryMissionRepository;
import ua.edu.teacherlicence.ppdata.repository.OlympiadGuidanceRepository;
import ua.edu.teacherlicence.ppdata.repository.PracticalExperienceRepository;
import ua.edu.teacherlicence.ppdata.repository.ProfessionalAssociationRepository;
import ua.edu.teacherlicence.ppdata.repository.ScientificConsultingRepository;
import ua.edu.teacherlicence.ppdata.repository.ScientificSupervisionRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * RAG-індексація: будує embedding-и профілів викладачів і зберігає їх у PGVector.
 *
 * Активний ТІЛЬКИ за умов:
 * - {@code ai.enabled=true} (глобальний AI toggle)
 * - {@code ai.rag.enabled=true} (локальний RAG toggle)
 * - Існує bean {@link VectorStore} (у dev профілі він не створюється → граційне відключення)
 *
 * Архітектура:
 * - Документ = повний текстовий профіль викладача (ПІБ, кафедра, досягнення, публікації,
 *   мови, курси, дисертація, бойовий досвід тощо).
 * - ID документа = "teacher-{id}" — deterministic → upsert = delete+add.
 * - metadata: teacherId, lastName, departmentId — для подальшої фільтрації.
 *
 * Реіндексація:
 * - При старті програми (@EventListener ApplicationReadyEvent) — async, щоб не блокувати startup.
 * - За розкладом (@Scheduled, інтервал з application.yml).
 * - Явно через метод {@link #indexTeacher(Long)} / {@link #reindexAll()} (викликається з endpoint).
 */
/**
 * ВАЖЛИВА ПРИМІТКА щодо життєвого циклу:
 *
 * НЕ використовуємо @ConditionalOnBean(VectorStore.class) — ця анотація ненадійна
 * для user-beans (документація Spring Boot: "strongly recommended to use this condition
 * on auto-configuration classes only"). User-bean може оцінюватись до того, як VectorStore
 * з auto-configuration зареєстрований у контексті → bean не створюється → тиша в логах.
 *
 * Натомість використовуємо ObjectProvider<VectorStore>:
 * - prod (pgvector autoconfig активний) → provider.getIfAvailable() повертає VectorStore
 * - dev (autoconfig exclude-нуто) → getIfAvailable() повертає null → всі операції no-op
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiEmbeddingIndexService {

    @Value("${ai.rag.enabled:false}")
    private boolean ragEnabled;

    /**
     * PGVector у Spring AI M6 жорстко вимагає UUID формат для document.id.
     * Генеруємо deterministic UUID від teacherId (name-based, type 3 UUID) —
     * однаковий teacherId → однаковий UUID → upsert (delete+add) працює коректно.
     */
    private static String docIdFor(Long teacherId) {
        return UUID.nameUUIDFromBytes(("teacher-" + teacherId).getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static final int BATCH_SIZE = 20;
    private static final int MAX_PUBS_PER_TEACHER = 20;
    private static final int MAX_QUALS_PER_TEACHER = 20;
    private static final int DEFAULT_TOP_K = 8;
    /**
     * Поріг cosine-similarity для семантичного пошуку.
     * Mistral-embed + профілі викладачів: спостереження показало що 0.35 пропускає забагато шуму
     * (частковий матч за словом "дисертація" у запиті "дисертація про ШІ" захоплює всіх з дисертаціями).
     * 0.55 — емпірично балансує: зазвичай повертає 1-5 справді релевантних; пусто — означає "не знайдено".
     */
    private static final double SIMILARITY_THRESHOLD = 0.55;

    private final ObjectProvider<VectorStore> vectorStoreProvider;
    private final TeacherRepository teacherRepository;
    private final ua.edu.teacherlicence.teacher.service.TeacherPositionService teacherPositionService;
    private final AchievementService achievementService;
    private final PublicationService publicationService;
    private final QualificationService qualificationService;
    private final LanguageSkillRepository languageSkillRepository;
    private final ua.edu.teacherlicence.teacher.repository.AcademicDegreeRepository academicDegreeRepository;
    private final ua.edu.teacherlicence.teacher.repository.AcademicTitleRepository academicTitleRepository;
    private final CareerRecordRepository careerRecordRepository;
    private final EducationRepository educationRepository;
    private final MilitaryEducationRepository militaryEducationRepository;
    private final TeacherDisciplineRepository teacherDisciplineRepository;
    private final ScientificSupervisionRepository scientificSupervisionRepository;
    private final AttestationActivityRepository attestationActivityRepository;
    private final EditorialActivityRepository editorialActivityRepository;
    private final ExpertCouncilRepository expertCouncilRepository;
    private final InternationalProjectRepository internationalProjectRepository;
    private final ScientificConsultingRepository scientificConsultingRepository;
    private final ForeignLanguageTeachingRepository foreignLanguageTeachingRepository;
    private final OlympiadGuidanceRepository olympiadGuidanceRepository;
    private final MilitaryMissionRepository militaryMissionRepository;
    private final ProfessionalAssociationRepository professionalAssociationRepository;
    private final PracticalExperienceRepository practicalExperienceRepository;

    /**
     * Self-injection через @Lazy — потрібно щоб виклик this.reindexAll() проходив
     * через AOP proxy і @Transactional насправді активувався.
     * Без цього — internal call минає proxy → no Hibernate session →
     * LazyInitializationException при доступі до t.getDepartment().
     */
    @Autowired
    @Lazy
    private AiEmbeddingIndexService self;

    /**
     * Перевірка чи сервіс повноцінно функціональний (є RAG-прапорець + VectorStore).
     * Використовується ззовні (AiController, AiToolsService) щоб відрізнити
     * "prod з pgvector" від "dev без vector store".
     */
    public boolean isAvailable() {
        return ragEnabled && vectorStoreProvider.getIfAvailable() != null;
    }

    // ═══════════════════════════════════════════════════════════════
    //  Lifecycle
    // ═══════════════════════════════════════════════════════════════

    /**
     * Діагностика: підтвердження що bean створено.
     * Має з'явитись ЗАВЖДИ у логах на старті — bean не має Conditional, створюється безумовно.
     */
    @PostConstruct
    public void init() {
        VectorStore vs = vectorStoreProvider.getIfAvailable();
        log.info("RAG: AiEmbeddingIndexService bean initialized. ragEnabled={}, vectorStore={}",
                ragEnabled, vs != null ? vs.getClass().getSimpleName() : "NOT AVAILABLE");
    }

    /**
     * Первинна індексація після повного старту програми.
     * Async — не блокує startup. Якщо викладачів багато, це може зайняти кілька секунд на API calls.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Async
    public void onStartup() {
        if (!ragEnabled) {
            log.info("RAG: disabled (ai.rag.enabled=false) — skipping initial indexing");
            return;
        }
        if (vectorStoreProvider.getIfAvailable() == null) {
            log.info("RAG: VectorStore bean not available — skipping initial indexing (dev profile?)");
            return;
        }
        log.info("RAG: starting initial indexing of teachers …");
        try {
            // Через self-proxy — щоб @Transactional реально активувалась
            // (інакше internal call не проходить через AOP → no session → LazyInitEx)
            int indexed = self.reindexAll();
            log.info("RAG: initial indexing complete — {} teachers indexed", indexed);
        } catch (Exception e) {
            log.error("RAG: initial indexing failed (will retry via scheduled reindex)", e);
        }
    }

    /**
     * Періодична повна реіндексація (default 5 хв).
     * initialDelay = інтервал, щоб перший запуск не конкурував з onStartup().
     */
    @Scheduled(
            fixedDelayString = "${ai.rag.reindex-interval-ms:300000}",
            initialDelayString = "${ai.rag.reindex-interval-ms:300000}"
    )
    public void scheduledReindex() {
        if (!isAvailable()) return;
        try {
            int indexed = self.reindexAll();  // через proxy — див. коментар в onStartup()
            log.debug("RAG: scheduled reindex — {} teachers", indexed);
        } catch (Exception e) {
            log.warn("RAG: scheduled reindex failed", e);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Public API
    // ═══════════════════════════════════════════════════════════════

    /**
     * Повна реіндексація всіх викладачів. Обробка батчами.
     *
     * Архітектура транзакцій:
     * - Самий метод БЕЗ @Transactional — він — координатор, не виконує ні читання, ні запису сам.
     * - buildDocumentsForBatch(ids) — @Transactional(readOnly=true) — читає викладачів з JPA
     *   та їхні lazy-колекції; повертає уже збудовані Document-и (detached — lazy вже ініціалізовано).
     * - writeToVectorStore(docs) — БЕЗ Spring tx — pgvector.delete/add. Кожен виклик PgVectorStore
     *   сам керує своєю JDBC-транзакцією через JdbcTemplate (auto-commit).
     *
     * Чому так: раніше readOnly tx обгортала і JPA-читання, і pgvector-запис → DELETE/INSERT
     * у read-only tx → 25P02 "cannot execute DELETE in a read-only transaction".
     *
     * @return кількість проіндексованих викладачів
     */
    public int reindexAll() {
        // Зчитуємо тільки ID (окрема неявна read-only tx від Spring Data)
        List<Long> allIds = teacherRepository.findAll().stream()
                .map(Teacher::getId).toList();
        int total = 0;
        for (int i = 0; i < allIds.size(); i += BATCH_SIZE) {
            List<Long> batchIds = allIds.subList(i, Math.min(i + BATCH_SIZE, allIds.size()));
            try {
                // Читання JPA + lazy collections — через self-proxy для @Transactional активації
                List<Document> docs = self.buildDocumentsForBatch(batchIds);
                if (!docs.isEmpty()) {
                    writeToVectorStore(docs); // БЕЗ Spring tx
                    total += docs.size();
                }
            } catch (Exception e) {
                log.error("RAG: failed to index batch starting at {}: {}", i, e.getMessage(), e);
                // Продовжуємо — не зупиняємо всю реіндексацію
            }
        }
        return total;
    }

    /**
     * Індексація одного викладача (upsert). Використовується endpoint-ом для точкового оновлення.
     */
    public void indexTeacher(Long teacherId) {
        try {
            List<Document> docs = self.buildDocumentsForBatch(List.of(teacherId));
            if (docs.isEmpty()) {
                log.warn("RAG: indexTeacher({}) — teacher not found, removing from index", teacherId);
                deleteTeacher(teacherId);
                return;
            }
            writeToVectorStore(docs);
        } catch (Exception e) {
            log.error("RAG: indexTeacher({}) failed: {}", teacherId, e.getMessage(), e);
        }
    }

    /**
     * Читає викладачів за ID + їхні lazy-колекції та будує Document-и для embedding-у.
     * ЦЕЙ метод виконується в readOnly JPA-транзакції (завдяки @Transactional + self-proxy).
     *
     * ВАЖЛИВО: повертає Document-и з уже матеріалізованим текстом — lazy-колекції доступу
     * більше не потрібні поза цією транзакцією.
     */
    @Transactional(readOnly = true)
    public List<Document> buildDocumentsForBatch(List<Long> teacherIds) {
        if (teacherIds == null || teacherIds.isEmpty()) return Collections.emptyList();
        List<Teacher> teachers = teacherRepository.findAllById(teacherIds);
        List<Document> docs = new ArrayList<>(teachers.size());
        for (Teacher t : teachers) {
            Document doc = buildDocument(t);
            if (doc != null) docs.add(doc);
        }
        return docs;
    }

    /**
     * Видалити викладача з vector store. Викликається при hard-delete викладача.
     */
    public void deleteTeacher(Long teacherId) {
        VectorStore vs = vectorStoreProvider.getIfAvailable();
        if (vs == null) return;
        try {
            vs.delete(List.of(docIdFor(teacherId)));
        } catch (Exception e) {
            log.warn("RAG: delete failed for teacherId={}", teacherId, e);
        }
    }

    /**
     * Семантичний пошук викладачів за природно-мовним запитом.
     * Використовується @Tool-ом {@code semanticSearchTeachers}.
     */
    public List<Document> search(String query, int topK) {
        if (query == null || query.isBlank()) return Collections.emptyList();
        VectorStore vs = vectorStoreProvider.getIfAvailable();
        if (vs == null) return Collections.emptyList();
        try {
            return vs.similaritySearch(
                    SearchRequest.builder()
                            .query(query)
                            .topK(topK > 0 ? topK : DEFAULT_TOP_K)
                            .similarityThreshold(SIMILARITY_THRESHOLD)
                            .build()
            );
        } catch (Exception e) {
            log.error("RAG: similaritySearch failed for query='{}'", query, e);
            return Collections.emptyList();
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Internals
    // ═══════════════════════════════════════════════════════════════

    /**
     * Запис Document-ів у pgvector (upsert). БЕЗ Spring tx — PgVectorStore сам керує JDBC tx.
     * Якщо викликати в межах readOnly Spring tx — впаде з "cannot DELETE in read-only transaction".
     */
    private void writeToVectorStore(List<Document> docs) {
        if (docs.isEmpty()) return;
        VectorStore vs = vectorStoreProvider.getIfAvailable();
        if (vs == null) return;

        List<String> ids = docs.stream().map(Document::getId).toList();
        // Upsert: delete старі ID → add нові. delete() для відсутніх ID — ігноруємо.
        try {
            vs.delete(ids);
        } catch (Exception ignored) {
            // Normal on first run when IDs don't exist yet
        }
        vs.add(docs);
    }

    /**
     * Побудова текстового профілю викладача для embedding-у.
     * Важливо:
     * - ТЕКСТ має бути природньою мовою, з синонімами — допомагає семантичному пошуку.
     * - Усі поля з лексемами для пошуку: "АТО", "ООС", "дисертація", "NATO" і т.д.
     * - Обмежуємо довжину (публікації/курси) щоб не перевищити input limit embedding-моделі.
     */
    private Document buildDocument(Teacher t) {
        if (t == null || t.getId() == null) return null;

        StringBuilder sb = new StringBuilder(1024);
        sb.append("Профіль викладача ВНЗ.\n");
        sb.append("ПІБ: ").append(nvl(t.getLastName())).append(" ").append(nvl(t.getFirstName()));
        if (t.getPatronymic() != null) sb.append(" ").append(t.getPatronymic());
        sb.append("\n");

        if (t.getDateOfBirth() != null) sb.append("Дата народження: ")
                .append(t.getDateOfBirth().format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy")))
                .append("\n");
        if (t.getMilitaryRank() != null) sb.append("Військове звання: ").append(t.getMilitaryRank()).append("\n");
        String effPosition = teacherPositionService.getEffectivePosition(t);
        if (effPosition != null && !effPosition.isBlank()) sb.append("Посада: ").append(effPosition).append("\n");
        if (t.getEmploymentType() != null)
            sb.append("Тип зайнятості: ").append("MAIN".equals(t.getEmploymentType()) ? "основне місце" : "сумісник").append("\n");
        if (t.getDepartment() != null) {
            sb.append("Кафедра: ").append(nvl(t.getDepartment().getName()));
            if (t.getDepartment().getNumber() != null) sb.append(" (№").append(t.getDepartment().getNumber()).append(")");
            if (t.getDepartment().getFaculty() != null)
                sb.append(", факультет: ").append(nvl(t.getDepartment().getFaculty().getName()));
            sb.append("\n");
        }
        if (t.getExperienceStartDate() != null)
            sb.append("Початок стажу: ").append(t.getExperienceStartDate()).append("\n");

        // ── Наукові ступені (ВСІ записи зі списку academic_degrees) ──
        var degrees = academicDegreeRepository.findByTeacherIdOrderByDiplomaDateAsc(t.getId());
        for (var d : degrees) {
            if (d.getDegree() == null || d.getDegree().isBlank()) continue;
            sb.append("Науковий ступінь: ").append(d.getDegree());
            if (d.getSpeciality() != null && !d.getSpeciality().isBlank())
                sb.append(", спеціальність: ").append(d.getSpeciality());
            if (d.getDissertationTopic() != null && !d.getDissertationTopic().isBlank())
                sb.append(", тема дисертації: ").append(d.getDissertationTopic());
            if (d.getDiplomaDate() != null)
                sb.append(" (").append(d.getDiplomaDate()).append(")");
            sb.append("\n");
        }

        // ── Вчені звання (ВСІ записи зі списку academic_titles) ──
        var titles = academicTitleRepository.findByTeacherIdOrderByAttestatDateAsc(t.getId());
        for (var at : titles) {
            if (at.getTitleName() == null || at.getTitleName().isBlank()) continue;
            sb.append("Вчене звання: ").append(at.getTitleName());
            if (at.getAttestatDate() != null)
                sb.append(" (").append(at.getAttestatDate()).append(")");
            sb.append("\n");
        }

        // ── Освіта (основний заклад — плоскі поля Teacher) ──
        if (t.getUniversity() != null) {
            sb.append("Освіта: ").append(t.getUniversity());
            if (t.getUniversitySpeciality() != null) sb.append(", спеціальність: ").append(t.getUniversitySpeciality());
            if (t.getUniversityGraduationYear() != null) sb.append(", ").append(t.getUniversityGraduationYear());
            sb.append("\n");
        }

        // ── Освіти (multi-record entity Education) ──
        List<Education> educations = safeEducations(t.getId());
        for (Education e : educations) {
            if (e.getInstitution() == null && e.getSpeciality() == null) continue;
            sb.append("Освіта: ");
            if (e.getDegree() != null) sb.append(e.getDegree()).append(", ");
            if (e.getInstitution() != null) sb.append(e.getInstitution());
            if (e.getSpeciality() != null) sb.append(", спеціальність: ").append(e.getSpeciality());
            if (e.getQualification() != null) sb.append(", кваліфікація: ").append(trunc(e.getQualification(), 150));
            if (e.getGraduationYear() != null) sb.append(" (").append(e.getGraduationYear()).append(")");
            sb.append("\n");
        }

        // ── Військова освіта (flat поля Teacher — primary) ──
        if (t.getMilitaryEducationLevel() != null) {
            sb.append("Військова освіта: ").append(t.getMilitaryEducationLevel().name());
            if (t.getMilitaryEducationDiplomaDate() != null)
                sb.append(", ").append(t.getMilitaryEducationDiplomaDate());
            sb.append("\n");
        }

        // ── Військові освіти (multi-record entity MilitaryEducation) ──
        List<MilitaryEducation> milEdus = safeMilitaryEducations(t.getId());
        for (MilitaryEducation me : milEdus) {
            sb.append("Військова освіта: ");
            if (me.getLevel() != null) sb.append(me.getLevel().name()).append(" рівень, ");
            if (me.getInstitution() != null) sb.append(me.getInstitution());
            if (me.getSpeciality() != null) sb.append(", ").append(trunc(me.getSpeciality(), 150));
            if (me.getGraduationYear() != null) sb.append(" (").append(me.getGraduationYear()).append(")");
            sb.append("\n");
        }

        // ── Послужний список (CareerRecord) ──
        List<CareerRecord> careers = safeCareerRecords(t.getId());
        if (!careers.isEmpty()) {
            sb.append("Послужний список (попередні місця роботи):\n");
            for (CareerRecord cr : careers) {
                sb.append("- ");
                if (cr.getPosition() != null) sb.append(cr.getPosition());
                if (cr.getOrganization() != null) sb.append(" @ ").append(cr.getOrganization());
                if (cr.getStartDate() != null) sb.append(" (").append(cr.getStartDate());
                if (cr.getEndDate() != null) sb.append(" – ").append(cr.getEndDate());
                else if (cr.getStartDate() != null) sb.append(" – по теперішній час");
                if (cr.getStartDate() != null) sb.append(")");
                if (cr.getNotes() != null && !cr.getNotes().isBlank())
                    sb.append(": ").append(trunc(cr.getNotes(), 200));
                sb.append("\n");
            }
        }

        // ── Дисципліни які викладає ──
        List<TeacherDiscipline> tds = safeTeacherDisciplines(t.getId());
        if (!tds.isEmpty()) {
            sb.append("Викладає дисципліни:\n");
            for (TeacherDiscipline td : tds) {
                if (td.getDiscipline() == null) continue;
                sb.append("- ");
                if (td.getDiscipline().getName() != null) sb.append(td.getDiscipline().getName());
                if (td.getDiscipline().getCode() != null) sb.append(" (").append(td.getDiscipline().getCode()).append(")");
                if (td.getDiscipline().getEducationalProgram() != null
                        && td.getDiscipline().getEducationalProgram().getName() != null) {
                    sb.append(" — ОПП: ").append(trunc(td.getDiscipline().getEducationalProgram().getName(), 120));
                }
                if (td.getAcademicYear() != null) sb.append(" [").append(td.getAcademicYear());
                if (td.getSemester() != null) sb.append(", сем. ").append(td.getSemester());
                if (td.getAcademicYear() != null) sb.append("]");
                sb.append("\n");
            }
        }

        // ── Бойовий досвід (УБД) ──
        if (t.isCombatVeteranStatus()) {
            sb.append("Учасник бойових дій (УБД): так");
            if (t.getCombatExperienceDates() != null && !t.getCombatExperienceDates().isBlank()) {
                sb.append(" — ").append(t.getCombatExperienceDates());
            }
            sb.append(" (досвід бойових дій, АТО, ООС, операції)\n");
        }

        // ── Контакти / наукометрія (коротко, без URL) ──
        if (t.getOrcidId() != null) sb.append("ORCID: ").append(t.getOrcidId()).append("\n");
        if (t.getScopusId() != null) sb.append("Scopus ID: ").append(t.getScopusId()).append("\n");

        // ── Мови ──
        List<LanguageSkill> langs = safeLangs(t.getId());
        if (!langs.isEmpty()) {
            sb.append("Мовні навички: ");
            for (int i = 0; i < langs.size(); i++) {
                LanguageSkill l = langs.get(i);
                if (i > 0) sb.append("; ");
                sb.append(nvl(l.getLanguage()));
                if (l.getLevel() != null) sb.append(" (").append(l.getLevel()).append(")");
                if (l.getSmrLevel() != null) sb.append(", СМР: ").append(l.getSmrLevel());
            }
            sb.append("\n");
        }

        // ── Досягнення п.38 ──
        List<Achievement> achs = safeAchievements(t.getId());
        if (!achs.isEmpty()) {
            sb.append("Досягнення п.38:\n");
            for (Achievement a : achs) {
                sb.append("- ");
                if (a.getAchievementType() != null) sb.append(a.getAchievementType().name()).append(": ");
                sb.append(nvl(a.getTitle()));
                if (a.getDateAchieved() != null) sb.append(" (").append(a.getDateAchieved()).append(")");
                sb.append("\n");
            }
        }

        // ── Публікації (топ 20, за роком спадання) ──
        List<Publication> pubs = safePublications(t.getId());
        if (!pubs.isEmpty()) {
            sb.append("Публікації:\n");
            pubs.stream()
                    .sorted((a, b) -> {
                        Integer ya = a.getYear(); Integer yb = b.getYear();
                        if (ya == null && yb == null) return 0;
                        if (ya == null) return 1;
                        if (yb == null) return -1;
                        return yb.compareTo(ya);
                    })
                    .limit(MAX_PUBS_PER_TEACHER)
                    .forEach(p -> {
                        sb.append("- ").append(trunc(p.getTitle(), 250));
                        if (p.getYear() != null) sb.append(" (").append(p.getYear()).append(")");
                        if (p.getArticleCategory() != null) sb.append(" [").append(p.getArticleCategory().name()).append("]");
                        if (p.getJournalName() != null) sb.append(" — ").append(trunc(p.getJournalName(), 150));
                        sb.append("\n");
                    });
        }

        // ── Підвищення кваліфікації (NATO, SANS, стажування, курси) ──
        List<QualificationImprovement> quals = safeQualifications(t.getId());
        if (!quals.isEmpty()) {
            sb.append("Підвищення кваліфікації:\n");
            quals.stream().limit(MAX_QUALS_PER_TEACHER).forEach(q -> {
                sb.append("- ").append(trunc(q.getTitle(), 250));
                if (q.getOrganization() != null) sb.append(" @ ").append(trunc(q.getOrganization(), 150));
                if (q.getStartDate() != null) sb.append(" (").append(q.getStartDate());
                if (q.getEndDate() != null) sb.append("–").append(q.getEndDate());
                if (q.getStartDate() != null) sb.append(")");
                if (q.getHours() != null) sb.append(", ").append(q.getHours()).append(" год.");
                if (q.getMilitaryCourseLevel() != null) sb.append(" [рівень ").append(q.getMilitaryCourseLevel()).append("]");
                sb.append("\n");
            });
        }

        // ── пп.6 Наукове керівництво (захищені здобувачі) ──
        List<ScientificSupervision> supervisions = safeSupervisions(t.getId());
        if (!supervisions.isEmpty()) {
            sb.append("Наукове керівництво (пп.6):\n");
            for (ScientificSupervision s : supervisions) {
                sb.append("- ");
                if (s.getStudentName() != null) sb.append(s.getStudentName());
                if (s.getDegreeType() != null) sb.append(" (").append(s.getDegreeType()).append(")");
                if (s.getTopic() != null) sb.append(", тема: ").append(trunc(s.getTopic(), 200));
                if (s.getDefenseDate() != null) sb.append(", захист ").append(s.getDefenseDate());
                if (s.getDiplomaNumber() != null) sb.append(", диплом ").append(s.getDiplomaNumber());
                sb.append("\n");
            }
        }

        // ── пп.7 Атестаційна діяльність (опонент / рецензент дисертації / голова / член постійної ради) ──
        List<AttestationActivity> attestations = safeAttestations(t.getId());
        if (!attestations.isEmpty()) {
            sb.append("Атестаційна діяльність (пп.7):\n");
            for (AttestationActivity a : attestations) {
                sb.append("- ");
                if (a.getRole() != null) sb.append(a.getRole()).append(": ");
                if (a.getStudentName() != null) sb.append(a.getStudentName());
                if (a.getCouncilName() != null) sb.append(" — рада: ").append(trunc(a.getCouncilName(), 150));
                if (a.getDefenseDate() != null) sb.append(" (захист: ").append(a.getDefenseDate()).append(")");
                appendDateRange(sb, a.getDateFrom(), a.getDateTo());
                sb.append("\n");
            }
        }

        // ── пп.8 Редакційно-видавнича діяльність ──
        List<EditorialActivity> editorials = safeEditorialActivities(t.getId());
        if (!editorials.isEmpty()) {
            sb.append("Редакційна діяльність (пп.8):\n");
            for (EditorialActivity ea : editorials) {
                sb.append("- ");
                if (ea.getRole() != null) sb.append(ea.getRole()).append(": ");
                if (ea.getJournalOrProjectName() != null) sb.append(trunc(ea.getJournalOrProjectName(), 200));
                appendDateRange(sb, ea.getDateFrom(), ea.getDateTo());
                if (ea.getDescription() != null) sb.append(" — ").append(trunc(ea.getDescription(), 150));
                sb.append("\n");
            }
        }

        // ── пп.9 Експертні ради (НАЗЯВО, акредитаційні, експертні) ──
        List<ExpertCouncil> councils = safeExpertCouncils(t.getId());
        if (!councils.isEmpty()) {
            sb.append("Експертні ради (пп.9):\n");
            for (ExpertCouncil ec : councils) {
                sb.append("- ");
                if (ec.getCouncilName() != null) sb.append(trunc(ec.getCouncilName(), 200));
                if (ec.getType() != null) sb.append(" [").append(ec.getType()).append("]");
                if (ec.getRole() != null) sb.append(", роль: ").append(ec.getRole());
                appendDateRange(sb, ec.getDateFrom(), ec.getDateTo());
                sb.append("\n");
            }
        }

        // ── пп.10 Міжнародні проєкти (Erasmus, Horizon, NATO programs) ──
        List<InternationalProject> intlProjects = safeInternationalProjects(t.getId());
        if (!intlProjects.isEmpty()) {
            sb.append("Міжнародні проєкти (пп.10):\n");
            for (InternationalProject ip : intlProjects) {
                sb.append("- ");
                if (ip.getProjectName() != null) sb.append(trunc(ip.getProjectName(), 200));
                if (ip.getProgram() != null) sb.append(" [").append(ip.getProgram()).append("]");
                if (ip.getRole() != null) sb.append(", роль: ").append(ip.getRole());
                appendDateRange(sb, ip.getDateFrom(), ip.getDateTo());
                if (ip.getDescription() != null) sb.append(" — ").append(trunc(ip.getDescription(), 150));
                sb.append("\n");
            }
        }

        // ── пп.11 Наукове консультування підприємств ──
        List<ScientificConsulting> consultings = safeScientificConsultings(t.getId());
        if (!consultings.isEmpty()) {
            sb.append("Наукове консультування (пп.11):\n");
            for (ScientificConsulting c : consultings) {
                sb.append("- ");
                if (c.getOrganizationName() != null) sb.append(trunc(c.getOrganizationName(), 200));
                if (c.getContractNumber() != null) sb.append(", договір ").append(c.getContractNumber());
                appendDateRange(sb, c.getDateFrom(), c.getDateTo());
                if (c.getYearsCount() != null) sb.append(", ").append(c.getYearsCount()).append(" р.");
                sb.append("\n");
            }
        }

        // ── пп.13 Викладання іноземною мовою ──
        List<ForeignLanguageTeaching> foreignTeachings = safeForeignLanguageTeachings(t.getId());
        if (!foreignTeachings.isEmpty()) {
            sb.append("Викладання іноземною мовою (пп.13):\n");
            for (ForeignLanguageTeaching ft : foreignTeachings) {
                sb.append("- ");
                if (ft.getDisciplineName() != null) sb.append(ft.getDisciplineName());
                if (ft.getLanguage() != null) sb.append(" — мовою: ").append(ft.getLanguage());
                if (ft.getHours() != null) sb.append(", ").append(ft.getHours()).append(" год.");
                if (ft.getAcademicYear() != null) sb.append(" [").append(ft.getAcademicYear()).append("]");
                sb.append("\n");
            }
        }

        // ── пп.14-15 Олімпіади / гуртки / конкурси ──
        List<OlympiadGuidance> olympiads = safeOlympiadGuidances(t.getId());
        if (!olympiads.isEmpty()) {
            sb.append("Олімпіади / гуртки / конкурси (пп.14-15):\n");
            for (OlympiadGuidance og : olympiads) {
                sb.append("- ");
                if (og.getActivityType() != null) sb.append(og.getActivityType()).append(": ");
                if (og.getOlympiadName() != null) sb.append(trunc(og.getOlympiadName(), 200));
                if (og.getStudentName() != null) sb.append(", здобувач: ").append(og.getStudentName());
                if (og.getResult() != null) sb.append(", результат: ").append(og.getResult());
                if (og.getYear() != null) sb.append(" (").append(og.getYear()).append(")");
                if (og.getCompetitionScope() != null) sb.append(" [").append(og.getCompetitionScope()).append("]");
                sb.append("\n");
            }
        }

        // ── пп.17-18 Військові місії (миротворчі ООН, навчання НАТО) ──
        List<MilitaryMission> missions = safeMilitaryMissions(t.getId());
        if (!missions.isEmpty()) {
            sb.append("Військові місії (пп.17-18):\n");
            for (MilitaryMission mm : missions) {
                sb.append("- ");
                if (mm.getMissionType() != null) sb.append(mm.getMissionType()).append(": ");
                if (mm.getMissionName() != null) sb.append(trunc(mm.getMissionName(), 200));
                if (mm.getCountry() != null) sb.append(", країна: ").append(mm.getCountry());
                appendDateRange(sb, mm.getDateFrom(), mm.getDateTo());
                sb.append("\n");
            }
        }

        // ── пп.19 Професійні асоціації (IEEE, ACM тощо) ──
        List<ProfessionalAssociation> associations = safeProfessionalAssociations(t.getId());
        if (!associations.isEmpty()) {
            sb.append("Професійні об'єднання (пп.19):\n");
            for (ProfessionalAssociation pa : associations) {
                sb.append("- ");
                if (pa.getOrganizationName() != null) sb.append(trunc(pa.getOrganizationName(), 200));
                if (pa.getRole() != null) sb.append(", роль: ").append(pa.getRole());
                appendDateRange(sb, pa.getDateFrom(), pa.getDateTo());
                if (pa.getCertificateNumber() != null) sb.append(" #").append(pa.getCertificateNumber());
                sb.append("\n");
            }
        }

        // ── пп.20 Практичний досвід за спеціальністю ──
        List<PracticalExperience> practicalExps = safePracticalExperiences(t.getId());
        if (!practicalExps.isEmpty()) {
            sb.append("Практичний досвід (пп.20):\n");
            for (PracticalExperience pe : practicalExps) {
                sb.append("- ");
                if (pe.getPosition() != null) sb.append(pe.getPosition());
                if (pe.getOrganizationName() != null) sb.append(" @ ").append(trunc(pe.getOrganizationName(), 200));
                if (pe.getSpecialtyName() != null) sb.append(", спеціальність: ").append(pe.getSpecialtyName());
                appendDateRange(sb, pe.getDateFrom(), pe.getDateTo());
                if (pe.getYearsCount() != null) sb.append(", ").append(pe.getYearsCount()).append(" р.");
                sb.append("\n");
            }
        }

        // Metadata — для майбутньої фільтрації пошуку
        java.util.Map<String, Object> meta = new java.util.HashMap<>();
        meta.put("teacherId", t.getId());
        meta.put("lastName", nvl(t.getLastName()));
        if (t.getDepartment() != null) {
            meta.put("departmentId", t.getDepartment().getId());
            if (t.getDepartment().getName() != null)
                meta.put("departmentName", t.getDepartment().getName());
        }

        return new Document(docIdFor(t.getId()), sb.toString(), meta);
    }

    private List<LanguageSkill> safeLangs(Long tid) {
        try { return languageSkillRepository.findByTeacherId(tid); }
        catch (Exception e) { return Collections.emptyList(); }
    }
    private List<Achievement> safeAchievements(Long tid) {
        try { return achievementService.findByTeacherId(tid); }
        catch (Exception e) { return Collections.emptyList(); }
    }
    private List<Publication> safePublications(Long tid) {
        try { return publicationService.findByTeacherId(tid); }
        catch (Exception e) { return Collections.emptyList(); }
    }
    private List<QualificationImprovement> safeQualifications(Long tid) {
        try { return qualificationService.findByTeacherId(tid); }
        catch (Exception e) { return Collections.emptyList(); }
    }
    private List<CareerRecord> safeCareerRecords(Long tid) {
        try { return careerRecordRepository.findByTeacherId(tid); }
        catch (Exception e) { return Collections.emptyList(); }
    }
    private List<Education> safeEducations(Long tid) {
        try { return educationRepository.findByTeacherIdOrderByGraduationYearDesc(tid); }
        catch (Exception e) { return Collections.emptyList(); }
    }
    private List<MilitaryEducation> safeMilitaryEducations(Long tid) {
        try { return militaryEducationRepository.findByTeacherIdOrderByGraduationYearDesc(tid); }
        catch (Exception e) { return Collections.emptyList(); }
    }
    private List<TeacherDiscipline> safeTeacherDisciplines(Long tid) {
        try { return teacherDisciplineRepository.findByTeacherId(tid); }
        catch (Exception e) { return Collections.emptyList(); }
    }
    private List<ScientificSupervision> safeSupervisions(Long tid) {
        try { return scientificSupervisionRepository.findByTeacherId(tid); }
        catch (Exception e) { return Collections.emptyList(); }
    }
    private List<AttestationActivity> safeAttestations(Long tid) {
        try { return attestationActivityRepository.findByTeacherId(tid); }
        catch (Exception e) { return Collections.emptyList(); }
    }
    private List<EditorialActivity> safeEditorialActivities(Long tid) {
        try { return editorialActivityRepository.findByTeacherId(tid); }
        catch (Exception e) { return Collections.emptyList(); }
    }
    private List<ExpertCouncil> safeExpertCouncils(Long tid) {
        try { return expertCouncilRepository.findByTeacherId(tid); }
        catch (Exception e) { return Collections.emptyList(); }
    }
    private List<InternationalProject> safeInternationalProjects(Long tid) {
        try { return internationalProjectRepository.findByTeacherId(tid); }
        catch (Exception e) { return Collections.emptyList(); }
    }
    private List<ScientificConsulting> safeScientificConsultings(Long tid) {
        try { return scientificConsultingRepository.findByTeacherId(tid); }
        catch (Exception e) { return Collections.emptyList(); }
    }
    private List<ForeignLanguageTeaching> safeForeignLanguageTeachings(Long tid) {
        try { return foreignLanguageTeachingRepository.findByTeacherId(tid); }
        catch (Exception e) { return Collections.emptyList(); }
    }
    private List<OlympiadGuidance> safeOlympiadGuidances(Long tid) {
        try { return olympiadGuidanceRepository.findByTeacherId(tid); }
        catch (Exception e) { return Collections.emptyList(); }
    }
    private List<MilitaryMission> safeMilitaryMissions(Long tid) {
        try { return militaryMissionRepository.findByTeacherId(tid); }
        catch (Exception e) { return Collections.emptyList(); }
    }
    private List<ProfessionalAssociation> safeProfessionalAssociations(Long tid) {
        try { return professionalAssociationRepository.findByTeacherId(tid); }
        catch (Exception e) { return Collections.emptyList(); }
    }
    private List<PracticalExperience> safePracticalExperiences(Long tid) {
        try { return practicalExperienceRepository.findByTeacherId(tid); }
        catch (Exception e) { return Collections.emptyList(); }
    }

    /** Утиліта: дописати " (dateFrom – dateTo)" якщо дати є. */
    private static void appendDateRange(StringBuilder sb, java.time.LocalDate from, java.time.LocalDate to) {
        if (from == null && to == null) return;
        sb.append(" (");
        if (from != null) sb.append(from);
        if (from != null && to != null) sb.append(" – ");
        if (to != null) sb.append(to);
        else if (from != null) sb.append(" – по т.ч.");
        sb.append(")");
    }

    private static String nvl(String s) { return s != null ? s : ""; }
    private static String trunc(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) + "…" : s;
    }
}
