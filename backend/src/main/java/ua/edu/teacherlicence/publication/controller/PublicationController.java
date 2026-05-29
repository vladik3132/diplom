package ua.edu.teacherlicence.publication.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import ua.edu.teacherlicence.auth.service.CurrentUserProvider;
import ua.edu.teacherlicence.file.model.EntityTypeConstants;
import ua.edu.teacherlicence.file.service.FileAttachmentService;
import ua.edu.teacherlicence.notification.service.ChangeNotificationService;
import ua.edu.teacherlicence.notification.service.FieldDiff;
import org.springframework.beans.factory.annotation.Autowired;
import ua.edu.teacherlicence.publication.model.ApprobationSubtype;
import ua.edu.teacherlicence.publication.model.ArticleCategory;
import ua.edu.teacherlicence.publication.model.MethodicalSubtype;
import ua.edu.teacherlicence.publication.model.Publication;
import ua.edu.teacherlicence.publication.model.PublicationStatus;
import ua.edu.teacherlicence.publication.model.PublicationType;
import ua.edu.teacherlicence.publication.service.PublicationService;
import ua.edu.teacherlicence.scopus.ScopusApiService;
import ua.edu.teacherlicence.scopus.ScopusVerificationResult;
import ua.edu.teacherlicence.user.model.User;

import java.nio.file.AccessDeniedException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/publications")
@RequiredArgsConstructor
public class PublicationController {

    private final PublicationService publicationService;
    private final CurrentUserProvider currentUser;
    private final ChangeNotificationService changeNotificationService;
    private final FileAttachmentService fileAttachmentService;

    @Autowired(required = false)
    private ScopusApiService scopusApiService;

    @GetMapping
    public List<Publication> getAll(@RequestParam(required = false) Long teacherId) throws AccessDeniedException {
        if (currentUser.isTeacher()) {
            User user = currentUser.getCurrentUser();
            return user.getTeacherId() != null
                    ? publicationService.findByTeacherId(user.getTeacherId())
                    : List.of();
        }
        if (teacherId != null) {
            if (!currentUser.isAdmin()) currentUser.checkTeacherAccess(teacherId);
            return publicationService.findByTeacherId(teacherId);
        }
        return publicationService.findAll();
    }

    @GetMapping("/{id}")
    public Publication getById(@PathVariable Long id) throws AccessDeniedException {
        Publication pub = publicationService.findById(id);
        if (pub.getTeacher() != null && !currentUser.isAdmin()) {
            currentUser.checkTeacherAccess(pub.getTeacher().getId());
        }
        return pub;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Publication create(@RequestBody Publication publication) throws AccessDeniedException {
        if (currentUser.isTeacher()) {
            if (publication.getTeacher() == null) {
                publication.setTeacher(currentUser.getCurrentTeacher());
            } else {
                currentUser.checkTeacherAccess(publication.getTeacher().getId());
            }
        }
        Publication saved = publicationService.create(publication);
        if (saved.getTeacher() != null) {
            changeNotificationService.notifyDataChanged(saved.getTeacher(), currentUser.getCurrentUser(),
                    "додано", "Публікації", saved.getTitle());
        }
        return saved;
    }

    @PutMapping("/{id}")
    public Publication update(@PathVariable Long id, @RequestBody Publication publication) throws AccessDeniedException {
        Publication existing = publicationService.findById(id);
        if (existing.getTeacher() != null && !currentUser.isAdmin()) {
            currentUser.checkTeacherAccess(existing.getTeacher().getId());
        }
        // Зберігаємо старі значення для порівняння
        String oldTitle = existing.getTitle();
        String oldJournal = existing.getJournalName();
        Integer oldYear = existing.getYear();
        String oldAuthors = existing.getAuthors();

        Publication saved = publicationService.update(id, publication);
        if (saved.getTeacher() != null) {
            FieldDiff diff = new FieldDiff()
                    .compare("Назва", oldTitle, saved.getTitle())
                    .compare("Журнал", oldJournal, saved.getJournalName())
                    .compare("Рік", oldYear, saved.getYear())
                    .compare("Автори", oldAuthors, saved.getAuthors());
            String details = diff.hasChanges()
                    ? saved.getTitle() + " | " + diff.build()
                    : saved.getTitle();
            changeNotificationService.notifyDataChanged(saved.getTeacher(), currentUser.getCurrentUser(),
                    "оновлено", "Публікації", details);
        }
        return saved;
    }

    /**
     * Оновити статус публікації (HEAD_VALIDATED, NEEDS_ATTENTION тощо).
     * Доступно для HEAD_OF_DEPARTMENT та ADMIN.
     */
    @PatchMapping("/{id}/status")
    public Publication updateStatus(@PathVariable Long id, @RequestBody Map<String, String> body) throws AccessDeniedException {
        Publication existing = publicationService.findById(id);
        if (!currentUser.isAdmin() && !currentUser.isHead()) {
            throw new AccessDeniedException("Тільки начальник кафедри або адмін може змінювати статус");
        }
        if (existing.getTeacher() != null && !currentUser.isAdmin()) {
            currentUser.checkTeacherAccess(existing.getTeacher().getId());
        }
        String statusStr = body.get("status");
        if (statusStr == null) {
            throw new RuntimeException("Не вказано статус");
        }
        PublicationStatus newStatus = PublicationStatus.valueOf(statusStr);
        existing.setStatus(newStatus);
        Publication saved = publicationService.saveDirectly(existing);
        if (saved.getTeacher() != null) {
            changeNotificationService.notifyDataChanged(saved.getTeacher(), currentUser.getCurrentUser(),
                    "статус змінено", "Публікації",
                    saved.getTitle() + " → " + newStatus.name());
        }
        return saved;
    }

    /**
     * Швидка зміна типу та/або категорії публікації (тільки ADMIN).
     * Використовує saveDirectly — без перевалідації журналу.
     */
    @PatchMapping("/{id}/classify")
    public Publication classify(@PathVariable Long id, @RequestBody Map<String, String> body) throws AccessDeniedException {
        if (!currentUser.isAdmin()) {
            throw new AccessDeniedException("Тільки адмін може змінювати класифікацію");
        }
        Publication existing = publicationService.findById(id);

        boolean changed = false;
        StringBuilder details = new StringBuilder(existing.getTitle() != null ? existing.getTitle() : "");

        // Тип публікації
        String typeStr = body.get("type");
        if (typeStr != null) {
            PublicationType newType = PublicationType.valueOf(typeStr);
            if (existing.getType() != newType) {
                details.append(" | тип: ").append(existing.getType()).append(" → ").append(newType);
                existing.setType(newType);
                // Автоматично оновити ppType при зміні типу
                int pp = PublicationService.inferPpFromType(newType);
                var ppType = ua.edu.teacherlicence.achievement.model.AchievementType.fromNumber(pp);
                if (ppType != null) {
                    existing.setPpType(ppType);
                    existing.setSourceSection("pp." + pp);
                }
                // Очистити зайві поля при зміні типу
                if (newType != PublicationType.ARTICLE) {
                    existing.setArticleCategory(null);
                }
                if (newType != PublicationType.METHODICAL) {
                    existing.setMethodicalSubtype(null);
                }
                if (newType != PublicationType.APPROBATION && newType != PublicationType.POPULAR_SCIENTIFIC) {
                    existing.setApprobationSubtype(null);
                }
                changed = true;
            }
        }

        // Категорія статті
        String catStr = body.get("articleCategory");
        if (catStr != null) {
            ArticleCategory newCat = catStr.isEmpty() ? null : ArticleCategory.valueOf(catStr);
            if (existing.getArticleCategory() != newCat) {
                details.append(" | категорія: ").append(existing.getArticleCategory()).append(" → ").append(newCat);
                existing.setArticleCategory(newCat);
                changed = true;
            }
        }

        // Підтип методичної праці
        String methStr = body.get("methodicalSubtype");
        if (methStr != null) {
            MethodicalSubtype newSub = methStr.isEmpty() ? null : MethodicalSubtype.valueOf(methStr);
            if (existing.getMethodicalSubtype() != newSub) {
                details.append(" | підтип: ").append(existing.getMethodicalSubtype()).append(" → ").append(newSub);
                existing.setMethodicalSubtype(newSub);
                changed = true;
            }
        }

        // Рівень видання (апробації)
        String appStr = body.get("approbationSubtype");
        if (appStr != null) {
            ApprobationSubtype newSub = appStr.isEmpty() ? null : ApprobationSubtype.valueOf(appStr);
            if (existing.getApprobationSubtype() != newSub) {
                details.append(" | рівень: ").append(existing.getApprobationSubtype()).append(" → ").append(newSub);
                existing.setApprobationSubtype(newSub);
                changed = true;
            }
        }

        if (!changed) return existing;

        Publication saved = publicationService.saveDirectly(existing);
        if (saved.getTeacher() != null) {
            changeNotificationService.notifyDataChanged(saved.getTeacher(), currentUser.getCurrentUser(),
                    "класифікацію змінено", "Публікації", details.toString());
        }
        return saved;
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) throws AccessDeniedException {
        Publication existing = publicationService.findById(id);
        if (existing.getTeacher() != null && !currentUser.isAdmin()) {
            currentUser.checkTeacherAccess(existing.getTeacher().getId());
        }
        if (existing.getTeacher() != null) {
            changeNotificationService.notifyDataChanged(existing.getTeacher(), currentUser.getCurrentUser(),
                    "видалено", "Публікації", existing.getTitle());
        }
        fileAttachmentService.deleteByEntity(EntityTypeConstants.PUBLICATION, id);
        publicationService.delete(id);
    }

    /**
     * Manual Scopus verification for a single publication.
     * Checks Scopus API and auto-updates category if confirmed.
     */
    @PostMapping("/{id}/verify-scopus")
    public ScopusVerificationResult verifyScopus(@PathVariable Long id) throws AccessDeniedException {
        if (scopusApiService == null) {
            return ScopusVerificationResult.error("Scopus API не налаштовано");
        }
        Publication pub = publicationService.findById(id);
        if (pub.getTeacher() != null && !currentUser.isAdmin()) {
            currentUser.checkTeacherAccess(pub.getTeacher().getId());
        }
        if (pub.getTeacher() == null) {
            return ScopusVerificationResult.error("Публікація не прив'язана до викладача");
        }
        ScopusVerificationResult result = scopusApiService.verifyPublication(
                pub.getTitle(),
                pub.getDoi(),
                pub.getTeacher().getScopusId(),
                pub.getTeacher().getLastName(),
                pub.getTeacher().getFirstName()
        );
        // Auto-update publication if Scopus confirmed
        if (result.isConfirmed() && pub.getArticleCategory() != ArticleCategory.SCOPUS) {
            pub.setArticleCategory(ArticleCategory.SCOPUS);
            publicationService.saveDirectly(pub);
        }
        return result;
    }

    /**
     * Перекласифікація підтипів усіх METHODICAL/APPROBATION публікацій (де підтип null).
     * POST /api/publications/reclassify-subtypes
     */
    @PostMapping("/reclassify-subtypes")
    public Map<String, Object> reclassifySubtypes() {
        int count = publicationService.reclassifySubtypes();
        return Map.of("reclassified", count);
    }

    /**
     * POST /api/publications/reclassify-subtypes/department/{departmentId}
     * Перекласифікація лише для викладачів конкретної кафедри.
     */
    @PostMapping("/reclassify-subtypes/department/{departmentId}")
    public Map<String, Object> reclassifySubtypesForDepartment(@PathVariable Long departmentId) throws AccessDeniedException {
        if (!currentUser.isAdmin()) {
            currentUser.checkDepartmentAccess(departmentId);
        }
        int count = publicationService.reclassifySubtypes(departmentId);
        return Map.of("reclassified", count, "departmentId", departmentId);
    }
}
