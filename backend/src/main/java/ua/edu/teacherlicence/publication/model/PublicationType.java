package ua.edu.teacherlicence.publication.model;

public enum PublicationType {
    ARTICLE,              // Наукова стаття (Scopus/WoS/фахове/інше)
    PATENT,               // Патент на винахід
    DECLARATIVE_PATENT,   // Деклараційний патент / патент на корисну модель
    COPYRIGHT,            // Свідоцтво про авторське право
    TEXTBOOK,             // Підручник
    STUDY_GUIDE,          // Навчальний посібник
    MONOGRAPH,            // Монографія
    METHODICAL,           // Навчально-методичне видання (пп.4)
    APPROBATION,          // Апробації, тези, конференції (пп.12)
    POPULAR_SCIENTIFIC,   // Науково-популярне, науково-експертне
    OTHER                 // Інше
}
