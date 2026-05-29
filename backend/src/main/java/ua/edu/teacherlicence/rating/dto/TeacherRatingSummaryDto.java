package ua.edu.teacherlicence.rating.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeacherRatingSummaryDto {
    private Long teacherId;
    private String teacherName;
    private String departmentName;
    private String departmentNumber;
    private String position;
    private String militaryRank;
    private int totalScore;
    private int rank;
    private List<TeacherRatingDetailDto> details;
}
