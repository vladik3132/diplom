package ua.edu.teacherlicence.department.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Коротка інформація про викладача зі вченим званням — для tooltip-ів
 * на статистичних картках сторінки кафедри.
 * Назва звання вже містить кафедру/спеціальність атестата
 * (напр. "Доцент кафедри комп'ютерних інформаційних технологій").
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeacherTitleBrief {
    private Long teacherId;
    /** ПІБ у короткій формі ("Шевченко О.І."). */
    private String fullName;
    /** Повна назва звання включно з кафедрою/спеціальністю. */
    private String titleName;
}
