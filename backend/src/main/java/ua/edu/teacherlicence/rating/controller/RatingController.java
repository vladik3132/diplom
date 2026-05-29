package ua.edu.teacherlicence.rating.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import ua.edu.teacherlicence.rating.dto.*;
import ua.edu.teacherlicence.rating.model.ProtocolSettings;
import ua.edu.teacherlicence.rating.model.RatingCriterion;
import ua.edu.teacherlicence.rating.repository.ProtocolSettingsRepository;
import ua.edu.teacherlicence.rating.service.CriterionRecordsService;
import ua.edu.teacherlicence.rating.service.RatingCalculationService;
import ua.edu.teacherlicence.rating.service.RatingProtocolService;
import ua.edu.teacherlicence.rating.service.RatingService;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rating")
@RequiredArgsConstructor
public class RatingController {

    private final RatingService ratingService;
    private final RatingCalculationService calculationService;
    private final CriterionRecordsService criterionRecordsService;
    private final RatingProtocolService protocolService;
    private final ProtocolSettingsRepository protocolSettingsRepository;

    // ── Періоди ──

    @GetMapping("/periods")
    public List<RatingPeriodDto> getPeriods() {
        return ratingService.getAllPeriods();
    }

    @PostMapping("/periods")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'HEAD_OF_DEPARTMENT')")
    public RatingPeriodDto createPeriod(@RequestBody RatingPeriodDto dto) {
        return ratingService.createPeriod(dto);
    }

    @PostMapping("/periods/for-year/{year}")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'HEAD_OF_DEPARTMENT')")
    public RatingPeriodDto createPeriodForYear(@PathVariable int year) {
        return ratingService.createPeriodForYear(year);
    }

    // ── Обрахунок (тільки адмін або начальник кафедри) ──

    @PostMapping("/calculate/{periodId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'HEAD_OF_DEPARTMENT')")
    public Map<String, Object> calculateAll(@PathVariable Long periodId) {
        int count = calculationService.calculateForAll(periodId);
        return Map.of("teachersCalculated", count);
    }

    @PostMapping("/calculate/{periodId}/teacher/{teacherId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'HEAD_OF_DEPARTMENT')")
    public TeacherRatingSummaryDto calculateForTeacher(@PathVariable Long periodId, @PathVariable Long teacherId) {
        calculationService.calculateForTeacher(teacherId, periodId);
        return ratingService.getTeacherRatingDetails(periodId, teacherId);
    }

    // ── Зведені дані ──

    @GetMapping("/{periodId}/teachers")
    public List<TeacherRatingSummaryDto> getTeacherRankings(
            @PathVariable Long periodId,
            @RequestParam(required = false) Long departmentId) {
        return ratingService.getTeacherRankings(periodId, departmentId);
    }

    @GetMapping("/{periodId}/teachers/{teacherId}")
    public TeacherRatingSummaryDto getTeacherDetails(@PathVariable Long periodId, @PathVariable Long teacherId) {
        return ratingService.getTeacherRatingDetails(periodId, teacherId);
    }

    @GetMapping("/{periodId}/departments")
    public List<DepartmentRatingSummaryDto> getDepartmentRankings(@PathVariable Long periodId) {
        return ratingService.getDepartmentRankings(periodId);
    }

    @GetMapping("/{periodId}/faculties")
    public List<FacultyRatingSummaryDto> getFacultyRankings(@PathVariable Long periodId) {
        return ratingService.getFacultyRankings(periodId);
    }

    // ── Протокол (тільки адмін) ──

    @PostMapping("/{periodId}/protocol")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<byte[]> generateProtocol(
            @PathVariable Long periodId,
            @RequestBody ProtocolRequest request) throws IOException {
        byte[] docx = protocolService.generateProtocol(periodId, request);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"protocol_rating.docx\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .body(docx);
    }

    // ── Налаштування протоколу (тільки адмін) ──

    @GetMapping("/protocol-settings")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> getProtocolSettings() {
        String json = protocolSettingsRepository.findAll().stream()
                .findFirst()
                .map(ProtocolSettings::getSettingsJson)
                .orElse("{}");
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(json,
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }

    @PostMapping("/protocol-settings")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> saveProtocolSettings(@RequestBody Map<String, Object> settingsMap) {
        String json;
        try {
            json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(settingsMap);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
        ProtocolSettings settings = protocolSettingsRepository.findAll().stream()
                .findFirst()
                .orElse(new ProtocolSettings());
        settings.setSettingsJson(json);
        protocolSettingsRepository.save(settings);
        return ResponseEntity.ok().build();
    }

    // ── Записи за критерієм ──

    @GetMapping("/{periodId}/teachers/{teacherId}/criterion/{criterion}/records")
    public List<CriterionRecordDto> getCriterionRecords(
            @PathVariable Long periodId,
            @PathVariable Long teacherId,
            @PathVariable RatingCriterion criterion) {
        return criterionRecordsService.getRecords(periodId, teacherId, criterion);
    }

    // ── Налаштування кафедр у рейтингу (тільки адмін) ──

    /** Список усіх кафедр з прапорцем "виключено з рейтингу". */
    @GetMapping("/department-settings")
    @PreAuthorize("hasRole('ADMIN')")
    public List<RatingDepartmentSettingDto> getDepartmentSettings() {
        return ratingService.getDepartmentSettings();
    }

    /**
     * Bulk-оновлення: тіло {@code excludedDepartmentIds} — id кафедр
     * що мають бути виключені з рейтингу. Усі інші автоматично включаються.
     */
    @PutMapping("/department-settings")
    @PreAuthorize("hasRole('ADMIN')")
    public List<RatingDepartmentSettingDto> updateDepartmentSettings(
            @RequestBody RatingDepartmentSettingsRequest request) {
        return ratingService.updateDepartmentSettings(request.getExcludedDepartmentIds());
    }
}
