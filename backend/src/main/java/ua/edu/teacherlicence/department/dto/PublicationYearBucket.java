package ua.edu.teacherlicence.department.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Накопичувальний bucket публікацій кафедри за один рік
 * для stacked-bar графіка "Публікації кафедри за 5 років".
 *
 * <p>Враховуються лише публікації типу ARTICLE з заповненою категорією,
 * незалежно від інших чинників (зокрема, не фільтрується свіжість —
 * cutoff забезпечується вибором років бакета).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicationYearBucket {
    private int year;
    private int scopus;
    private int wos;
    private int categoryA;
    private int categoryB;
}
