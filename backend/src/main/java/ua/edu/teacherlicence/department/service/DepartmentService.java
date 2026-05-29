package ua.edu.teacherlicence.department.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ua.edu.teacherlicence.department.model.Department;
import ua.edu.teacherlicence.department.model.Faculty;
import ua.edu.teacherlicence.department.model.StaffPosition;
import ua.edu.teacherlicence.department.repository.DepartmentRepository;
import ua.edu.teacherlicence.department.repository.FacultyRepository;
import ua.edu.teacherlicence.department.repository.StaffPositionRepository;
import ua.edu.teacherlicence.teacher.model.Teacher;
import ua.edu.teacherlicence.teacher.repository.TeacherRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class DepartmentService {

    private final FacultyRepository facultyRepository;
    private final DepartmentRepository departmentRepository;
    private final StaffPositionRepository staffPositionRepository;
    private final TeacherRepository teacherRepository;

    // ── Faculty CRUD ──────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<Faculty> findAllFaculties() {
        return facultyRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Faculty findFacultyById(Long id) {
        return facultyRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Faculty not found with id: " + id));
    }

    public Faculty createFaculty(Faculty faculty) {
        return facultyRepository.save(faculty);
    }

    public Faculty updateFaculty(Long id, Faculty updated) {
        Faculty existing = findFacultyById(id);
        existing.setName(updated.getName());
        return facultyRepository.save(existing);
    }

    public void deleteFaculty(Long id) {
        if (!facultyRepository.existsById(id)) {
            throw new EntityNotFoundException("Faculty not found with id: " + id);
        }
        facultyRepository.deleteById(id);
    }

    // ── Department CRUD ───────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<Department> findAllDepartments() {
        return departmentRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<Department> findDepartmentsByFacultyId(Long facultyId) {
        return departmentRepository.findByFacultyId(facultyId);
    }

    @Transactional(readOnly = true)
    public Department findDepartmentById(Long id) {
        return departmentRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Department not found with id: " + id));
    }

    public Department createDepartment(Department department) {
        if (department.getFaculty() != null && department.getFaculty().getId() != null) {
            Faculty faculty = findFacultyById(department.getFaculty().getId());
            department.setFaculty(faculty);
        }
        return departmentRepository.save(department);
    }

    public Department updateDepartment(Long id, Department updated) {
        Department existing = findDepartmentById(id);
        existing.setName(updated.getName());
        if (updated.getFaculty() != null && updated.getFaculty().getId() != null) {
            Faculty faculty = findFacultyById(updated.getFaculty().getId());
            existing.setFaculty(faculty);
        }
        return departmentRepository.save(existing);
    }

    public void deleteDepartment(Long id) {
        if (!departmentRepository.existsById(id)) {
            throw new EntityNotFoundException("Department not found with id: " + id);
        }
        departmentRepository.deleteById(id);
    }

    // ── StaffPosition CRUD ─────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<StaffPosition> findStaffPositions(Long departmentId) {
        return staffPositionRepository.findByDepartmentIdOrderByOrderNumber(departmentId);
    }

    public StaffPosition createStaffPosition(Long departmentId, StaffPosition position) {
        Department department = findDepartmentById(departmentId);
        position.setDepartment(department);
        resolveTeacher(position);
        // Створення через UI — це підтвердження адміна, не bootstrap.
        position.setBootstrapped(false);
        return staffPositionRepository.save(position);
    }

    public StaffPosition updateStaffPosition(Long id, StaffPosition updated) {
        StaffPosition existing = staffPositionRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("StaffPosition not found with id: " + id));
        existing.setOrderNumber(updated.getOrderNumber());
        existing.setPositionTitle(updated.getPositionTitle());
        existing.setMilitaryRankCategory(updated.getMilitaryRankCategory());
        existing.setMilitarySpecialtyCode(updated.getMilitarySpecialtyCode());
        existing.setTariffGrade(updated.getTariffGrade());
        existing.setRate(updated.getRate() != null ? updated.getRate() : 1.0);
        existing.setImportedTeacherName(updated.getImportedTeacherName());
        // Teacher assignment
        if (updated.getTeacher() != null && updated.getTeacher().getId() != null) {
            Teacher teacher = teacherRepository.findById(updated.getTeacher().getId())
                    .orElseThrow(() -> new EntityNotFoundException("Teacher not found with id: " + updated.getTeacher().getId()));
            existing.setTeacher(teacher);
        } else {
            existing.setTeacher(null);
        }
        // Ручне редагування через UI знімає прапорець "автоматично створено" —
        // адмін підтвердив / уточнив дані.
        existing.setBootstrapped(false);
        return staffPositionRepository.save(existing);
    }

    public void deleteStaffPosition(Long id) {
        if (!staffPositionRepository.existsById(id)) {
            throw new EntityNotFoundException("StaffPosition not found with id: " + id);
        }
        staffPositionRepository.deleteById(id);
    }

    public List<StaffPosition> batchImportStaffPositions(Long departmentId, List<StaffPosition> positions) {
        Department department = findDepartmentById(departmentId);
        // Видалити старі
        List<StaffPosition> existing = staffPositionRepository.findByDepartmentIdOrderByOrderNumber(departmentId);
        staffPositionRepository.deleteAll(existing);
        // Зберегти нові — імпорт із документа штату вважається підтвердженим, тож bootstrapped=false
        for (StaffPosition pos : positions) {
            pos.setId(null);
            pos.setDepartment(department);
            if (pos.getRate() == null) pos.setRate(1.0);
            pos.setBootstrapped(false);
            resolveTeacher(pos);
        }
        return staffPositionRepository.saveAll(positions);
    }

    private void resolveTeacher(StaffPosition position) {
        if (position.getTeacher() != null && position.getTeacher().getId() != null) {
            // Лінкування по ID
            Teacher teacher = teacherRepository.findById(position.getTeacher().getId())
                    .orElseThrow(() -> new EntityNotFoundException("Teacher not found with id: " + position.getTeacher().getId()));
            position.setTeacher(teacher);
        } else if (position.getImportedTeacherName() != null
                && !position.getImportedTeacherName().isBlank()
                && !"ВАКАНТ".equalsIgnoreCase(position.getImportedTeacherName().trim())) {
            // Спроба авто-лінкування по ПІБ
            Teacher matched = findTeacherByFullName(position.getImportedTeacherName().trim());
            position.setTeacher(matched); // null якщо не знайдено
        } else {
            position.setTeacher(null);
        }
    }

    /**
     * Перелінкування всіх незалінкованих посад конкретної кафедри.
     * Повертає кількість успішно залінкованих.
     */
    public int relinkStaffPositions(Long departmentId) {
        List<StaffPosition> positions = staffPositionRepository.findByDepartmentIdOrderByOrderNumber(departmentId);
        int linked = 0;
        for (StaffPosition pos : positions) {
            if (pos.getTeacher() == null
                    && pos.getImportedTeacherName() != null
                    && !pos.getImportedTeacherName().isBlank()
                    && !"ВАКАНТ".equalsIgnoreCase(pos.getImportedTeacherName().trim())) {
                Teacher matched = findTeacherByFullName(pos.getImportedTeacherName().trim());
                if (matched != null) {
                    pos.setTeacher(matched);
                    staffPositionRepository.save(pos);
                    linked++;
                }
            }
        }
        return linked;
    }

    /**
     * Авто-лінкування штатних посад для щойно створеного/імпортованого викладача.
     * Шукає всі посади де teacher == null, але importedTeacherName задано,
     * і спробує зматчити по ПІБ.
     */
    public void autoLinkStaffPositionsForTeacher(Teacher teacher) {
        if (teacher == null || teacher.getLastName() == null) return;

        List<StaffPosition> unlinked = staffPositionRepository.findByTeacherIsNullAndImportedTeacherNameIsNotNull();
        for (StaffPosition pos : unlinked) {
            if (matchesTeacher(pos.getImportedTeacherName().trim(), teacher)) {
                pos.setTeacher(teacher);
                staffPositionRepository.save(pos);
            }
        }
    }

    private boolean matchesTeacher(String fullName, Teacher teacher) {
        String[] parts = fullName.trim().split("\\s+");
        if (parts.length < 1) return false;

        String lastName = parts[0];
        if (teacher.getLastName() == null || !teacher.getLastName().equalsIgnoreCase(lastName)) return false;

        if (parts.length > 1) {
            String firstName = parts[1];
            if (teacher.getFirstName() == null || !teacher.getFirstName().equalsIgnoreCase(firstName)) return false;
        }

        if (parts.length > 2) {
            String patronymic = parts[2];
            if (teacher.getPatronymic() != null && !teacher.getPatronymic().equalsIgnoreCase(patronymic)) return false;
        }

        return true;
    }

    /**
     * Пошук викладача по ПІБ (прізвище + ім'я + по-батькові).
     * Підтримує формати: "ШЕВЧЕНКО Олександр Іванович", "Шевченко Олександр Іванович"
     */
    private Teacher findTeacherByFullName(String fullName) {
        String[] parts = fullName.trim().split("\\s+");
        if (parts.length < 2) return null;

        String lastName = parts[0];
        String firstName = parts.length > 1 ? parts[1] : "";
        String patronymic = parts.length > 2 ? parts[2] : null;

        List<Teacher> all = teacherRepository.findAll();
        return all.stream()
                .filter(t -> t.getLastName() != null && t.getLastName().equalsIgnoreCase(lastName))
                .filter(t -> firstName.isEmpty() || (t.getFirstName() != null && t.getFirstName().equalsIgnoreCase(firstName)))
                .filter(t -> patronymic == null || (t.getPatronymic() != null && t.getPatronymic().equalsIgnoreCase(patronymic)))
                .findFirst()
                .orElse(null);
    }
}
