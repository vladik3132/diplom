package ua.edu.teacherlicence.department.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Деталізація відповідності викладача п.37 Постанови КМУ №1187
 * для tooltip на сторінці кафедри.
 *
 * <p>Блок А (хоча б один з 4 пунктів):
 * <ul>
 *   <li>А1 — диплом за напрямом кафедри</li>
 *   <li>А2 — науковий ступінь за напрямом кафедри</li>
 *   <li>А3 — пп.20 практичний досвід за фахом ≥ 5 років</li>
 *   <li>А4 — пп.6 наукове керівництво дисертацією за профілем кафедри</li>
 * </ul>
 *
 * <p>Блок Б — пп.1 ≥ 5 публікацій (Scopus / WoS / Категорія А / Б).
 *
 * <p>п.37 виконується якщо: <b>(А1 ∨ А2 ∨ А3 ∨ А4) ∧ Б</b>.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Point37TeacherBrief {
    private Long teacherId;
    /** ПІБ у короткій формі — "Шевченко О.І." */
    private String fullName;
    private boolean a1Diploma;
    private boolean a2Degree;
    private boolean a3Practical;
    private boolean a4Supervision;
    private boolean blockB;
    /** Підсумок: (A1||A2||A3||A4) && B */
    private boolean point37Compliant;
}
