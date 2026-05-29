package ua.edu.teacherlicence.rating.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ua.edu.teacherlicence.rating.model.TeacherRating;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeacherRatingDetailDto {
    private Long id;
    private String criterion;
    private String criterionLabel;
    private int count;
    private int pointsPerUnit;
    private int score;

    public static TeacherRatingDetailDto fromEntity(TeacherRating tr) {
        return TeacherRatingDetailDto.builder()
                .id(tr.getId())
                .criterion(tr.getCriterion().name())
                .criterionLabel(tr.getCriterion().getLabel())
                .count(tr.getCount())
                .pointsPerUnit(tr.getCriterion().getPoints())
                .score(tr.getScore())
                .build();
    }
}
