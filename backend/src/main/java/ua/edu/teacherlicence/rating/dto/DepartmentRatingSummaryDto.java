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
public class DepartmentRatingSummaryDto {
    private Long departmentId;
    private String departmentName;
    private String facultyName;
    private int teacherCount;
    private int totalScore;
    private double averageScore;
    private int rank;
    private List<TeacherRatingSummaryDto> teachers;
}
