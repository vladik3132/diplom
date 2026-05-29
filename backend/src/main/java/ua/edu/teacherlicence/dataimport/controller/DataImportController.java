package ua.edu.teacherlicence.dataimport.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import ua.edu.teacherlicence.ai.service.PublicationRelevanceAiService;
import ua.edu.teacherlicence.auth.service.CurrentUserProvider;
import ua.edu.teacherlicence.dataimport.service.AiImportService;
import ua.edu.teacherlicence.dataimport.service.DataImportService;
import ua.edu.teacherlicence.department.model.Department;
import ua.edu.teacherlicence.department.repository.DepartmentRepository;
import ua.edu.teacherlicence.opp.model.EducationalProgram;
import ua.edu.teacherlicence.opp.repository.EducationalProgramRepository;
import ua.edu.teacherlicence.ppdata.service.PpDataValidationService;
import ua.edu.teacherlicence.publication.model.ArticleCategory;
import ua.edu.teacherlicence.publication.model.Publication;
import ua.edu.teacherlicence.publication.model.PublicationStatus;
import ua.edu.teacherlicence.publication.model.PublicationType;
import ua.edu.teacherlicence.publication.repository.PublicationRepository;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/import")
@RequiredArgsConstructor
public class DataImportController {

    private final DataImportService dataImportService;
    private final CurrentUserProvider currentUser;
    private final PublicationRepository publicationRepository;
    private final DepartmentRepository departmentRepository;
    private final EducationalProgramRepository educationalProgramRepository;

    @Autowired(required = false)
    private AiImportService aiImportService;

    @Autowired(required = false)
    private PpDataValidationService ppDataValidationService;

    @Autowired(required = false)
    private PublicationRelevanceAiService relevanceAiService;

    @PreAuthorize("hasAnyRole('ADMIN', 'HEAD_OF_DEPARTMENT')")
    @PostMapping(value = "/docx", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DataImportService.ImportResult importDocx(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "departmentId", required = false) Long departmentId,
            @RequestParam(value = "useAi", required = false, defaultValue = "false") boolean useAi) throws IOException, AccessDeniedException {

        // HEAD can only import for own department
        if (!currentUser.isAdmin()) {
            Long headDeptId = currentUser.getCurrentDepartmentId();
            if (departmentId == null) {
                departmentId = headDeptId;
            } else {
                currentUser.checkDepartmentAccess(departmentId);
            }
        }

        log.info("=== IMPORT REQUEST: file='{}', size={}, contentType='{}', departmentId={}, useAi={} ===",
                file.getOriginalFilename(), file.getSize(), file.getContentType(), departmentId, useAi);

        DataImportService.ImportResult result;
        if (useAi && aiImportService != null) {
            log.info("Using AI-powered import (Mistral)");
            result = aiImportService.importFromDocx(file.getInputStream(), departmentId);
        } else {
            if (useAi && aiImportService == null) {
                log.warn("AI import requested but AI is disabled (ai.enabled != true). Falling back to regex parser.");
            }
            result = dataImportService.importFromDocx(file.getInputStream(), departmentId);
        }

        log.info("=== IMPORT RESULT: teachers={}, achievements={}, errors={} ===",
                result.teachersImported, result.achievementsImported, result.errors.size());

        // Автоматична ШІ-валідація ppData для імпортованих викладачів
        if (ppDataValidationService != null && !result.importedTeacherIds.isEmpty() && result.ppDataImported > 0) {
            for (Long tid : result.importedTeacherIds) {
                try {
                    log.info("Auto-validating ppData after import for teacher id={}", tid);
                    ppDataValidationService.validateAll(tid);
                } catch (Exception e) {
                    log.warn("Failed ppData auto-validation after import for teacher {}: {}", tid, e.getMessage());
                }
            }
        }

        // Перевірка відповідності публікацій напряму кафедри (пакетна AI-перевірка)
        if (relevanceAiService != null && departmentId != null && result.publicationsImported > 0) {
            checkPublicationRelevanceBatch(departmentId, result);
        }

        return result;
    }

    /**
     * Пакетна перевірка відповідності імпортованих фахових/Scopus публікацій напряму кафедри.
     */
    private void checkPublicationRelevanceBatch(Long departmentId, DataImportService.ImportResult result) {
        try {
            Department dept = departmentRepository.findById(departmentId).orElse(null);
            if (dept == null) return;

            List<String> specialties = educationalProgramRepository.findByDepartmentId(departmentId)
                    .stream()
                    .map(EducationalProgram::getSpecialty)
                    .filter(s -> s != null && !s.isBlank())
                    .distinct()
                    .toList();

            // Збираємо фахові/Scopus публікації імпортованих викладачів
            List<Publication> articlesToCheck = new java.util.ArrayList<>();
            for (Long tid : result.importedTeacherIds) {
                publicationRepository.findByTeacherId(tid).stream()
                        .filter(p -> p.getType() == PublicationType.ARTICLE)
                        .filter(p -> p.getArticleCategory() != null)
                        .filter(p -> p.getFieldRelevant() == null) // ще не перевірені
                        .forEach(articlesToCheck::add);
            }

            if (articlesToCheck.isEmpty()) return;

            log.info("Checking field relevance for {} publications against department '{}'",
                    articlesToCheck.size(), dept.getName());

            List<String> titles = articlesToCheck.stream()
                    .map(Publication::getTitle)
                    .toList();

            Map<String, Boolean> relevanceResults = relevanceAiService.checkRelevanceBatch(
                    titles, dept.getName(), dept.getId(), specialties
            );

            int notRelevantCount = 0;
            for (Publication pub : articlesToCheck) {
                Boolean relevant = relevanceResults.get(pub.getTitle());
                if (relevant != null) {
                    pub.setFieldRelevant(relevant);
                    if (!relevant) {
                        notRelevantCount++;
                        if (pub.getStatus() != PublicationStatus.OUTDATED) {
                            pub.setStatus(PublicationStatus.NEEDS_ATTENTION);
                        }
                    }
                    publicationRepository.save(pub);
                }
            }

            log.info("Publication relevance check done: {} total, {} not relevant",
                    articlesToCheck.size(), notRelevantCount);
            if (notRelevantCount > 0) {
                result.errors.add(notRelevantCount + " публікацій не відповідають напряму кафедри — потребують уваги");
            }
        } catch (Exception e) {
            log.warn("Failed batch publication relevance check: {}", e.getMessage());
        }
    }
}
