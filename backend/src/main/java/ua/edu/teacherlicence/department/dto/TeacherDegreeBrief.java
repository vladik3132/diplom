package ua.edu.teacherlicence.department.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Коротка інформація про викладача зі ступенем — для tooltip-ів
 * на статистичних картках сторінки кафедри.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeacherDegreeBrief {
    private Long teacherId;
    /** ПІБ у короткій формі ("Шевченко О.І."). */
    private String fullName;
    /** Назва ступеня ("Доктор технічних наук", "Кандидат наук" тощо). */
    private String degreeName;
    /** Спеціальність ступеня (шифр+назва). Може бути null. */
    private String speciality;
}
