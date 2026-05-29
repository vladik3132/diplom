package ua.edu.teacherlicence.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import ua.edu.teacherlicence.department.model.Department;
import ua.edu.teacherlicence.department.model.Faculty;
import ua.edu.teacherlicence.department.repository.DepartmentRepository;
import ua.edu.teacherlicence.department.repository.FacultyRepository;
import ua.edu.teacherlicence.opp.model.EducationalProgram;
import ua.edu.teacherlicence.opp.repository.EducationalProgramRepository;
import ua.edu.teacherlicence.user.model.Role;
import ua.edu.teacherlicence.user.model.User;
import ua.edu.teacherlicence.user.repository.UserRepository;

/**
 * Seeds structural data (faculties, departments, OPP, admin user) on ALL profiles.
 * Runs before DataSeeder (dev-only). Skips if data already exists.
 */
@Slf4j
@Component
@Profile("!schema-gen")
@Order(1)
@RequiredArgsConstructor
public class StructureSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final FacultyRepository facultyRepository;
    private final DepartmentRepository departmentRepository;
    private final EducationalProgramRepository educationalProgramRepository;

    @Override
    public void run(String... args) {
        if (facultyRepository.count() > 0) {
            log.info("Structure data already exists, skipping seeder");
            return;
        }

        log.info("Seeding structural data (faculties, departments, OPP, admin)...");

        // ===== FACULTIES =====
        Faculty fEKS = facultyRepository.save(Faculty.builder()
                .name("Факультет електронних комунікаційних систем").build());
        Faculty fIT = facultyRepository.save(Faculty.builder()
                .name("Факультет інформаційних технологій").build());
        Faculty fKB = facultyRepository.save(Faculty.builder()
                .name("Факультет кіберборотьби").build());
        Faculty fLid = facultyRepository.save(Faculty.builder()
                .name("Факультет лідерства").build());
        Faculty fZag = facultyRepository.save(Faculty.builder()
                .name("Загальноінститутські кафедри").build());

        // ===== DEPARTMENTS =====
        // Факультет електронних комунікаційних систем
        Department d11 = departmentRepository.save(Department.builder()
                .number("11").name("Кафедра комунікаційних систем та мереж").faculty(fEKS).build());
        Department d12 = departmentRepository.save(Department.builder()
                .number("12").name("Кафедра радіотехнологій").faculty(fEKS).build());
        Department d13 = departmentRepository.save(Department.builder()
                .number("13").name("Кафедра електроніки та схемотехніки").faculty(fEKS).build());

        // Факультет інформаційних технологій
        Department d21 = departmentRepository.save(Department.builder()
                .number("21").name("Кафедра інформаційних систем та технологій").faculty(fIT).build());
        Department d22 = departmentRepository.save(Department.builder()
                .number("22").name("Кафедра комп'ютерних наук та інтелектуальних технологій").faculty(fIT).build());
        Department d23 = departmentRepository.save(Department.builder()
                .number("23").name("Кафедра технічного забезпечення").faculty(fIT).build());

        // Факультет кіберборотьби
        Department d31 = departmentRepository.save(Department.builder()
                .number("31").name("Кафедра захисту інформації в інформаційно-комунікаційних системах").faculty(fKB).build());
        Department d32 = departmentRepository.save(Department.builder()
                .number("32").name("Кафедра спеціальних інформаційних систем та робототехнічних комплексів").faculty(fKB).build());
        Department d33 = departmentRepository.save(Department.builder()
                .number("33").name("Кафедра кібербезпеки").faculty(fKB).build());

        // Факультет лідерства
        Department d41 = departmentRepository.save(Department.builder()
                .number("41").name("Кафедра бойового застосування підрозділів зв'язку").faculty(fLid).build());
        departmentRepository.save(Department.builder()
                .number("42").name("Кафедра тактики та вогневої підготовки").faculty(fLid).build());
        departmentRepository.save(Department.builder()
                .number("43").name("Кафедра бойового забезпечення та повсякденної діяльності").faculty(fLid).build());

        // Загальноінститутські кафедри
        departmentRepository.save(Department.builder()
                .number("44").name("Кафедра соціально-гуманітарних дисциплін").faculty(fZag).build());
        departmentRepository.save(Department.builder()
                .number("45").name("Кафедра фізичного виховання, спеціальної фізичної підготовки і спорту").faculty(fZag).build());
        departmentRepository.save(Department.builder()
                .number("1").name("Кафедра фундаментальних дисциплін").faculty(fZag).build());
        departmentRepository.save(Department.builder()
                .number("2").name("Кафедра іноземних мов").faculty(fZag).build());
        departmentRepository.save(Department.builder()
                .number("3").name("Кафедра автомобільної техніки").faculty(fZag).build());
        departmentRepository.save(Department.builder()
                .number("4").name("Кафедра військової підготовки").faculty(fZag).build());

        // ===== EDUCATIONAL PROGRAMS (OPP) =====
        educationalProgramRepository.save(EducationalProgram.builder()
                .name("Комп'ютерні науки та технології")
                .shortCode("F3 КН")
                .educationLevel("перший (бакалаврський)")
                .educationForm("очна (денна)")
                .degree("бакалавр")
                .educationalQualification("бакалавр з комп'ютерних наук")
                .fieldOfKnowledge("F Інформаційні технології")
                .professionalQualification("офіцер тактичного рівня")
                .specialty("F3 Комп'ютерні науки")
                .credits(240)
                .specialization("математичне, інформаційне і програмне забезпечення військових інформаційних систем")
                .duration("4 роки на основі повної загальної середньої освіти")
                .enrollmentYear(2025)
                .department(d22)
                .build());

        educationalProgramRepository.save(EducationalProgram.builder()
                .name("Кібербезпека в інформаційно-телекомунікаційних системах")
                .shortCode("F5 КЗІ (121501)")
                .educationLevel("перший (бакалаврський)")
                .educationForm("очна (денна)")
                .degree("бакалавр")
                .educationalQualification("бакалавр з кібербезпеки та захисту інформації")
                .fieldOfKnowledge("F Інформаційні технології")
                .professionalQualification("офіцер тактичного рівня")
                .specialty("F5 Кібербезпека та захист інформації")
                .credits(240)
                .specialization("захист інформації та кібернетична безпека в інформаційно-телекомунікаційних системах")
                .duration("4 роки на основі повної загальної середньої освіти")
                .enrollmentYear(2025)
                .department(d33)
                .build());

        educationalProgramRepository.save(EducationalProgram.builder()
                .name("Кіберборотьба: ведення дій в кіберпосторі")
                .shortCode("F5 КЗІ (160200)")
                .educationLevel("перший (бакалаврський)")
                .educationForm("очна (денна)")
                .degree("бакалавр")
                .educationalQualification("бакалавр з кібербезпеки та захисту інформації")
                .fieldOfKnowledge("F Інформаційні технології")
                .professionalQualification("офіцер тактичного рівня")
                .specialty("F5 Кібербезпека та захист інформації")
                .credits(240)
                .specialization("кіберзахист та кібервплив, системи та комплекси ведення кіберборотьби")
                .duration("4 роки на основі повної загальної середньої освіти")
                .enrollmentYear(2025)
                .department(d33)
                .build());

        educationalProgramRepository.save(EducationalProgram.builder()
                .name("Інформаційні системи та технології")
                .shortCode("F6 ІСТ")
                .educationLevel("перший (бакалаврський)")
                .educationForm("очна (денна)")
                .degree("бакалавр")
                .educationalQualification("бакалавр з інформаційних систем та технологій")
                .fieldOfKnowledge("F Інформаційні технології")
                .professionalQualification("офіцер тактичного рівня")
                .specialty("F6 Інформаційні системи і технології")
                .credits(240)
                .specialization("автоматизовані системи управління військами та озброєнням")
                .duration("4 роки на основі повної загальної середньої освіти")
                .enrollmentYear(2025)
                .department(d21)
                .build());

        educationalProgramRepository.save(EducationalProgram.builder()
                .name("Телекомунікаційні системи та мережі")
                .shortCode("G5 ЕЕКПР")
                .educationLevel("перший (бакалаврський)")
                .educationForm("очна (денна)")
                .degree("бакалавр")
                .educationalQualification("бакалавр з електронних комунікацій та радіотехніки")
                .fieldOfKnowledge("G Інженерія, виробництво та будівництво")
                .professionalQualification("офіцер тактичного рівня")
                .specialty("G5 Електроніка, електронні комунікації, приладобудування та радіотехніка")
                .credits(240)
                .specialization("системи військового зв'язку")
                .duration("4 роки на основі повної загальної середньої освіти")
                .enrollmentYear(2025)
                .department(d12)
                .build());

        educationalProgramRepository.save(EducationalProgram.builder()
                .name("Управління діями підрозділів зв'язку")
                .shortCode("K5 ВУ")
                .educationLevel("перший (бакалаврський)")
                .educationForm("очна (денна)")
                .degree("бакалавр")
                .educationalQualification("бакалавр військового управління")
                .fieldOfKnowledge("K Безпека та оборона")
                .professionalQualification("офіцер тактичного рівня")
                .specialty("К5 Військове управління (за видами збройних сил)")
                .credits(240)
                .specialization("управління діями підрозділів зв'язку")
                .duration("4 роки на основі повної загальної середньої освіти")
                .enrollmentYear(2025)
                .department(d41)
                .build());

        educationalProgramRepository.save(EducationalProgram.builder()
                .name("Спецозброєння та робототехнічні військові комплекси")
                .shortCode("K7 ОВТ (160100)")
                .educationLevel("перший (бакалаврський)")
                .educationForm("очна (денна)")
                .degree("бакалавр")
                .educationalQualification("бакалавр озброєння та військової техніки")
                .fieldOfKnowledge("K Безпека та оборона")
                .professionalQualification("офіцер тактичного рівня")
                .specialty("K7 Озброєння та військова техніка")
                .credits(240)
                .specialization("радіоелектронні інформаційні системи, оперативна техніка та спецозброєння")
                .duration("4 роки на основі повної загальної середньої освіти")
                .enrollmentYear(2025)
                .department(d32)
                .build());

        educationalProgramRepository.save(EducationalProgram.builder()
                .name("Кіберрозвідка, системи та комплекси спеціального призначення")
                .shortCode("K7 ОВТ (093100)")
                .educationLevel("перший (бакалаврський)")
                .educationForm("очна (денна)")
                .degree("бакалавр")
                .educationalQualification("бакалавр озброєння та військової техніки")
                .fieldOfKnowledge("K Безпека та оборона")
                .professionalQualification("офіцер тактичного рівня")
                .specialty("K7 Озброєння та військова техніка")
                .credits(240)
                .specialization("кіберрозвідка, системи та комплекси спеціального призначення")
                .duration("4 роки на основі повної загальної середньої освіти")
                .enrollmentYear(2025)
                .department(d32)
                .build());

        // ===== ADMIN USER =====
        if (userRepository.count() == 0) {
            userRepository.save(User.builder()
                    .email("gradebook.superadmin@viti.edu.ua")
                    .passwordHash(passwordEncoder.encode("Fgjrfksgcbc450"))
                    .role(Role.ADMIN).isActive(true).build());
            log.info("Created admin user (gradebook.superadmin@viti.edu.ua)");
        }

        log.info("Structure seeding complete: {} faculties, {} departments, {} OPP",
                facultyRepository.count(), departmentRepository.count(),
                educationalProgramRepository.count());
    }
}
