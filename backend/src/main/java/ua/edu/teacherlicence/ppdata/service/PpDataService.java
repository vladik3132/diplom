package ua.edu.teacherlicence.ppdata.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ua.edu.teacherlicence.achievement.service.AchievementComposer;
import ua.edu.teacherlicence.ppdata.model.*;
import ua.edu.teacherlicence.ppdata.repository.*;
import ua.edu.teacherlicence.teacher.model.Teacher;

import java.util.List;
import java.util.Optional;

/**
 * Єдиний сервіс для всіх 11 сутностей структурованих даних пп.5-20.
 * Забезпечує CRUD-операції для кожного типу даних.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PpDataService {

    private final ScientificSupervisionRepository scientificSupervisionRepo;
    private final AttestationActivityRepository attestationActivityRepo;
    private final EditorialActivityRepository editorialActivityRepo;
    private final ExpertCouncilRepository expertCouncilRepo;
    private final InternationalProjectRepository internationalProjectRepo;
    private final ScientificConsultingRepository scientificConsultingRepo;
    private final ForeignLanguageTeachingRepository foreignLanguageTeachingRepo;
    private final OlympiadGuidanceRepository olympiadGuidanceRepo;
    private final MilitaryMissionRepository militaryMissionRepo;
    private final ProfessionalAssociationRepository professionalAssociationRepo;
    private final PracticalExperienceRepository practicalExperienceRepo;
    private final AchievementComposer achievementComposer;

    /**
     * Перегенерація досягнень після зміни даних п.38.
     */
    private void recomposeAchievements(Teacher teacher) {
        if (teacher == null) return;
        try {
            achievementComposer.recomposeForTeacher(teacher);
            log.info("Recomposed achievements for {} after ppData change", teacher.getLastName());
        } catch (Exception e) {
            log.warn("Failed to recompose achievements for {}: {}", teacher.getLastName(), e.getMessage());
        }
    }

    // =====================================================================
    // пп.6 — ScientificSupervision (Наукове керівництво)
    // =====================================================================

    public List<ScientificSupervision> findScientificSupervisionByTeacherId(Long teacherId) {
        return scientificSupervisionRepo.findByTeacherId(teacherId);
    }

    public Optional<ScientificSupervision> findScientificSupervisionById(Long id) {
        return scientificSupervisionRepo.findById(id);
    }

    @Transactional
    public ScientificSupervision saveScientificSupervision(ScientificSupervision entity) {
        ScientificSupervision saved = scientificSupervisionRepo.save(entity);
        recomposeAchievements(saved.getTeacher());
        return saved;
    }

    @Transactional
    public void deleteScientificSupervisionById(Long id) {
        scientificSupervisionRepo.findById(id).ifPresent(e -> {
            Teacher teacher = e.getTeacher();
            scientificSupervisionRepo.deleteById(id);
            recomposeAchievements(teacher);
        });
    }

    // =====================================================================
    // пп.7 — AttestationActivity (Участь в атестації)
    // =====================================================================

    public List<AttestationActivity> findAttestationActivityByTeacherId(Long teacherId) {
        return attestationActivityRepo.findByTeacherId(teacherId);
    }

    public Optional<AttestationActivity> findAttestationActivityById(Long id) {
        return attestationActivityRepo.findById(id);
    }

    @Transactional
    public AttestationActivity saveAttestationActivity(AttestationActivity entity) {
        AttestationActivity saved = attestationActivityRepo.save(entity);
        recomposeAchievements(saved.getTeacher());
        return saved;
    }

    @Transactional
    public void deleteAttestationActivityById(Long id) {
        attestationActivityRepo.findById(id).ifPresent(e -> {
            Teacher teacher = e.getTeacher();
            attestationActivityRepo.deleteById(id);
            recomposeAchievements(teacher);
        });
    }

    // =====================================================================
    // пп.8 — EditorialActivity (Редакційна діяльність)
    // =====================================================================

    public List<EditorialActivity> findEditorialActivityByTeacherId(Long teacherId) {
        return editorialActivityRepo.findByTeacherId(teacherId);
    }

    public Optional<EditorialActivity> findEditorialActivityById(Long id) {
        return editorialActivityRepo.findById(id);
    }

    @Transactional
    public EditorialActivity saveEditorialActivity(EditorialActivity entity) {
        EditorialActivity saved = editorialActivityRepo.save(entity);
        recomposeAchievements(saved.getTeacher());
        return saved;
    }

    @Transactional
    public void deleteEditorialActivityById(Long id) {
        editorialActivityRepo.findById(id).ifPresent(e -> {
            Teacher teacher = e.getTeacher();
            editorialActivityRepo.deleteById(id);
            recomposeAchievements(teacher);
        });
    }

    // =====================================================================
    // пп.9 — ExpertCouncil (Експертна рада)
    // =====================================================================

    public List<ExpertCouncil> findExpertCouncilByTeacherId(Long teacherId) {
        return expertCouncilRepo.findByTeacherId(teacherId);
    }

    public Optional<ExpertCouncil> findExpertCouncilById(Long id) {
        return expertCouncilRepo.findById(id);
    }

    @Transactional
    public ExpertCouncil saveExpertCouncil(ExpertCouncil entity) {
        ExpertCouncil saved = expertCouncilRepo.save(entity);
        recomposeAchievements(saved.getTeacher());
        return saved;
    }

    @Transactional
    public void deleteExpertCouncilById(Long id) {
        expertCouncilRepo.findById(id).ifPresent(e -> {
            Teacher teacher = e.getTeacher();
            expertCouncilRepo.deleteById(id);
            recomposeAchievements(teacher);
        });
    }

    // =====================================================================
    // пп.10 — InternationalProject (Міжнародні проекти)
    // =====================================================================

    public List<InternationalProject> findInternationalProjectByTeacherId(Long teacherId) {
        return internationalProjectRepo.findByTeacherId(teacherId);
    }

    public Optional<InternationalProject> findInternationalProjectById(Long id) {
        return internationalProjectRepo.findById(id);
    }

    @Transactional
    public InternationalProject saveInternationalProject(InternationalProject entity) {
        InternationalProject saved = internationalProjectRepo.save(entity);
        recomposeAchievements(saved.getTeacher());
        return saved;
    }

    @Transactional
    public void deleteInternationalProjectById(Long id) {
        internationalProjectRepo.findById(id).ifPresent(e -> {
            Teacher teacher = e.getTeacher();
            internationalProjectRepo.deleteById(id);
            recomposeAchievements(teacher);
        });
    }

    // =====================================================================
    // пп.11 — ScientificConsulting (Наукове консультування)
    // =====================================================================

    public List<ScientificConsulting> findScientificConsultingByTeacherId(Long teacherId) {
        return scientificConsultingRepo.findByTeacherId(teacherId);
    }

    public Optional<ScientificConsulting> findScientificConsultingById(Long id) {
        return scientificConsultingRepo.findById(id);
    }

    @Transactional
    public ScientificConsulting saveScientificConsulting(ScientificConsulting entity) {
        ScientificConsulting saved = scientificConsultingRepo.save(entity);
        recomposeAchievements(saved.getTeacher());
        return saved;
    }

    @Transactional
    public void deleteScientificConsultingById(Long id) {
        scientificConsultingRepo.findById(id).ifPresent(e -> {
            Teacher teacher = e.getTeacher();
            scientificConsultingRepo.deleteById(id);
            recomposeAchievements(teacher);
        });
    }

    // =====================================================================
    // пп.13 — ForeignLanguageTeaching (Викладання іноземною мовою)
    // =====================================================================

    public List<ForeignLanguageTeaching> findForeignLanguageTeachingByTeacherId(Long teacherId) {
        return foreignLanguageTeachingRepo.findByTeacherId(teacherId);
    }

    public Optional<ForeignLanguageTeaching> findForeignLanguageTeachingById(Long id) {
        return foreignLanguageTeachingRepo.findById(id);
    }

    @Transactional
    public ForeignLanguageTeaching saveForeignLanguageTeaching(ForeignLanguageTeaching entity) {
        ForeignLanguageTeaching saved = foreignLanguageTeachingRepo.save(entity);
        recomposeAchievements(saved.getTeacher());
        return saved;
    }

    @Transactional
    public void deleteForeignLanguageTeachingById(Long id) {
        foreignLanguageTeachingRepo.findById(id).ifPresent(e -> {
            Teacher teacher = e.getTeacher();
            foreignLanguageTeachingRepo.deleteById(id);
            recomposeAchievements(teacher);
        });
    }

    // =====================================================================
    // пп.14+15 — OlympiadGuidance (Олімпіади та конкурси)
    // =====================================================================

    public List<OlympiadGuidance> findOlympiadGuidanceByTeacherId(Long teacherId) {
        return olympiadGuidanceRepo.findByTeacherId(teacherId);
    }

    public Optional<OlympiadGuidance> findOlympiadGuidanceById(Long id) {
        return olympiadGuidanceRepo.findById(id);
    }

    @Transactional
    public OlympiadGuidance saveOlympiadGuidance(OlympiadGuidance entity) {
        OlympiadGuidance saved = olympiadGuidanceRepo.save(entity);
        recomposeAchievements(saved.getTeacher());
        return saved;
    }

    @Transactional
    public void deleteOlympiadGuidanceById(Long id) {
        olympiadGuidanceRepo.findById(id).ifPresent(e -> {
            Teacher teacher = e.getTeacher();
            olympiadGuidanceRepo.deleteById(id);
            recomposeAchievements(teacher);
        });
    }

    // =====================================================================
    // пп.17+18 — MilitaryMission (Міжнародні військові місії)
    // =====================================================================

    public List<MilitaryMission> findMilitaryMissionByTeacherId(Long teacherId) {
        return militaryMissionRepo.findByTeacherId(teacherId);
    }

    public Optional<MilitaryMission> findMilitaryMissionById(Long id) {
        return militaryMissionRepo.findById(id);
    }

    @Transactional
    public MilitaryMission saveMilitaryMission(MilitaryMission entity) {
        MilitaryMission saved = militaryMissionRepo.save(entity);
        recomposeAchievements(saved.getTeacher());
        return saved;
    }

    @Transactional
    public void deleteMilitaryMissionById(Long id) {
        militaryMissionRepo.findById(id).ifPresent(e -> {
            Teacher teacher = e.getTeacher();
            militaryMissionRepo.deleteById(id);
            recomposeAchievements(teacher);
        });
    }

    // =====================================================================
    // пп.19 — ProfessionalAssociation (Професійні об'єднання)
    // =====================================================================

    public List<ProfessionalAssociation> findProfessionalAssociationByTeacherId(Long teacherId) {
        return professionalAssociationRepo.findByTeacherId(teacherId);
    }

    public Optional<ProfessionalAssociation> findProfessionalAssociationById(Long id) {
        return professionalAssociationRepo.findById(id);
    }

    @Transactional
    public ProfessionalAssociation saveProfessionalAssociation(ProfessionalAssociation entity) {
        ProfessionalAssociation saved = professionalAssociationRepo.save(entity);
        recomposeAchievements(saved.getTeacher());
        return saved;
    }

    @Transactional
    public void deleteProfessionalAssociationById(Long id) {
        professionalAssociationRepo.findById(id).ifPresent(e -> {
            Teacher teacher = e.getTeacher();
            professionalAssociationRepo.deleteById(id);
            recomposeAchievements(teacher);
        });
    }

    // =====================================================================
    // пп.20 — PracticalExperience (Досвід практичної роботи)
    // =====================================================================

    public List<PracticalExperience> findPracticalExperienceByTeacherId(Long teacherId) {
        return practicalExperienceRepo.findByTeacherId(teacherId);
    }

    public Optional<PracticalExperience> findPracticalExperienceById(Long id) {
        return practicalExperienceRepo.findById(id);
    }

    @Transactional
    public PracticalExperience savePracticalExperience(PracticalExperience entity) {
        PracticalExperience saved = practicalExperienceRepo.save(entity);
        recomposeAchievements(saved.getTeacher());
        return saved;
    }

    @Transactional
    public void deletePracticalExperienceById(Long id) {
        practicalExperienceRepo.findById(id).ifPresent(e -> {
            Teacher teacher = e.getTeacher();
            practicalExperienceRepo.deleteById(id);
            recomposeAchievements(teacher);
        });
    }
}
