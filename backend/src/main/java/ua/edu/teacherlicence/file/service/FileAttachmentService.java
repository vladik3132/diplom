package ua.edu.teacherlicence.file.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import ua.edu.teacherlicence.file.model.EntityTypeConstants;
import ua.edu.teacherlicence.file.model.FileAttachment;
import ua.edu.teacherlicence.file.repository.FileAttachmentRepository;
import ua.edu.teacherlicence.ppdata.repository.*;
import ua.edu.teacherlicence.publication.repository.PublicationRepository;
import ua.edu.teacherlicence.qualification.repository.QualificationImprovementRepository;
import ua.edu.teacherlicence.teacher.model.Teacher;
import ua.edu.teacherlicence.teacher.repository.LanguageSkillRepository;
import ua.edu.teacherlicence.teacher.repository.TeacherRepository;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileAttachmentService {

    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "application/pdf", "image/jpeg", "image/png",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document" // .docx
    );

    private final FileAttachmentRepository repository;
    private final FileStorageService storageService;
    private final TeacherRepository teacherRepository;
    private final PublicationRepository publicationRepository;
    private final QualificationImprovementRepository qualificationImprovementRepository;
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
    private final LanguageSkillRepository languageSkillRepository;

    @Value("${app.google-drive.max-file-size-mb:20}")
    private int maxFileSizeMb;

    // ── Upload ──────────────────────────────────────────────────────

    @Transactional
    public FileAttachment upload(MultipartFile file, String entityType, Long entityId,
                                 Long teacherId, String uploadedBy) throws IOException {
        // Validate size
        long maxBytes = (long) maxFileSizeMb * 1024 * 1024;
        if (file.getSize() > maxBytes) {
            throw new IllegalArgumentException("Файл занадто великий. Максимум: " + maxFileSizeMb + " МБ");
        }

        // Validate MIME type
        String mimeType = file.getContentType();
        if (mimeType == null || !ALLOWED_MIME_TYPES.contains(mimeType)) {
            throw new IllegalArgumentException("Непідтримуваний тип файлу. Дозволено: PDF, JPEG, PNG, DOCX");
        }

        // Build folder path: Кафедра / Прізвище_Імʼя / ТипДокумента
        String folderPath = buildFolderPath(entityType, teacherId);

        // Upload to storage
        String fileReference = storageService.upload(
                file.getInputStream(), file.getOriginalFilename(), mimeType, folderPath);

        // Determine if it's Drive or local
        boolean isDriveId = !fileReference.contains("/") && !fileReference.contains("\\");

        // Save metadata
        FileAttachment attachment = FileAttachment.builder()
                .entityType(entityType)
                .entityId(entityId)
                .driveFileId(isDriveId ? fileReference : null)
                .localPath(isDriveId ? null : fileReference)
                .originalName(file.getOriginalFilename())
                .mimeType(mimeType)
                .fileSize(file.getSize())
                .uploadedBy(uploadedBy)
                .build();

        return repository.save(attachment);
    }

    // ── List ────────────────────────────────────────────────────────

    public List<FileAttachment> findByEntity(String entityType, Long entityId) {
        return repository.findByEntityTypeAndEntityId(entityType, entityId);
    }

    public FileAttachment findById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Файл не знайдено: " + id));
    }

    // ── Download ────────────────────────────────────────────────────

    public byte[] download(FileAttachment attachment) throws IOException {
        String ref = attachment.getDriveFileId() != null
                ? attachment.getDriveFileId()
                : attachment.getLocalPath();
        if (ref == null) {
            throw new IOException("Файл не має посилання на сховище");
        }
        return storageService.download(ref);
    }

    // ── Delete ──────────────────────────────────────────────────────

    @Transactional
    public void delete(FileAttachment attachment) throws IOException {
        deleteFromStorage(attachment);
        repository.delete(attachment);
    }

    /**
     * Delete all files attached to a specific entity (cascade delete).
     * Call this before deleting the parent entity.
     */
    @Transactional
    public void deleteByEntity(String entityType, Long entityId) {
        List<FileAttachment> attachments = repository.findByEntityTypeAndEntityId(entityType, entityId);
        for (FileAttachment fa : attachments) {
            try {
                deleteFromStorage(fa);
            } catch (IOException e) {
                log.warn("Failed to delete file from storage: {} — {}", fa.getOriginalName(), e.getMessage());
            }
        }
        repository.deleteAll(attachments);
    }

    private void deleteFromStorage(FileAttachment attachment) throws IOException {
        String ref = attachment.getDriveFileId() != null
                ? attachment.getDriveFileId()
                : attachment.getLocalPath();
        if (ref != null) {
            storageService.delete(ref);
        }
    }

    /**
     * Delete ALL files associated with a teacher across all entity types.
     * Call this before deleting the teacher entity.
     */
    @Transactional
    public void deleteAllFilesForTeacher(Long teacherId) {
        // Collect entity IDs per entity type
        Map<String, List<Long>> entityIdsByType = Map.ofEntries(
                Map.entry(EntityTypeConstants.PUBLICATION,
                        publicationRepository.findByTeacherId(teacherId).stream().map(e -> e.getId()).collect(Collectors.toList())),
                Map.entry(EntityTypeConstants.QUALIFICATION,
                        qualificationImprovementRepository.findByTeacherId(teacherId).stream().map(e -> e.getId()).collect(Collectors.toList())),
                Map.entry(EntityTypeConstants.PP_SCIENTIFIC_SUPERVISION,
                        scientificSupervisionRepo.findByTeacherId(teacherId).stream().map(e -> e.getId()).collect(Collectors.toList())),
                Map.entry(EntityTypeConstants.PP_ATTESTATION_ACTIVITY,
                        attestationActivityRepo.findByTeacherId(teacherId).stream().map(e -> e.getId()).collect(Collectors.toList())),
                Map.entry(EntityTypeConstants.PP_EDITORIAL_ACTIVITY,
                        editorialActivityRepo.findByTeacherId(teacherId).stream().map(e -> e.getId()).collect(Collectors.toList())),
                Map.entry(EntityTypeConstants.PP_EXPERT_COUNCIL,
                        expertCouncilRepo.findByTeacherId(teacherId).stream().map(e -> e.getId()).collect(Collectors.toList())),
                Map.entry(EntityTypeConstants.PP_INTERNATIONAL_PROJECT,
                        internationalProjectRepo.findByTeacherId(teacherId).stream().map(e -> e.getId()).collect(Collectors.toList())),
                Map.entry(EntityTypeConstants.PP_SCIENTIFIC_CONSULTING,
                        scientificConsultingRepo.findByTeacherId(teacherId).stream().map(e -> e.getId()).collect(Collectors.toList())),
                Map.entry(EntityTypeConstants.PP_FOREIGN_LANGUAGE_TEACHING,
                        foreignLanguageTeachingRepo.findByTeacherId(teacherId).stream().map(e -> e.getId()).collect(Collectors.toList())),
                Map.entry(EntityTypeConstants.PP_OLYMPIAD_GUIDANCE,
                        olympiadGuidanceRepo.findByTeacherId(teacherId).stream().map(e -> e.getId()).collect(Collectors.toList())),
                Map.entry(EntityTypeConstants.PP_MILITARY_MISSION,
                        militaryMissionRepo.findByTeacherId(teacherId).stream().map(e -> e.getId()).collect(Collectors.toList())),
                Map.entry(EntityTypeConstants.PP_PROFESSIONAL_ASSOCIATION,
                        professionalAssociationRepo.findByTeacherId(teacherId).stream().map(e -> e.getId()).collect(Collectors.toList())),
                Map.entry(EntityTypeConstants.PP_PRACTICAL_EXPERIENCE,
                        practicalExperienceRepo.findByTeacherId(teacherId).stream().map(e -> e.getId()).collect(Collectors.toList()))
        );

        for (Map.Entry<String, List<Long>> entry : entityIdsByType.entrySet()) {
            for (Long entityId : entry.getValue()) {
                deleteByEntity(entry.getKey(), entityId);
            }
        }

        // Delete teacher-level files (photo uses teacherId as entityId)
        deleteByEntity(EntityTypeConstants.TEACHER_PHOTO, teacherId);

        // Language skills use their own IDs as entityId
        for (var ls : languageSkillRepository.findByTeacherId(teacherId)) {
            deleteByEntity(EntityTypeConstants.LANGUAGE_SKILL, ls.getId());
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────

    private String buildFolderPath(String entityType, Long teacherId) {
        if (teacherId == null) {
            return EntityTypeConstants.getFolderLabel(entityType);
        }

        Teacher teacher = teacherRepository.findById(teacherId).orElse(null);
        if (teacher == null) {
            return EntityTypeConstants.getFolderLabel(entityType);
        }

        String deptName = teacher.getDepartment() != null
                ? sanitizeFolderName(teacher.getDepartment().getName())
                : "Без_кафедри";

        String teacherName = sanitizeFolderName(
                teacher.getLastName() + "_" + teacher.getFirstName());

        String docType = EntityTypeConstants.getFolderLabel(entityType);

        return deptName + "/" + teacherName + "/" + docType;
    }

    private String sanitizeFolderName(String name) {
        if (name == null) return "unknown";
        return name.replaceAll("[/\\\\:*?\"<>|]", "_").trim();
    }
}
