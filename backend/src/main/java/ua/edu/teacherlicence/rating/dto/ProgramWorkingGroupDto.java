package ua.edu.teacherlicence.rating.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ua.edu.teacherlicence.rating.model.ProgramWorkingGroup;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProgramWorkingGroupDto {
    private Long id;
    private Long programId;
    private Long teacherId;
    private String teacherName;
    private String role; // CHAIR or MEMBER
    private String orderNumber;
    private LocalDate orderDate;

    public static ProgramWorkingGroupDto fromEntity(ProgramWorkingGroup e) {
        String name = "";
        if (e.getTeacher() != null) {
            name = e.getTeacher().getLastName() + " " + e.getTeacher().getFirstName()
                    + (e.getTeacher().getPatronymic() != null ? " " + e.getTeacher().getPatronymic() : "");
        }
        return ProgramWorkingGroupDto.builder()
                .id(e.getId())
                .programId(e.getProgram() != null ? e.getProgram().getId() : null)
                .teacherId(e.getTeacher() != null ? e.getTeacher().getId() : null)
                .teacherName(name.trim())
                .role(e.getRole() != null ? e.getRole().name() : null)
                .orderNumber(e.getOrderNumber())
                .orderDate(e.getOrderDate())
                .build();
    }
}
