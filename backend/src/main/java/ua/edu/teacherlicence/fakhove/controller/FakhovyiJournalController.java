package ua.edu.teacherlicence.fakhove.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import ua.edu.teacherlicence.fakhove.dto.VerificationResult;
import ua.edu.teacherlicence.fakhove.model.FakhovyiJournal;
import ua.edu.teacherlicence.fakhove.model.ScopusJournal;
import ua.edu.teacherlicence.fakhove.service.FakhovyiJournalService;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/fakhovi-journals")
@RequiredArgsConstructor
public class FakhovyiJournalController {

    private final FakhovyiJournalService fakhovyiJournalService;

    /**
     * Імпорт реєстру фахових видань МОН з Excel-файлу.
     * Видаляє всі існуючі записи та завантажує нові.
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping(value = "/upload-fakhovi", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> uploadFakhovi(@RequestParam("file") MultipartFile file) throws IOException {
        log.info("Uploading fakhovi journals Excel: name='{}', size={}", file.getOriginalFilename(), file.getSize());
        int count = fakhovyiJournalService.importFakhoviFromExcel(file.getInputStream());
        return Map.of("imported", count, "message", "Фахові видання імпортовано успішно");
    }

    /**
     * Імпорт Scopus Source List з Excel-файлу.
     * Видаляє всі існуючі записи та завантажує нові.
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping(value = "/upload-scopus", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> uploadScopus(@RequestParam("file") MultipartFile file) throws IOException {
        log.info("Uploading Scopus journals Excel: name='{}', size={}", file.getOriginalFilename(), file.getSize());
        int count = fakhovyiJournalService.importScopusFromExcel(file.getInputStream());
        return Map.of("imported", count, "message", "Scopus журнали імпортовано успішно");
    }

    /**
     * Список усіх фахових видань.
     */
    @GetMapping
    public List<FakhovyiJournal> getAll() {
        return fakhovyiJournalService.findAll();
    }

    /**
     * Пошук фахових видань за назвою.
     */
    @GetMapping("/search")
    public List<FakhovyiJournal> search(@RequestParam("q") String query) {
        return fakhovyiJournalService.searchByName(query);
    }

    /**
     * Список усіх Scopus джерел.
     */
    @GetMapping("/scopus")
    public List<ScopusJournal> getAllScopus() {
        return fakhovyiJournalService.findAllScopus();
    }

    /**
     * Перевірка журналу у реєстрі фахових видань та Scopus.
     */
    @GetMapping("/verify")
    public VerificationResult verify(
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "issn", required = false) String issn) {
        return fakhovyiJournalService.verifyJournal(name, issn);
    }

    /**
     * Кількість записів (фахові + Scopus).
     */
    @GetMapping("/count")
    public Map<String, Long> count() {
        return Map.of(
                "fakhovi", fakhovyiJournalService.countFakhovi(),
                "scopus", fakhovyiJournalService.countScopus()
        );
    }
}
