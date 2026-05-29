package ua.edu.teacherlicence.rating.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import ua.edu.teacherlicence.file.model.EntityTypeConstants;
import ua.edu.teacherlicence.file.service.FileAttachmentService;
import ua.edu.teacherlicence.rating.dto.AcademicMobilityDto;
import ua.edu.teacherlicence.rating.dto.ForeignInternshipDto;
import ua.edu.teacherlicence.rating.dto.MethodologicalExperimentDto;
import ua.edu.teacherlicence.rating.dto.OpenLessonDto;
import ua.edu.teacherlicence.rating.model.AcademicMobility;
import ua.edu.teacherlicence.rating.model.ForeignInternship;
import ua.edu.teacherlicence.rating.model.MethodologicalExperiment;
import ua.edu.teacherlicence.rating.model.OpenLesson;
import ua.edu.teacherlicence.rating.repository.AcademicMobilityRepository;
import ua.edu.teacherlicence.rating.repository.ForeignInternshipRepository;
import ua.edu.teacherlicence.rating.repository.MethodologicalExperimentRepository;
import ua.edu.teacherlicence.rating.repository.OpenLessonRepository;
import ua.edu.teacherlicence.teacher.model.Teacher;
import ua.edu.teacherlicence.teacher.repository.TeacherRepository;

import java.util.List;

/**
 * CRUD для вкладки "Інша діяльність" на профілі викладача.
 * Сутності: OpenLesson, MethodologicalExperiment, AcademicMobility.
 */
@RestController
@RequestMapping("/api/teachers/{teacherId}")
@RequiredArgsConstructor
public class OtherActivityController {

    private final OpenLessonRepository openLessonRepository;
    private final MethodologicalExperimentRepository experimentRepository;
    private final AcademicMobilityRepository mobilityRepository;
    private final ForeignInternshipRepository foreignInternshipRepository;
    private final TeacherRepository teacherRepository;
    private final FileAttachmentService fileAttachmentService;

    private Teacher resolveTeacher(Long teacherId) {
        return teacherRepository.findById(teacherId)
                .orElseThrow(() -> new RuntimeException("Викладача не знайдено: " + teacherId));
    }

    // ══════════════════════════════════════════════
    //  OpenLesson — Відкриті/показові заняття
    // ══════════════════════════════════════════════

    @GetMapping("/open-lessons")
    public List<OpenLessonDto> getOpenLessons(@PathVariable Long teacherId) {
        return openLessonRepository.findByTeacherIdOrderByDateDesc(teacherId)
                .stream().map(OpenLessonDto::fromEntity).toList();
    }

    @PostMapping("/open-lessons")
    @ResponseStatus(HttpStatus.CREATED)
    public OpenLessonDto createOpenLesson(@PathVariable Long teacherId, @RequestBody OpenLessonDto dto) {
        Teacher teacher = resolveTeacher(teacherId);
        OpenLesson entity = OpenLesson.builder()
                .teacher(teacher)
                .topic(dto.getTopic())
                .date(dto.getDate())
                .hostDepartment(dto.getHostDepartment())
                .lessonType(dto.getLessonType())
                .orderNumber(dto.getOrderNumber())
                .orderDate(dto.getOrderDate())
                .notes(dto.getNotes())
                .documentUrl(dto.getDocumentUrl())
                .build();
        return OpenLessonDto.fromEntity(openLessonRepository.save(entity));
    }

    @PutMapping("/open-lessons/{id}")
    public OpenLessonDto updateOpenLesson(@PathVariable Long teacherId, @PathVariable Long id,
                                          @RequestBody OpenLessonDto dto) {
        OpenLesson entity = openLessonRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Запис не знайдено: " + id));
        entity.setTopic(dto.getTopic());
        entity.setDate(dto.getDate());
        entity.setHostDepartment(dto.getHostDepartment());
        entity.setLessonType(dto.getLessonType());
        entity.setOrderNumber(dto.getOrderNumber());
        entity.setOrderDate(dto.getOrderDate());
        entity.setNotes(dto.getNotes());
        entity.setDocumentUrl(dto.getDocumentUrl());
        return OpenLessonDto.fromEntity(openLessonRepository.save(entity));
    }

    @DeleteMapping("/open-lessons/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteOpenLesson(@PathVariable Long teacherId, @PathVariable Long id) {
        fileAttachmentService.deleteByEntity(EntityTypeConstants.OPEN_LESSON, id);
        openLessonRepository.deleteById(id);
    }

    // ══════════════════════════════════════════════
    //  MethodologicalExperiment — Методичні експерименти
    // ══════════════════════════════════════════════

    @GetMapping("/methodological-experiments")
    public List<MethodologicalExperimentDto> getExperiments(@PathVariable Long teacherId) {
        return experimentRepository.findByTeacherIdOrderByDateDesc(teacherId)
                .stream().map(MethodologicalExperimentDto::fromEntity).toList();
    }

    @PostMapping("/methodological-experiments")
    @ResponseStatus(HttpStatus.CREATED)
    public MethodologicalExperimentDto createExperiment(@PathVariable Long teacherId,
                                                        @RequestBody MethodologicalExperimentDto dto) {
        Teacher teacher = resolveTeacher(teacherId);
        MethodologicalExperiment entity = MethodologicalExperiment.builder()
                .teacher(teacher)
                .title(dto.getTitle())
                .description(dto.getDescription())
                .date(dto.getDate())
                .orderNumber(dto.getOrderNumber())
                .orderDate(dto.getOrderDate())
                .notes(dto.getNotes())
                .documentUrl(dto.getDocumentUrl())
                .build();
        return MethodologicalExperimentDto.fromEntity(experimentRepository.save(entity));
    }

    @PutMapping("/methodological-experiments/{id}")
    public MethodologicalExperimentDto updateExperiment(@PathVariable Long teacherId, @PathVariable Long id,
                                                        @RequestBody MethodologicalExperimentDto dto) {
        MethodologicalExperiment entity = experimentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Запис не знайдено: " + id));
        entity.setTitle(dto.getTitle());
        entity.setDescription(dto.getDescription());
        entity.setDate(dto.getDate());
        entity.setOrderNumber(dto.getOrderNumber());
        entity.setOrderDate(dto.getOrderDate());
        entity.setNotes(dto.getNotes());
        entity.setDocumentUrl(dto.getDocumentUrl());
        return MethodologicalExperimentDto.fromEntity(experimentRepository.save(entity));
    }

    @DeleteMapping("/methodological-experiments/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteExperiment(@PathVariable Long teacherId, @PathVariable Long id) {
        fileAttachmentService.deleteByEntity(EntityTypeConstants.METHODOLOGICAL_EXPERIMENT, id);
        experimentRepository.deleteById(id);
    }

    // ══════════════════════════════════════════════
    //  AcademicMobility — Академічна мобільність
    // ══════════════════════════════════════════════

    @GetMapping("/academic-mobilities")
    public List<AcademicMobilityDto> getMobilities(@PathVariable Long teacherId) {
        return mobilityRepository.findByTeacherIdOrderByDateFromDesc(teacherId)
                .stream().map(AcademicMobilityDto::fromEntity).toList();
    }

    @PostMapping("/academic-mobilities")
    @ResponseStatus(HttpStatus.CREATED)
    public AcademicMobilityDto createMobility(@PathVariable Long teacherId,
                                               @RequestBody AcademicMobilityDto dto) {
        Teacher teacher = resolveTeacher(teacherId);
        AcademicMobility entity = AcademicMobility.builder()
                .teacher(teacher)
                .programName(dto.getProgramName())
                .institution(dto.getInstitution())
                .country(dto.getCountry())
                .dateFrom(dto.getDateFrom())
                .dateTo(dto.getDateTo())
                .description(dto.getDescription())
                .notes(dto.getNotes())
                .documentUrl(dto.getDocumentUrl())
                .build();
        return AcademicMobilityDto.fromEntity(mobilityRepository.save(entity));
    }

    @PutMapping("/academic-mobilities/{id}")
    public AcademicMobilityDto updateMobility(@PathVariable Long teacherId, @PathVariable Long id,
                                               @RequestBody AcademicMobilityDto dto) {
        AcademicMobility entity = mobilityRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Запис не знайдено: " + id));
        entity.setProgramName(dto.getProgramName());
        entity.setInstitution(dto.getInstitution());
        entity.setCountry(dto.getCountry());
        entity.setDateFrom(dto.getDateFrom());
        entity.setDateTo(dto.getDateTo());
        entity.setDescription(dto.getDescription());
        entity.setNotes(dto.getNotes());
        entity.setDocumentUrl(dto.getDocumentUrl());
        return AcademicMobilityDto.fromEntity(mobilityRepository.save(entity));
    }

    @DeleteMapping("/academic-mobilities/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMobility(@PathVariable Long teacherId, @PathVariable Long id) {
        fileAttachmentService.deleteByEntity(EntityTypeConstants.ACADEMIC_MOBILITY, id);
        mobilityRepository.deleteById(id);
    }

    // ══════════════════════════════════════════════
    //  ForeignInternship — Міжнародне стажування
    // ══════════════════════════════════════════════

    @GetMapping("/foreign-internships")
    public List<ForeignInternshipDto> getForeignInternships(@PathVariable Long teacherId) {
        return foreignInternshipRepository.findByTeacherIdOrderByDateFromDesc(teacherId)
                .stream().map(ForeignInternshipDto::fromEntity).toList();
    }

    @PostMapping("/foreign-internships")
    @ResponseStatus(HttpStatus.CREATED)
    public ForeignInternshipDto createForeignInternship(@PathVariable Long teacherId,
                                                         @RequestBody ForeignInternshipDto dto) {
        Teacher teacher = resolveTeacher(teacherId);
        ForeignInternship entity = ForeignInternship.builder()
                .teacher(teacher)
                .programName(dto.getProgramName())
                .institution(dto.getInstitution())
                .country(dto.getCountry())
                .dateFrom(dto.getDateFrom())
                .dateTo(dto.getDateTo())
                .description(dto.getDescription())
                .notes(dto.getNotes())
                .documentUrl(dto.getDocumentUrl())
                .build();
        return ForeignInternshipDto.fromEntity(foreignInternshipRepository.save(entity));
    }

    @PutMapping("/foreign-internships/{id}")
    public ForeignInternshipDto updateForeignInternship(@PathVariable Long teacherId, @PathVariable Long id,
                                                         @RequestBody ForeignInternshipDto dto) {
        ForeignInternship entity = foreignInternshipRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Запис не знайдено: " + id));
        entity.setProgramName(dto.getProgramName());
        entity.setInstitution(dto.getInstitution());
        entity.setCountry(dto.getCountry());
        entity.setDateFrom(dto.getDateFrom());
        entity.setDateTo(dto.getDateTo());
        entity.setDescription(dto.getDescription());
        entity.setNotes(dto.getNotes());
        entity.setDocumentUrl(dto.getDocumentUrl());
        return ForeignInternshipDto.fromEntity(foreignInternshipRepository.save(entity));
    }

    @DeleteMapping("/foreign-internships/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteForeignInternship(@PathVariable Long teacherId, @PathVariable Long id) {
        fileAttachmentService.deleteByEntity(EntityTypeConstants.FOREIGN_INTERNSHIP, id);
        foreignInternshipRepository.deleteById(id);
    }
}
