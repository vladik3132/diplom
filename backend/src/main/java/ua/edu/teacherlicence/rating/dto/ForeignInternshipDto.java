package ua.edu.teacherlicence.rating.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ua.edu.teacherlicence.rating.model.ForeignInternship;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ForeignInternshipDto {
    private Long id;
    private Long teacherId;
    private String programName;
    private String institution;
    private String country;
    private LocalDate dateFrom;
    private LocalDate dateTo;
    private String description;
    private String notes;
    private String documentUrl;

    public static ForeignInternshipDto fromEntity(ForeignInternship e) {
        return ForeignInternshipDto.builder()
                .id(e.getId())
                .teacherId(e.getTeacher() != null ? e.getTeacher().getId() : null)
                .programName(e.getProgramName())
                .institution(e.getInstitution())
                .country(e.getCountry())
                .dateFrom(e.getDateFrom())
                .dateTo(e.getDateTo())
                .description(e.getDescription())
                .notes(e.getNotes())
                .documentUrl(e.getDocumentUrl())
                .build();
    }
}
