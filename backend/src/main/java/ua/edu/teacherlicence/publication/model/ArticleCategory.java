package ua.edu.teacherlicence.publication.model;

/**
 * Підтип/категорія для PublicationType.ARTICLE.
 * Визначається при верифікації журналу в реєстрі фахових видань або Scopus.
 */
public enum ArticleCategory {
    SCOPUS,       // Індексується в Scopus
    WOS,          // Індексується в Web of Science
    CATEGORY_A,   // Фахове видання категорії А
    CATEGORY_B    // Фахове видання категорії Б
}
