package ua.edu.teacherlicence.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import ua.edu.teacherlicence.achievement.model.Achievement;
import ua.edu.teacherlicence.achievement.model.AchievementType;
import ua.edu.teacherlicence.achievement.repository.AchievementRepository;
import ua.edu.teacherlicence.department.model.Department;
import ua.edu.teacherlicence.department.model.Faculty;
import ua.edu.teacherlicence.department.model.StaffPosition;
import ua.edu.teacherlicence.department.repository.DepartmentRepository;
import ua.edu.teacherlicence.department.repository.FacultyRepository;
import ua.edu.teacherlicence.department.repository.StaffPositionRepository;
import ua.edu.teacherlicence.fakhove.model.FakhovyiJournal;
import ua.edu.teacherlicence.fakhove.model.JournalCategory;
import ua.edu.teacherlicence.fakhove.model.ScopusJournal;
import ua.edu.teacherlicence.fakhove.repository.FakhovyiJournalRepository;
import ua.edu.teacherlicence.fakhove.repository.ScopusJournalRepository;
import ua.edu.teacherlicence.opp.model.EducationalProgram;
import ua.edu.teacherlicence.opp.repository.EducationalProgramRepository;
import ua.edu.teacherlicence.ppdata.model.*;
import ua.edu.teacherlicence.ppdata.repository.*;
import ua.edu.teacherlicence.publication.model.Publication;
import ua.edu.teacherlicence.publication.model.ArticleCategory;
import ua.edu.teacherlicence.publication.model.PublicationType;
import ua.edu.teacherlicence.publication.repository.PublicationRepository;
import ua.edu.teacherlicence.qualification.model.QualificationImprovement;
import ua.edu.teacherlicence.qualification.repository.QualificationImprovementRepository;
import ua.edu.teacherlicence.teacher.model.AcademicDegree;
import ua.edu.teacherlicence.teacher.model.AcademicTitle;
import ua.edu.teacherlicence.teacher.model.LanguageSkill;
import ua.edu.teacherlicence.teacher.model.Teacher;
import ua.edu.teacherlicence.teacher.repository.AcademicDegreeRepository;
import ua.edu.teacherlicence.teacher.repository.AcademicTitleRepository;
import ua.edu.teacherlicence.teacher.repository.LanguageSkillRepository;
import ua.edu.teacherlicence.teacher.repository.TeacherRepository;
import ua.edu.teacherlicence.user.model.Role;
import ua.edu.teacherlicence.user.model.User;
import ua.edu.teacherlicence.user.repository.UserRepository;

import java.time.LocalDate;
import java.util.List;

/**
 * Seed data for dev profile (VITI structure).
 */
@Slf4j
@Component
@Profile("dev")
@Order(2)
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final FacultyRepository facultyRepository;
    private final DepartmentRepository departmentRepository;
    private final TeacherRepository teacherRepository;
    private final AcademicDegreeRepository academicDegreeRepository;
    private final AcademicTitleRepository academicTitleRepository;
    private final AchievementRepository achievementRepository;
    private final PublicationRepository publicationRepository;
    private final QualificationImprovementRepository qualificationRepository;
    private final LanguageSkillRepository languageSkillRepository;
    private final StaffPositionRepository staffPositionRepository;
    private final FakhovyiJournalRepository fakhovyiJournalRepository;
    private final ScopusJournalRepository scopusJournalRepository;
    private final EducationalProgramRepository educationalProgramRepository;
    private final ScientificSupervisionRepository scientificSupervisionRepository;
    private final EditorialActivityRepository editorialActivityRepository;
    private final InternationalProjectRepository internationalProjectRepository;
    private final PracticalExperienceRepository practicalExperienceRepository;
    private final ForeignLanguageTeachingRepository foreignLanguageTeachingRepository;
    private final ProfessionalAssociationRepository professionalAssociationRepository;
    private final OlympiadGuidanceRepository olympiadGuidanceRepository;
    private final ua.edu.teacherlicence.teacher.repository.CareerRecordRepository careerRecordRepository;
    private final ua.edu.teacherlicence.publication.service.DstuCitationGenerator dstuGenerator;
    private final ua.edu.teacherlicence.achievement.service.AchievementComposer achievementComposer;
    private final ua.edu.teacherlicence.teacher.service.TeacherPositionService teacherPositionService;

    @Override
    public void run(String... args) {
        if (teacherRepository.count() > 0) {
            log.info("Dev seed data already exists, skipping");
            return;
        }

        log.info("Seeding dev test data (teachers, achievements, publications)...");

        // Reuse structure created by StructureSeeder — lookup departments by number
        java.util.Map<String, Department> deptMap = new java.util.HashMap<>();
        departmentRepository.findAll().forEach(d -> { if (d.getNumber() != null) deptMap.put(d.getNumber(), d); });
        Department d11 = deptMap.get("11");
        Department d21 = deptMap.get("21");
        Department d22 = deptMap.get("22");
        Department d33 = deptMap.get("33");
        Department d41 = deptMap.get("41");
        Department d42 = deptMap.get("42");
        Department d45 = deptMap.get("45");
        Department dMova = deptMap.get("2");
        if (d22 == null || d33 == null) {
            log.warn("Required departments not found, skipping dev seed");
            return;
        }

        // Departments and faculties created by StructureSeeder

        // OPP created by StructureSeeder

        // ===== TEACHERS =====
        Teacher t1 = teacherRepository.save(Teacher.builder()
                .lastName("Шевченко").firstName("Олександр").patronymic("Іванович")
                .dateOfBirth(LocalDate.of(1975, 3, 14)).position("Начальник кафедри — професор")
                .employmentType("MAIN").militaryRank("полковник")
                .university("Військовий інститут телекомунікацій та інформатизації")
                .universitySpeciality("Захист інформації в інформаційно-комунікаційних системах")
                .universityDiploma("ВС №012345").universityGraduationYear(1997)
                .universityDiplomaDate(LocalDate.of(1997, 6, 25))
                .experienceStartDate(LocalDate.of(2001, 9, 1)).email("shevchenko@viti.edu.ua")
                .orcidId("0000-0001-2345-6789").scopusId("57200001234")
                .combatVeteranStatus(true)
                .combatVeteranDoc("Посвідчення УБД №045678")
                .combatVeteranDocDate(LocalDate.of(2016, 5, 10))
                .combatVeteranDocIssuedBy("МО України")
                .combatExperienceDates("2014-2015 (АТО)")
                .department(d33).build());

        Teacher t2 = teacherRepository.save(Teacher.builder()
                .lastName("Коваленко").firstName("Марія").patronymic("Петрівна")
                .dateOfBirth(LocalDate.of(1982, 7, 22)).position("Доцент")
                .employmentType("MAIN")
                .university("Національний технічний університет України \"КПІ\"")
                .universitySpeciality("Комп'ютерні науки").universityDiploma("КВ №567890")
                .universityGraduationYear(2004).universityDiplomaDate(LocalDate.of(2004, 6, 20))
                .experienceStartDate(LocalDate.of(2011, 9, 1)).email("kovalenko@viti.edu.ua")
                .orcidId("0000-0002-3456-7890")
                .combatVeteranStatus(false).department(d33).build());

        Teacher t3 = teacherRepository.save(Teacher.builder()
                .lastName("Бондаренко").firstName("Андрій").patronymic("Сергійович")
                .dateOfBirth(LocalDate.of(1985, 11, 3)).position("Старший викладач")
                .employmentType("MAIN").militaryRank("підполковник")
                .university("ВІТІ").universitySpeciality("Інформаційні технології")
                .universityDiploma("ВС №234567").universityGraduationYear(2007)
                .universityDiplomaDate(LocalDate.of(2007, 6, 22))
                .experienceStartDate(LocalDate.of(2014, 9, 1)).email("bondarenko@viti.edu.ua")
                .combatVeteranStatus(true)
                .combatVeteranDoc("Посвідчення УБД №078912")
                .combatVeteranDocDate(LocalDate.of(2023, 8, 15))
                .combatVeteranDocIssuedBy("Полтавський РТЦК та СП")
                .combatExperienceDates("2022-2023 (ООС/ЗСУ)")
                .department(d22).build());

        Teacher t4 = teacherRepository.save(Teacher.builder()
                .lastName("Мельник").firstName("Ірина").patronymic("Олегівна")
                .dateOfBirth(LocalDate.of(1990, 5, 18)).position("Викладач")
                .employmentType("MAIN")
                .university("Київський національний університет ім. Тараса Шевченка")
                .universitySpeciality("Програмна інженерія")
                .universityDiploma("КВ №890123").universityGraduationYear(2012)
                .universityDiplomaDate(LocalDate.of(2012, 6, 28))
                .experienceStartDate(LocalDate.of(2018, 2, 1)).email("melnyk@viti.edu.ua")
                .combatVeteranStatus(false).department(d22).build());

        Teacher t5 = teacherRepository.save(Teacher.builder()
                .lastName("Ткаченко").firstName("Віктор").patronymic("Михайлович")
                .dateOfBirth(LocalDate.of(1970, 1, 27)).position("Професор")
                .employmentType("MAIN").militaryRank("полковник у відставці")
                .university("Національний університет оборони України")
                .universitySpeciality("Управління військами").universityDiploma("ВМ №098765")
                .universityGraduationYear(1992).universityDiplomaDate(LocalDate.of(1992, 6, 28))
                .experienceStartDate(LocalDate.of(1996, 9, 1)).email("tkachenko@viti.edu.ua")
                .orcidId("0000-0003-4567-8901").scopusId("57200005678")
                .combatVeteranStatus(true)
                .combatVeteranDoc("Посвідчення УБД №012345")
                .combatVeteranDocDate(LocalDate.of(2017, 2, 20))
                .combatVeteranDocIssuedBy("МО України")
                .combatExperienceDates("2014-2016 (АТО)")
                .department(d42).build());

        Teacher t6 = teacherRepository.save(Teacher.builder()
                .lastName("Литвиненко").firstName("Олексій").patronymic("Васильович")
                .dateOfBirth(LocalDate.of(1988, 9, 4)).position("Викладач")
                .employmentType("MAIN").militaryRank("майор")
                .university("ВІТІ").universitySpeciality("Телекомунікаційні системи та мережі")
                .universityDiploma("ВС №345678").universityGraduationYear(2010)
                .universityDiplomaDate(LocalDate.of(2010, 6, 24))
                .experienceStartDate(LocalDate.of(2021, 9, 1)).email("lytvynenko@viti.edu.ua")
                .combatVeteranStatus(true)
                .combatVeteranDoc("Посвідчення УБД №156789")
                .combatVeteranDocDate(LocalDate.of(2024, 3, 12))
                .combatVeteranDocIssuedBy("Київський РТЦК та СП")
                .combatExperienceDates("2022-2024 (ЗСУ)")
                .department(d11).build());

        Teacher t7 = teacherRepository.save(Teacher.builder()
                .lastName("Петренко").firstName("Дмитро").patronymic("Анатолійович")
                .dateOfBirth(LocalDate.of(1978, 4, 11)).position("Доцент")
                .employmentType("MAIN").militaryRank("підполковник")
                .university("Національна академія оборони України")
                .universitySpeciality("Управління діями підрозділів зв'язку")
                .universityDiploma("ВМ №456789").universityGraduationYear(2000)
                .universityDiplomaDate(LocalDate.of(2000, 6, 20))
                .experienceStartDate(LocalDate.of(2008, 9, 1)).email("petrenko@viti.edu.ua")
                .combatVeteranStatus(false).department(d41).build());

        Teacher t8 = teacherRepository.save(Teacher.builder()
                .lastName("Гончарук").firstName("Наталія").patronymic("Ігорівна")
                .dateOfBirth(LocalDate.of(1983, 12, 9)).position("Старший викладач")
                .employmentType("MAIN")
                .university("Київський національний лінгвістичний університет")
                .universitySpeciality("Англійська мова та література")
                .universityDiploma("КВ №678901").universityGraduationYear(2005)
                .universityDiplomaDate(LocalDate.of(2005, 6, 25))
                .experienceStartDate(LocalDate.of(2012, 9, 1)).email("honcharuk@viti.edu.ua")
                .combatVeteranStatus(false).department(dMova).build());

        Teacher t9 = teacherRepository.save(Teacher.builder()
                .lastName("Савченко").firstName("Тарас").patronymic("Олексійович")
                .dateOfBirth(LocalDate.of(1992, 8, 16)).position("Викладач")
                .employmentType("MAIN").militaryRank("капітан")
                .university("Національний університет фізичного виховання і спорту України")
                .universitySpeciality("Фізичне виховання")
                .universityDiploma("КВ №901234").universityGraduationYear(2014)
                .universityDiplomaDate(LocalDate.of(2014, 6, 30))
                .experienceStartDate(LocalDate.of(2023, 2, 1)).email("savchenko@viti.edu.ua")
                .combatVeteranStatus(true)
                .combatVeteranDoc("Посвідчення УБД №234567")
                .combatVeteranDocDate(LocalDate.of(2024, 7, 5))
                .combatVeteranDocIssuedBy("Харківський РТЦК та СП")
                .combatExperienceDates("2023-2024 (ЗСУ)")
                .department(d45).build());

        Teacher t10 = teacherRepository.save(Teacher.builder()
                .lastName("Кравченко").firstName("Юлія").patronymic("Романівна")
                .dateOfBirth(LocalDate.of(1980, 6, 21)).position("Професор")
                .employmentType("MAIN")
                .university("Харківський національний університет радіоелектроніки")
                .universitySpeciality("Інформаційні технології")
                .universityDiploma("ХН №123456").universityGraduationYear(2002)
                .universityDiplomaDate(LocalDate.of(2002, 6, 22))
                .experienceStartDate(LocalDate.of(2004, 9, 1)).email("kravchenko@viti.edu.ua")
                .orcidId("0000-0004-5678-9012")
                .combatVeteranStatus(false).department(d21).build());

        // ===== ACADEMIC DEGREES =====
        academicDegreeRepository.saveAll(List.of(
            AcademicDegree.builder().teacher(t1).degree("Доктор технічних наук")
                .dissertationTopic("Методи захисту інформації у військових телекомунікаційних системах")
                .diploma("ДД №001234").diplomaDate(LocalDate.of(2012, 3, 15)).build(),
            AcademicDegree.builder().teacher(t2).degree("Кандидат технічних наук")
                .diploma("ДК №005678").diplomaDate(LocalDate.of(2015, 11, 5)).build(),
            AcademicDegree.builder().teacher(t3).degree("Кандидат технічних наук")
                .diploma("ДК №009876").diplomaDate(LocalDate.of(2019, 5, 14)).build(),
            AcademicDegree.builder().teacher(t4).degree("Кандидат технічних наук")
                .diploma("ДК №012345").diplomaDate(LocalDate.of(2020, 12, 10)).build(),
            AcademicDegree.builder().teacher(t5).degree("Доктор військових наук")
                .dissertationTopic("Тактика застосування систем зв'язку в сучасних операціях")
                .diploma("ДД №000567").diplomaDate(LocalDate.of(2021, 6, 25)).build(),
            AcademicDegree.builder().teacher(t7).degree("Кандидат військових наук")
                .diploma("ДК №007654").diplomaDate(LocalDate.of(2014, 4, 18)).build(),
            AcademicDegree.builder().teacher(t8).degree("Кандидат філологічних наук")
                .diploma("ДК №011222").diplomaDate(LocalDate.of(2016, 9, 28)).build(),
            AcademicDegree.builder().teacher(t10).degree("Доктор технічних наук")
                .dissertationTopic("Інтелектуальні системи аналізу даних у військових інформаційних мережах")
                .diploma("ДД №002345").diplomaDate(LocalDate.of(2018, 2, 14)).build()
        ));

        // ===== ACADEMIC TITLES =====
        academicTitleRepository.saveAll(List.of(
            AcademicTitle.builder().teacher(t1).titleName("Професор")
                .attestat("12ДЦ №045678").attestatDate(LocalDate.of(2014, 10, 20)).build(),
            AcademicTitle.builder().teacher(t2).titleName("Доцент")
                .attestat("12ДЦ №067890").attestatDate(LocalDate.of(2018, 3, 12)).build(),
            AcademicTitle.builder().teacher(t5).titleName("Професор")
                .attestat("12ПР №012345").attestatDate(LocalDate.of(2022, 12, 1)).build(),
            AcademicTitle.builder().teacher(t7).titleName("Доцент")
                .attestat("12ДЦ №078901").attestatDate(LocalDate.of(2017, 6, 25)).build(),
            AcademicTitle.builder().teacher(t8).titleName("Доцент")
                .attestat("12ДЦ №089012").attestatDate(LocalDate.of(2019, 11, 15)).build(),
            AcademicTitle.builder().teacher(t10).titleName("Професор")
                .attestat("12ПР №023456").attestatDate(LocalDate.of(2020, 5, 18)).build()
        ));

        // ===== DEV USERS (admin already created by StructureSeeder) =====
        userRepository.save(User.builder()
                .email("head@university.edu.ua").passwordHash(passwordEncoder.encode("head"))
                .role(Role.HEAD_OF_DEPARTMENT).isActive(true).teacherId(t1.getId()).build());
        userRepository.save(User.builder()
                .email("teacher@university.edu.ua").passwordHash(passwordEncoder.encode("teacher"))
                .role(Role.TEACHER).isActive(true).teacherId(t2.getId()).build());

        // ===== ACHIEVEMENTS p.38 =====
        // Shevchenko — 5 types (COMPLIANT)
        achievementRepository.saveAll(List.of(
            Achievement.builder().teacher(t1).title("Публікації у Scopus: Cybersecurity Framework for Military IoT")
                .achievementType(AchievementType.PP_1_PUBLICATIONS).dateAchieved(LocalDate.of(2024, 3, 15))
                .verified(true).verifiedBy("admin").build(),
            Achievement.builder().teacher(t1).title("Підручник: Захист інформації у військових системах зв'язку")
                .achievementType(AchievementType.PP_3_TEXTBOOK).dateAchieved(LocalDate.of(2023, 9, 1))
                .verified(true).verifiedBy("admin").build(),
            Achievement.builder().teacher(t1).title("Наукове керівництво ад'юнктом Іваненко О.П.")
                .achievementType(AchievementType.PP_6_SUPERVISION).dateAchieved(LocalDate.of(2024, 6, 20))
                .verified(true).verifiedBy("admin").build(),
            Achievement.builder().teacher(t1).title("Участь у бойових діях (АТО 2014-2015)")
                .achievementType(AchievementType.PP_16_COMBAT_VETERAN).dateAchieved(LocalDate.of(2015, 1, 1))
                .verified(true).verifiedBy("admin").build(),
            Achievement.builder().teacher(t1).title("Апробаційні публікації")
                .achievementType(AchievementType.PP_12_APPROBATION).dateAchieved(LocalDate.of(2024, 1, 1))
                .verified(false).build(),
            Achievement.builder().teacher(t1).title("Олімпіади/гуртки")
                .achievementType(AchievementType.PP_14_STUDENT_OLYMPIAD).dateAchieved(LocalDate.of(2024, 1, 1))
                .verified(false).build(),
            Achievement.builder().teacher(t1).title("Стажування NATO CCDCOE (Таллінн), 240 годин")
                .achievementType(AchievementType.PP_10_INTERNATIONAL).dateAchieved(LocalDate.of(2023, 7, 15))
                .verified(true).verifiedBy("admin").build(),
            Achievement.builder().teacher(t1).title("Досвід практичної роботи за спеціальністю")
                .achievementType(AchievementType.PP_20_PRACTICAL_EXPERIENCE).dateAchieved(LocalDate.of(2024, 1, 1))
                .verified(false).build()
        ));

        // Kovalenko — 4 types (COMPLIANT)
        achievementRepository.saveAll(List.of(
            Achievement.builder().teacher(t2).title("5 публікацій у фахових виданнях категорії Б")
                .achievementType(AchievementType.PP_1_PUBLICATIONS).dateAchieved(LocalDate.of(2024, 5, 10))
                .verified(true).verifiedBy("admin").build(),
            Achievement.builder().teacher(t2).title("Навчально-методичний комплекс з криптографії")
                .achievementType(AchievementType.PP_4_METHODICAL).dateAchieved(LocalDate.of(2023, 12, 1))
                .verified(true).verifiedBy("admin").build(),
            Achievement.builder().teacher(t2).title("Сертифікат B2 з англійської мови (Cambridge)")
                .achievementType(AchievementType.PP_13_FOREIGN_LANGUAGE).dateAchieved(LocalDate.of(2024, 2, 28))
                .verified(true).verifiedBy("admin").build(),
            Achievement.builder().teacher(t2).title("Міжнародне стажування: University of Tallinn, 180 годин")
                .achievementType(AchievementType.PP_10_INTERNATIONAL).dateAchieved(LocalDate.of(2024, 4, 1))
                .verified(true).verifiedBy("admin").build()
        ));

        // Bondarenko — 3 types (WARNING)
        achievementRepository.saveAll(List.of(
            Achievement.builder().teacher(t3).title("Публікація у WoS: Software Security in Command Systems")
                .achievementType(AchievementType.PP_1_PUBLICATIONS).dateAchieved(LocalDate.of(2024, 8, 5))
                .verified(true).verifiedBy("admin").build(),
            Achievement.builder().teacher(t3).title("Участь у бойових діях (ООС/ЗСУ 2022-2023)")
                .achievementType(AchievementType.PP_16_COMBAT_VETERAN).dateAchieved(LocalDate.of(2022, 5, 1))
                .verified(true).verifiedBy("admin").build(),
            Achievement.builder().teacher(t3).title("Патент: Система виявлення кіберзагроз у військових мережах")
                .achievementType(AchievementType.PP_2_PATENTS).dateAchieved(LocalDate.of(2024, 11, 20))
                .verified(true).verifiedBy("admin").build()
        ));

        // Melnyk — 2 types (NON_COMPLIANT)
        achievementRepository.saveAll(List.of(
            Achievement.builder().teacher(t4).title("3 публікації у фахових виданнях")
                .achievementType(AchievementType.PP_1_PUBLICATIONS).dateAchieved(LocalDate.of(2024, 6, 15))
                .verified(true).verifiedBy("admin").build(),
            Achievement.builder().teacher(t4).title("Методичні рекомендації з розробки мобільних додатків C4ISR")
                .achievementType(AchievementType.PP_4_METHODICAL).dateAchieved(LocalDate.of(2024, 9, 1))
                .verified(false).build()
        ));

        // Tkachenko — 6 types (COMPLIANT)
        achievementRepository.saveAll(List.of(
            Achievement.builder().teacher(t5).title("10 публікацій Scopus/WoS за 2022-2024")
                .achievementType(AchievementType.PP_1_PUBLICATIONS).dateAchieved(LocalDate.of(2024, 12, 1))
                .verified(true).verifiedBy("admin").build(),
            Achievement.builder().teacher(t5).title("Підручник: Тактика застосування військ зв'язку")
                .achievementType(AchievementType.PP_3_TEXTBOOK).dateAchieved(LocalDate.of(2023, 2, 15))
                .verified(true).verifiedBy("admin").build(),
            Achievement.builder().teacher(t5).title("Захист докторської дисертації з військових наук")
                .achievementType(AchievementType.PP_5_DISSERTATION).dateAchieved(LocalDate.of(2021, 6, 25))
                .verified(true).verifiedBy("admin").build(),
            Achievement.builder().teacher(t5).title("Членство у редколегії \"Збірник наукових праць ВІТІ\"")
                .achievementType(AchievementType.PP_8_EDITORIAL).dateAchieved(LocalDate.of(2023, 1, 1))
                .verified(true).verifiedBy("admin").build(),
            Achievement.builder().teacher(t5).title("Участь у бойових діях (АТО 2014-2016)")
                .achievementType(AchievementType.PP_16_COMBAT_VETERAN).dateAchieved(LocalDate.of(2014, 8, 1))
                .verified(true).verifiedBy("admin").build(),
            Achievement.builder().teacher(t5).title("Керівництво ад'юнктом Сидоренко В.В.")
                .achievementType(AchievementType.PP_6_SUPERVISION).dateAchieved(LocalDate.of(2024, 9, 15))
                .verified(true).verifiedBy("admin").build()
        ));

        // Lytvynenko — 2 types (NON_COMPLIANT, but UBD)
        achievementRepository.saveAll(List.of(
            Achievement.builder().teacher(t6).title("2 публікації у фахових виданнях")
                .achievementType(AchievementType.PP_1_PUBLICATIONS).dateAchieved(LocalDate.of(2024, 10, 1))
                .verified(false).build(),
            Achievement.builder().teacher(t6).title("Участь у бойових діях (ЗСУ 2022-2024)")
                .achievementType(AchievementType.PP_16_COMBAT_VETERAN).dateAchieved(LocalDate.of(2022, 3, 1))
                .verified(true).verifiedBy("admin").build()
        ));

        // Kravchenko — 5 types (COMPLIANT)
        achievementRepository.saveAll(List.of(
            Achievement.builder().teacher(t10).title("8 публікацій у фахових та Scopus виданнях")
                .achievementType(AchievementType.PP_1_PUBLICATIONS).dateAchieved(LocalDate.of(2024, 11, 1))
                .verified(true).verifiedBy("admin").build(),
            Achievement.builder().teacher(t10).title("Монографія: ІТ-інфраструктура систем зв'язку ЗСУ")
                .achievementType(AchievementType.PP_3_TEXTBOOK).dateAchieved(LocalDate.of(2024, 4, 20))
                .verified(true).verifiedBy("admin").build(),
            Achievement.builder().teacher(t10).title("Сертифікат CEH (Certified Ethical Hacker)")
                .achievementType(AchievementType.PP_15_SCHOOL_OLYMPIAD).dateAchieved(LocalDate.of(2023, 10, 5))
                .verified(true).verifiedBy("admin").build(),
            Achievement.builder().teacher(t10).title("Практичний досвід у галузі ІТ 15+ років")
                .achievementType(AchievementType.PP_20_PRACTICAL_EXPERIENCE).dateAchieved(LocalDate.of(2024, 1, 1))
                .verified(true).verifiedBy("admin").build(),
            Achievement.builder().teacher(t10).title("Стажування Bundeswehr University Munich, 180 годин")
                .achievementType(AchievementType.PP_10_INTERNATIONAL).dateAchieved(LocalDate.of(2024, 8, 30))
                .verified(true).verifiedBy("admin").build()
        ));

        // ===== PUBLICATIONS =====
        publicationRepository.saveAll(List.of(
            Publication.builder().teacher(t1).title("Cybersecurity Framework for Military IoT Networks")
                .journalName("IEEE Military Communications Conference").type(PublicationType.ARTICLE).articleCategory(ArticleCategory.SCOPUS)
                .year(2024).volume("42").pages("104-118").doi("10.1109/MILCOM.2024.001")
                .ppType(AchievementType.PP_1_PUBLICATIONS).sourceSection("pp.1").build(),
            Publication.builder().teacher(t1).title("Захист критичної інфраструктури від кібератак")
                .journalName("Збірник наукових праць ВІТІ").type(PublicationType.ARTICLE).articleCategory(ArticleCategory.CATEGORY_B)
                .year(2024).volume("3").pages("12-25")
                .ppType(AchievementType.PP_1_PUBLICATIONS).sourceSection("pp.1").build(),
            Publication.builder().teacher(t2).title("Криптографічні протоколи для захищеного військового зв'язку")
                .journalName("Захист інформації").type(PublicationType.ARTICLE).articleCategory(ArticleCategory.CATEGORY_B)
                .year(2024).volume("7").pages("23-31")
                .ppType(AchievementType.PP_1_PUBLICATIONS).sourceSection("pp.1").build(),
            Publication.builder().teacher(t3).title("Software Security in Military Command and Control Systems")
                .journalName("Journal of Systems and Software").type(PublicationType.ARTICLE).articleCategory(ArticleCategory.WOS)
                .year(2024).volume("208").pages("111-125").doi("10.1016/j.jss.2024.002")
                .ppType(AchievementType.PP_1_PUBLICATIONS).sourceSection("pp.1").build(),
            Publication.builder().teacher(t5).title("Tactical Communications in Modern Warfare: Lessons Learned")
                .journalName("Military Operations Research").type(PublicationType.ARTICLE).articleCategory(ArticleCategory.SCOPUS)
                .year(2024).volume("29").pages("45-62").doi("10.5711/MOR.2024.001")
                .ppType(AchievementType.PP_1_PUBLICATIONS).sourceSection("pp.1").build(),
            Publication.builder().teacher(t5).title("Автоматизація управління військами в умовах РЕБ")
                .journalName("Наука і оборона").type(PublicationType.ARTICLE).articleCategory(ArticleCategory.CATEGORY_B)
                .year(2023).volume("2").pages("45-56")
                .ppType(AchievementType.PP_1_PUBLICATIONS).sourceSection("pp.1").build(),
            Publication.builder().teacher(t7).title("Оптимізація каналів зв'язку в умовах бойових дій")
                .journalName("Зв'язок").type(PublicationType.ARTICLE).articleCategory(ArticleCategory.CATEGORY_B)
                .year(2024).volume("3").pages("78-85")
                .ppType(AchievementType.PP_1_PUBLICATIONS).sourceSection("pp.1").build(),
            Publication.builder().teacher(t10).title("AI-based Intrusion Detection for Military Networks")
                .journalName("Computers & Security").type(PublicationType.ARTICLE).articleCategory(ArticleCategory.SCOPUS)
                .year(2024).volume("138").pages("201-218").doi("10.1016/j.cose.2024.001")
                .ppType(AchievementType.PP_1_PUBLICATIONS).sourceSection("pp.1").build(),
            Publication.builder().teacher(t8).title("Military English Terminology Teaching Methodology")
                .journalName("Proceedings of NATO Language Conference").type(PublicationType.APPROBATION)
                .year(2024).pages("67-73")
                .ppType(AchievementType.PP_12_APPROBATION).sourceSection("pp.12").build(),
            Publication.builder().teacher(t2).title("Post-quantum cryptography for secure military communication")
                .journalName("IEEE MILCOM 2024").type(PublicationType.APPROBATION)
                .year(2024).pages("156-161")
                .ppType(AchievementType.PP_12_APPROBATION).sourceSection("pp.12").build(),
            // CEUR Workshop Proceedings — indexed in Scopus (ISSN 1613-0073)
            // journalName = conference name, publisher = CEUR Workshop Proceedings
            Publication.builder().teacher(t1).title("Prediction of data transmission reliability in military IoT networks")
                .journalName("ITTAP 2023: Information Technology and Implementation")
                .publisher("CEUR Workshop Proceedings")
                .type(PublicationType.ARTICLE).articleCategory(ArticleCategory.SCOPUS)
                .year(2023).volume("3624").pages("112-119").issn("1613-0073")
                .ppType(AchievementType.PP_1_PUBLICATIONS).sourceSection("pp.1").build(),
            Publication.builder().teacher(t1).title("Structuring Management Tasks of Military Communication Systems")
                .journalName("ICSIT 2024: International Conference on Security and Information Technologies")
                .publisher("CEUR Workshop Proceedings")
                .type(PublicationType.ARTICLE).articleCategory(ArticleCategory.SCOPUS)
                .year(2024).volume("3801").pages("45-53").issn("1613-0073")
                .ppType(AchievementType.PP_1_PUBLICATIONS).sourceSection("pp.1").build(),
            Publication.builder().teacher(t1).title("Graph-based approach to the cybersecurity monitoring of military networks")
                .journalName("COLINS 2025: Computational Linguistics and Intelligent Systems")
                .publisher("CEUR Workshop Proceedings")
                .type(PublicationType.ARTICLE).articleCategory(ArticleCategory.SCOPUS)
                .year(2025).volume("3912").pages("78-86").issn("1613-0073")
                .ppType(AchievementType.PP_1_PUBLICATIONS).sourceSection("pp.1").build(),
            // Approbation publications for teacher 1 (should be pp.12, not pp.1)
            Publication.builder().teacher(t1).title("Сучасні підходи в побудові систем кіберзахисту")
                .conferenceInfo("I Міжнародна науково-технічна конференція \"Системи і технології зв'язку, інформатизації та кібербезпеки\", 25 — 26 листопада 2021 року, Київ, Україна")
                .city("Київ").type(PublicationType.APPROBATION).year(2021).pages("45-47")
                .ppType(AchievementType.PP_12_APPROBATION).sourceSection("pp.12").build(),
            Publication.builder().teacher(t1).title("Прогнозування стану електромагнітної обстановки")
                .conferenceInfo("III Міжнародна науково-технічна конференція \"Системи і технології зв'язку, інформатизації та кібербезпеки: актуальні питання і тенденції розвитку\", 30 листопада 2023 року, Київ, Україна")
                .city("Київ").type(PublicationType.APPROBATION).year(2023).pages("112-115")
                .ppType(AchievementType.PP_12_APPROBATION).sourceSection("pp.12").build(),
            Publication.builder().teacher(t1).title("Оцінка знань у закладах вищої освіти")
                .conferenceInfo("XXIV Міжнародна науково-практична конференція \"Інформаційні технології та безпека\" (ITS-2024), 19 жовтня 2024 року, Київ, Україна")
                .city("Київ").publisher("Інжиніринг")
                .type(PublicationType.APPROBATION).year(2024).pages("67-70")
                .ppType(AchievementType.PP_12_APPROBATION).sourceSection("pp.12").build(),
            Publication.builder().teacher(t1).title("Управління балансуванням навантаження в мережах зв'язку")
                .journalName("Системи і технології зв'язку").type(PublicationType.ARTICLE)
                .articleCategory(ArticleCategory.CATEGORY_B).year(2025).volume("1").pages("33-41")
                .ppType(AchievementType.PP_1_PUBLICATIONS).sourceSection("pp.1").build()
        ));

        // Дисципліни та призначення створюються через імпорт Excel НПБ

        // ===== QUALIFICATIONS =====
        qualificationRepository.saveAll(List.of(
            QualificationImprovement.builder().teacher(t1)
                .title("NATO Cyber Defence (CCDCOE Tallinn)")
                .organization("NATO Cooperative Cyber Defence Centre of Excellence")
                .startDate(LocalDate.of(2024, 2, 1)).endDate(LocalDate.of(2024, 4, 30))
                .hours(240).credits(8.0).certificateNumber("CCDCOE-2024-UA-056")
                .certificateDate(LocalDate.of(2024, 5, 10)).build(),
            QualificationImprovement.builder().teacher(t2)
                .title("Хмарні технології в освіті та обороні")
                .organization("НДІПО")
                .startDate(LocalDate.of(2024, 5, 15)).endDate(LocalDate.of(2024, 6, 15))
                .hours(30).credits(1.0).certificateNumber("NDIPO-2024-1234")
                .certificateDate(LocalDate.of(2024, 6, 20)).build(),
            QualificationImprovement.builder().teacher(t3)
                .title("SANS SEC504: Hacker Tools and Incident Handling")
                .organization("SANS Institute")
                .startDate(LocalDate.of(2023, 9, 1)).endDate(LocalDate.of(2023, 12, 20))
                .hours(180).credits(6.0).certificateNumber("SANS-2023-789")
                .certificateDate(LocalDate.of(2024, 1, 8)).build(),
            QualificationImprovement.builder().teacher(t5)
                .title("Сучасні засоби управління військами")
                .organization("Національний університет оборони України")
                .startDate(LocalDate.of(2024, 1, 10)).endDate(LocalDate.of(2024, 3, 10))
                .hours(60).credits(2.0).certificateNumber("NUOU-2024-321")
                .certificateDate(LocalDate.of(2024, 3, 15)).build(),
            QualificationImprovement.builder().teacher(t5)
                .title("NATO Military Decision Making Process (MDMP)")
                .organization("NATO School Oberammergau")
                .startDate(LocalDate.of(2023, 5, 15)).endDate(LocalDate.of(2023, 6, 10))
                .hours(120).credits(4.0).certificateNumber("NSO-2023-UA-089")
                .certificateDate(LocalDate.of(2023, 6, 15)).build(),
            QualificationImprovement.builder().teacher(t7)
                .title("Автоматизовані системи управління зв'язком")
                .organization("ВІКНУ ім. Т. Шевченка")
                .startDate(LocalDate.of(2024, 3, 1)).endDate(LocalDate.of(2024, 5, 31))
                .hours(90).credits(3.0).certificateNumber("VIKNU-2024-567")
                .certificateDate(LocalDate.of(2024, 6, 5)).build(),
            QualificationImprovement.builder().teacher(t10)
                .title("Artificial Intelligence in Military Applications")
                .organization("Bundeswehr University Munich")
                .startDate(LocalDate.of(2024, 7, 1)).endDate(LocalDate.of(2024, 8, 30))
                .hours(180).credits(6.0).certificateNumber("BUMunich-2024-012")
                .certificateDate(LocalDate.of(2024, 9, 5)).build()
        ));

        // ===== LANGUAGE SKILLS =====
        languageSkillRepository.saveAll(List.of(
            LanguageSkill.builder().teacher(t1)
                .language("Англійська").level("2222")
                .certificateDetails("СМР STANAG 6001")
                .certificateNumber("MIL-ENG-2024-001")
                .certificateDate(LocalDate.of(2024, 3, 15))
                .certificateOrganization("Національний університет оборони України").build(),
            LanguageSkill.builder().teacher(t2)
                .language("Англійська").level("2221")
                .certificateDetails("СМР STANAG 6001")
                .certificateNumber("MIL-ENG-2024-002")
                .certificateDate(LocalDate.of(2024, 5, 20))
                .certificateOrganization("Національний університет оборони України").build(),
            LanguageSkill.builder().teacher(t2)
                .language("Німецька").level("1111")
                .certificateDetails("Goethe-Zertifikat B1")
                .certificateNumber("GZ-B1-2023-456")
                .certificateDate(LocalDate.of(2023, 11, 10))
                .certificateOrganization("Goethe-Institut Kyiv").build(),
            LanguageSkill.builder().teacher(t3)
                .language("Англійська").level("1+1+11")
                .certificateDetails("СМР STANAG 6001")
                .certificateNumber("MIL-ENG-2023-015")
                .certificateDate(LocalDate.of(2023, 6, 28))
                .certificateOrganization("Національний університет оборони України").build(),
            LanguageSkill.builder().teacher(t5)
                .language("Англійська").level("2222")
                .certificateDetails("СМР STANAG 6001")
                .certificateNumber("MIL-ENG-2023-003")
                .certificateDate(LocalDate.of(2023, 4, 12))
                .certificateOrganization("Національний університет оборони України").build(),
            LanguageSkill.builder().teacher(t5)
                .language("Французька").level("1111")
                .certificateDetails("DELF B1")
                .certificateNumber("DELF-B1-2022-789")
                .certificateDate(LocalDate.of(2022, 9, 5))
                .certificateOrganization("Alliance Française de Kiev").build(),
            LanguageSkill.builder().teacher(t6)
                .language("Англійська").level("1111")
                .certificateDetails("СМР STANAG 6001")
                .certificateNumber("MIL-ENG-2024-010")
                .certificateDate(LocalDate.of(2024, 2, 18))
                .certificateOrganization("Національний університет оборони України").build(),
            LanguageSkill.builder().teacher(t7)
                .language("Англійська").level("1+1+1+1+")
                .certificateDetails("СМР STANAG 6001")
                .certificateNumber("MIL-ENG-2024-007")
                .certificateDate(LocalDate.of(2024, 1, 22))
                .certificateOrganization("Національний університет оборони України").build(),
            LanguageSkill.builder().teacher(t8)
                .language("Англійська").level("3333")
                .certificateDetails("IELTS 8.0, Cambridge CPE")
                .certificateNumber("CPE-2023-UK-1234")
                .certificateDate(LocalDate.of(2023, 7, 15))
                .certificateOrganization("Cambridge Assessment English").build(),
            LanguageSkill.builder().teacher(t8)
                .language("Німецька").level("2222")
                .certificateDetails("Goethe-Zertifikat C1")
                .certificateNumber("GZ-C1-2022-321")
                .certificateDate(LocalDate.of(2022, 12, 8))
                .certificateOrganization("Goethe-Institut Kyiv").build(),
            LanguageSkill.builder().teacher(t10)
                .language("Англійська").level("2211")
                .certificateDetails("СМР STANAG 6001")
                .certificateNumber("MIL-ENG-2024-011")
                .certificateDate(LocalDate.of(2024, 4, 30))
                .certificateOrganization("Національний університет оборони України").build()
        ));

        // ===== STAFF POSITIONS =====
        // Кафедра кібербезпеки (d33) — t1 Шевченко, t2 Коваленко
        staffPositionRepository.saveAll(List.of(
            StaffPosition.builder().department(d33).orderNumber(1)
                .positionTitle("Начальник кафедри").militaryRankCategory("Полковник")
                .militarySpecialtyCode("5302002").tariffGrade(49).teacher(t1).build(),
            StaffPosition.builder().department(d33).orderNumber(2)
                .positionTitle("Заступник начальника кафедри").militaryRankCategory("Підполковник")
                .militarySpecialtyCode("5302003").tariffGrade(48).teacher(null).build(),
            StaffPosition.builder().department(d33).orderNumber(3)
                .positionTitle("Професор").militaryRankCategory("Підполковник")
                .militarySpecialtyCode("5302003").tariffGrade(47).teacher(null).build(),
            StaffPosition.builder().department(d33).orderNumber(4)
                .positionTitle("Доцент").militaryRankCategory("Підполковник")
                .militarySpecialtyCode("5302003").tariffGrade(44).teacher(t2).build(),
            StaffPosition.builder().department(d33).orderNumber(5)
                .positionTitle("Доцент").militaryRankCategory("Підполковник")
                .militarySpecialtyCode("5302003").tariffGrade(44).teacher(null).build(),
            StaffPosition.builder().department(d33).orderNumber(6)
                .positionTitle("Старший викладач").militaryRankCategory("Підполковник")
                .militarySpecialtyCode("5302003").tariffGrade(43).teacher(null).build()
        ));

        // Кафедра комп'ютерних наук (d22) — t3 Бондаренко, t4 Мельник
        staffPositionRepository.saveAll(List.of(
            StaffPosition.builder().department(d22).orderNumber(1)
                .positionTitle("Начальник кафедри").militaryRankCategory("Полковник")
                .militarySpecialtyCode("5302002").tariffGrade(49).teacher(null).build(),
            StaffPosition.builder().department(d22).orderNumber(2)
                .positionTitle("Професор").militaryRankCategory("Підполковник")
                .militarySpecialtyCode("5302003").tariffGrade(47).teacher(null).build(),
            StaffPosition.builder().department(d22).orderNumber(3)
                .positionTitle("Доцент").militaryRankCategory("Підполковник")
                .militarySpecialtyCode("5302003").tariffGrade(44).teacher(null).build(),
            StaffPosition.builder().department(d22).orderNumber(4)
                .positionTitle("Старший викладач").militaryRankCategory("Підполковник")
                .militarySpecialtyCode("5302003").tariffGrade(43).teacher(t3).build(),
            StaffPosition.builder().department(d22).orderNumber(5)
                .positionTitle("Викладач").militaryRankCategory("Майор")
                .militarySpecialtyCode("5302003").tariffGrade(42).teacher(t4).build()
        ));

        // Кафедра тактики та вогневої підготовки (d42) — t5 Ткаченко
        staffPositionRepository.saveAll(List.of(
            StaffPosition.builder().department(d42).orderNumber(1)
                .positionTitle("Начальник кафедри").militaryRankCategory("Полковник")
                .militarySpecialtyCode("5302002").tariffGrade(49).teacher(null).build(),
            StaffPosition.builder().department(d42).orderNumber(2)
                .positionTitle("Професор").militaryRankCategory("Полковник")
                .militarySpecialtyCode("5302003").tariffGrade(47).teacher(t5).build(),
            StaffPosition.builder().department(d42).orderNumber(3)
                .positionTitle("Доцент").militaryRankCategory("Підполковник")
                .militarySpecialtyCode("5302003").tariffGrade(44).teacher(null).build(),
            StaffPosition.builder().department(d42).orderNumber(4)
                .positionTitle("Старший викладач").militaryRankCategory("Майор")
                .militarySpecialtyCode("5302003").tariffGrade(43).teacher(null).build()
        ));

        // ===== FAKHOVI JOURNALS (довідник фахових видань) =====
        fakhovyiJournalRepository.saveAll(List.of(
            FakhovyiJournal.builder().name("Збірник наукових праць ВІТІ")
                .founders("Військовий інститут телекомунікацій та інформатизації")
                .specialtyCodes("122, 125, 126, 172, 255")
                .inclusionDate(LocalDate.of(2018, 5, 10))
                .category(JournalCategory.CATEGORY_B)
                .nameNormalized("збірник наукових праць віті").build(),
            FakhovyiJournal.builder().name("Захист інформації")
                .founders("НАУ, ДССЗЗІ України")
                .specialtyCodes("125, 126, 172")
                .inclusionDate(LocalDate.of(2015, 3, 20))
                .category(JournalCategory.CATEGORY_B)
                .nameNormalized("захист інформації").build(),
            FakhovyiJournal.builder().name("Наука і оборона")
                .founders("Міністерство оборони України")
                .specialtyCodes("255, 256")
                .inclusionDate(LocalDate.of(2010, 1, 15))
                .category(JournalCategory.CATEGORY_B)
                .nameNormalized("наука і оборона").build(),
            FakhovyiJournal.builder().name("Зв'язок")
                .founders("Державний університет телекомунікацій")
                .specialtyCodes("172, 125")
                .inclusionDate(LocalDate.of(2016, 7, 1))
                .category(JournalCategory.CATEGORY_B)
                .nameNormalized("зв'язок").build(),
            FakhovyiJournal.builder().name("Кібербезпека: освіта, наука, техніка")
                .founders("Національний авіаційний університет")
                .specialtyCodes("125, 126")
                .inclusionDate(LocalDate.of(2019, 11, 5))
                .category(JournalCategory.CATEGORY_A)
                .nameNormalized("кібербезпека: освіта, наука, техніка").build(),
            FakhovyiJournal.builder().name("Сучасний захист інформації")
                .founders("ДССЗЗІ України, ДУІКТ")
                .specialtyCodes("125, 126, 172")
                .inclusionDate(LocalDate.of(2017, 9, 12))
                .category(JournalCategory.CATEGORY_B)
                .nameNormalized("сучасний захист інформації").build()
        ));

        // ===== SCOPUS JOURNALS =====
        scopusJournalRepository.saveAll(List.of(
            ScopusJournal.builder().name("IEEE Military Communications Conference")
                .issn("2155-7578").nameNormalized("ieee military communications conference").build(),
            ScopusJournal.builder().name("Computers & Security")
                .issn("0167-4048").nameNormalized("computers & security").build(),
            ScopusJournal.builder().name("Journal of Systems and Software")
                .issn("0164-1212").nameNormalized("journal of systems and software").build(),
            ScopusJournal.builder().name("Military Operations Research")
                .issn("1082-5983").nameNormalized("military operations research").build(),
            ScopusJournal.builder().name("Eastern-European Journal of Enterprise Technologies")
                .issn("1729-3774").nameNormalized("eastern-european journal of enterprise technologies").build(),
            ScopusJournal.builder().name("XI International Scientific Conference \"Information Technology and Implementation\"")
                .issn(null).nameNormalized("xi international scientific conference \"information technology and implementation\"").build(),
            ScopusJournal.builder().name("CEUR Workshop Proceedings")
                .issn("1613-0073").nameNormalized("ceur workshop proceedings").build()
        ));

        // ===== PP DATA (структуровані дані п.38) =====

        // пп.6 Наукове керівництво — Шевченко, Ткаченко
        scientificSupervisionRepository.saveAll(List.of(
            ScientificSupervision.builder().teacher(t1)
                .studentName("Іваненко Олег Петрович")
                .topic("Методи виявлення кібератак у мережах Інтернету речей військового призначення")
                .defenseDate(LocalDate.of(2024, 6, 20))
                .degreeType(DegreeType.PHD).diplomaNumber("ДК №015678").build(),
            ScientificSupervision.builder().teacher(t5)
                .studentName("Сидоренко Василь Васильович")
                .topic("Удосконалення тактики зв'язку в умовах сучасних операцій")
                .degreeType(DegreeType.PHD).build()
        ));

        // пп.8 Редакційно-видавнича діяльність — Ткаченко
        editorialActivityRepository.saveAll(List.of(
            EditorialActivity.builder().teacher(t5)
                .role(EditorialRole.BOARD_MEMBER)
                .journalOrProjectName("Збірник наукових праць ВІТІ")
                .dateFrom(LocalDate.of(2023, 1, 1)).build()
        ));

        // пп.10 Міжнародні проєкти — Шевченко, Коваленко, Кравченко
        internationalProjectRepository.saveAll(List.of(
            InternationalProject.builder().teacher(t1)
                .projectName("NATO CCDCOE Cyber Defence Training")
                .program(InternationalProgram.NATO)
                .role("Учасник стажування")
                .dateFrom(LocalDate.of(2023, 7, 1)).dateTo(LocalDate.of(2023, 7, 30)).build(),
            InternationalProject.builder().teacher(t2)
                .projectName("University of Tallinn Academic Exchange")
                .program(InternationalProgram.ERASMUS)
                .role("Стажист")
                .dateFrom(LocalDate.of(2024, 3, 1)).dateTo(LocalDate.of(2024, 4, 30)).build(),
            InternationalProject.builder().teacher(t10)
                .projectName("Bundeswehr University Munich AI Program")
                .program(InternationalProgram.GRANT)
                .role("Дослідник")
                .dateFrom(LocalDate.of(2024, 7, 1)).dateTo(LocalDate.of(2024, 8, 30)).build()
        ));

        // пп.13 Іноземна мова у навчанні — Гончарук
        foreignLanguageTeachingRepository.saveAll(List.of(
            ForeignLanguageTeaching.builder().teacher(t8)
                .disciplineName("Військова англійська мова")
                .language("Англійська")
                .hours(120)
                .academicYear("2024-2025").semester(1).build(),
            ForeignLanguageTeaching.builder().teacher(t8)
                .disciplineName("Військова англійська мова")
                .language("Англійська")
                .hours(120)
                .academicYear("2024-2025").semester(2).build()
        ));

        // пп.14 Олімпіади/конкурси/гуртки — Шевченко, Козак
        olympiadGuidanceRepository.saveAll(List.of(
            // Науковий гурток — Шевченко
            OlympiadGuidance.builder().teacher(t1)
                .activityType(Pp14ActivityType.SCIENTIFIC_GROUP)
                .role(OlympiadRole.GROUP_LEADER)
                .olympiadName("Науковий гурток «Кіберзахист»")
                .departmentName("Кафедра комп'ютерних інформаційних технологій")
                .participantCount(14)
                .academicYear("2023-2024")
                .orderNumber("133")
                .orderDate(LocalDate.of(2023, 8, 20))
                .build(),
            // Олімпіада — Шевченко
            OlympiadGuidance.builder().teacher(t1)
                .activityType(Pp14ActivityType.OLYMPIAD)
                .level(OlympiadLevel.STUDENT)
                .role(OlympiadRole.SUPERVISOR)
                .olympiadName("II етап Всеукраїнської олімпіади з інформаційної безпеки")
                .studentName("Петренко Андрій Олегович")
                .result("III місце")
                .year(2024)
                .build(),
            // Конкурс наукових робіт — Козак
            OlympiadGuidance.builder().teacher(t4)
                .activityType(Pp14ActivityType.SCIENTIFIC_COMPETITION)
                .level(OlympiadLevel.STUDENT)
                .role(OlympiadRole.SUPERVISOR)
                .olympiadName("Всеукраїнський конкурс наукових робіт зі спеціальності «Кібербезпека»")
                .studentName("Мельник Ірина Сергіївна")
                .result("Диплом I ступеня")
                .year(2024)
                .build()
        ));

        // пп.19 Професійні об'єднання — Кравченко
        professionalAssociationRepository.saveAll(List.of(
            ProfessionalAssociation.builder().teacher(t10)
                .organizationName("IEEE Computer Society")
                .role("Member")
                .dateFrom(LocalDate.of(2020, 1, 1))
                .certificateNumber("IEEE-2020-UA-567").build()
        ));

        // пп.20 Практичний досвід — Кравченко
        practicalExperienceRepository.saveAll(List.of(
            PracticalExperience.builder().teacher(t10)
                .organizationName("НДІ Інформаційних технологій МО України")
                .position("Провідний інженер")
                .dateFrom(LocalDate.of(2004, 9, 1)).dateTo(LocalDate.of(2018, 8, 31))
                .yearsCount(14).build()
        ));

        // Послужний список (CareerRecord) — для пп.20 аналізу
        careerRecordRepository.saveAll(List.of(
            // Шевченко (t1) — 7 років практичного досвіду + педагогічний
            ua.edu.teacherlicence.teacher.model.CareerRecord.builder().teacher(t1)
                .position("Інженер-програміст").organization("НДІ Кібернетики МО України")
                .startDate(LocalDate.of(2000, 9, 1)).endDate(LocalDate.of(2005, 8, 31))
                .notes("Розробка ПЗ для систем зв'язку").build(),
            ua.edu.teacherlicence.teacher.model.CareerRecord.builder().teacher(t1)
                .position("Провідний інженер з кібербезпеки").organization("ДССЗЗІ України")
                .startDate(LocalDate.of(2005, 9, 1)).endDate(LocalDate.of(2008, 8, 31))
                .notes("Технічний захист інформації").build(),
            ua.edu.teacherlicence.teacher.model.CareerRecord.builder().teacher(t1)
                .position("Старший викладач кафедри КІТ").organization("ВІТІ")
                .startDate(LocalDate.of(2008, 9, 1)).endDate(LocalDate.of(2015, 8, 31))
                .notes("Педагогічна діяльність").build(),
            ua.edu.teacherlicence.teacher.model.CareerRecord.builder().teacher(t1)
                .position("Доцент кафедри КІТ").organization("ВІТІ")
                .startDate(LocalDate.of(2015, 9, 1)).endDate(null)
                .notes("Науково-педагогічна діяльність").build(),
            // Кравченко (t10) — 14 років практичного досвіду
            ua.edu.teacherlicence.teacher.model.CareerRecord.builder().teacher(t10)
                .position("Провідний інженер").organization("НДІ Інформаційних технологій МО України")
                .startDate(LocalDate.of(2004, 9, 1)).endDate(LocalDate.of(2018, 8, 31))
                .notes("Розробка та впровадження ІТ-систем").build(),
            ua.edu.teacherlicence.teacher.model.CareerRecord.builder().teacher(t10)
                .position("Викладач").organization("ВІТІ")
                .startDate(LocalDate.of(2018, 9, 1)).endDate(null)
                .notes("Педагогічна діяльність").build()
        ));

        // Автогенерація ДСТУ 8302:2015 для всіх публікацій
        publicationRepository.findAll().forEach(pub -> {
            if (pub.getDstuCitation() == null || pub.getDstuCitation().isBlank()) {
                String dstu = dstuGenerator.generate(pub);
                if (dstu != null) {
                    pub.setDstuCitation(dstu);
                    publicationRepository.save(pub);
                }
            }
        });

        // Перегенеруємо описи досягнень з структурованих ентіті +
        // створюємо bootstrap-StaffPosition для викладачів (бо Liquibase міграція
        // 014 виконалась перед DataSeeder, тож тестові викладачі залишилися без штату).
        List<ua.edu.teacherlicence.teacher.model.Teacher> allTeachers = teacherRepository.findAll();
        for (ua.edu.teacherlicence.teacher.model.Teacher t : allTeachers) {
            try {
                achievementComposer.recomposeForTeacher(t);
            } catch (Exception e) {
                log.warn("Failed to recompose achievements for {}: {}", t.getLastName(), e.getMessage());
            }
            try {
                teacherPositionService.ensureStaffPosition(t);
            } catch (Exception e) {
                log.warn("Failed to ensure staff position for {}: {}", t.getLastName(), e.getMessage());
            }
        }

        log.info("Seed data created: 5 faculties, 18 departments, 10 teachers, 28 achievements, " +
                "10 publications, 12 disciplines, 20 teacher-disciplines, 7 qualifications, " +
                "11 language skills, 15 staff positions, 6 fakhovi journals, 6 scopus journals, " +
                "ppData records, 3 users");
    }
}
