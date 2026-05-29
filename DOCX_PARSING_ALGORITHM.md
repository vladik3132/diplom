# DOCX Import Algorithm — DataImportService

## Main Flow

```mermaid
flowchart TD
    START["📄 importFromDocx(InputStream)"] --> OPEN["Open DOCX via Apache POI<br/>XWPFDocument"]
    OPEN --> TABLES["Iterate all XWPFTable"]
    TABLES --> ROWS["For each row (skip header row 0)"]

    ROWS --> CLASSIFY{{"row.cells >= 10?"}}
    CLASSIFY -->|"YES"| DATA["🟩 Data Row<br/>(teacher structured data)"]
    CLASSIFY -->|"NO"| DETAIL["🟧 Detail Row<br/>(achievement narrative text)"]

    DATA --> PHASE2["PHASE 2: parseDataRow()"]
    DETAIL --> PHASE3["PHASE 3: processAchievementText()"]

    PHASE2 --> SAVE_T[("💾 Save Teacher to DB<br/>savedTeachers map")]
    SAVE_T --> ROWS

    PHASE3 --> ROWS

    style START fill:#d0bfff,stroke:#7048e8
    style SAVE_T fill:#b2f2bb,stroke:#2f9e44
    style DATA fill:#d3f9d8,stroke:#2f9e44
    style DETAIL fill:#ffe8cc,stroke:#e8590c
    style CLASSIFY fill:#fff3bf,stroke:#f08c00
```

## Phase 2: Data Row Column Parsing

```mermaid
flowchart TD
    DR["parseDataRow(row)"] --> OFFSET{{"Col[0] = row number?<br/>(regex: ^\\d{1,3}\\.?$)"}}
    OFFSET -->|"YES"| OFF1["offset = 1"]
    OFFSET -->|"NO"| OFF0["offset = 0"]

    OFF1 --> COLS
    OFF0 --> COLS

    COLS["Parse 10 columns with offset"] --> C0 & C1 & C2 & C3 & C4 & C5 & C6 & C7 & C8 & C9

    C0["Col 0: parseTeacherBasicInfo()<br/>━━━━━━━━━━━━━━━━━━━━<br/>• ПРІЗВИЩЕ Ім'я По-батькові<br/>• Рік народження (19XX/20XX)<br/>• Військове звання<br/>• Стаж (X років)"]

    C1["Col 1: Посада<br/>━━━━━━━━━━━━━━━━━━━━<br/>• position = text<br/>• 'сумісни' → PART_TIME<br/>• інше → MAIN"]

    C2["Col 2: parseDisciplines()<br/>━━━━━━━━━━━━━━━━━━━━<br/>• Split: ; \\n або 1. 2.<br/>• find-or-create Discipline<br/>• TeacherDiscipline link"]

    C3["Col 3: parseEducation()<br/>━━━━━━━━━━━━━━━━━━━━<br/>• ВНЗ (повний текст)<br/>• Спеціальність: regex<br/>• Диплом: серія/номер"]

    C4["Col 4: parseScientific()<br/>━━━━━━━━━━━━━━━━━━━━<br/>• Ступінь: к.т.н → Кандидат<br/>• Звання: Атестат → Доцент<br/>• Дисертація: Тема:..."]

    C5["Col 5: parseCareerRecords()<br/>━━━━━━━━━━━━━━━━━━━━<br/>• Split: по рядках<br/>• Дати: DD.MM.YYYY-DD.MM.YYYY<br/>• Організація: ключові слова"]

    C6["Col 6: Бойовий досвід<br/>━━━━━━━━━━━━━━━━━━━━<br/>• не порожнє / не «—»<br/>• combatVeteran = true"]

    C7["Col 7: parseLanguageSkills()<br/>━━━━━━━━━━━━━━━━━━━━<br/>• Мова: english → Англійська<br/>• Рівень: СМР / CEFR / text<br/>• Сертифікат: full text"]

    C8["Col 8: parseQualifications()<br/>━━━━━━━━━━━━━━━━━━━━<br/>• Split: 1. 2. 3.<br/>• Sub-items: а) б) в)<br/>• → parseOneQualification()"]

    C9["Col 9: parseIdentifiers()<br/>━━━━━━━━━━━━━━━━━━━━<br/>• ORCID: 0000-XXXX-XXXX<br/>• Email: user@...<br/>• Scopus/WoS/Scholar ID"]

    C0 & C1 & C3 & C4 & C6 & C9 --> SAVE_PRE["Save Teacher to DB"]
    SAVE_PRE --> C2 & C5 & C7 & C8
    C2 & C5 & C7 & C8 --> SAVE_POST["Save related entities"]

    style DR fill:#d0bfff,stroke:#7048e8
    style OFFSET fill:#fff3bf,stroke:#f08c00
    style SAVE_PRE fill:#b2f2bb,stroke:#2f9e44
    style SAVE_POST fill:#b2f2bb,stroke:#2f9e44
    style C0 fill:#e7f5ff,stroke:#1971c2
    style C1 fill:#e7f5ff,stroke:#1971c2
    style C2 fill:#d3f9d8,stroke:#2f9e44
    style C3 fill:#e7f5ff,stroke:#1971c2
    style C4 fill:#e7f5ff,stroke:#1971c2
    style C5 fill:#d3f9d8,stroke:#2f9e44
    style C6 fill:#e7f5ff,stroke:#1971c2
    style C7 fill:#d3f9d8,stroke:#2f9e44
    style C8 fill:#ffe8cc,stroke:#e8590c
    style C9 fill:#e7f5ff,stroke:#1971c2
```

## Phase 3: Achievement Text Processing

```mermaid
flowchart TD
    P3["Detail Rows processing<br/>(pairs: label + text)"] --> MATCH["matchTeacherByNameLabel()<br/>extract surname → lookup<br/>in savedTeachers map"]

    MATCH --> SPLIT["splitBySections()<br/>Regex: п.38 пп.(1-20)"]

    MATCH --> PREAMBLE["extractPreamble()<br/>Text BEFORE first п.38 пп.X"]
    PREAMBLE --> PREAM_Q{{"Has пп.1<br/>section?"}}
    PREAM_Q -->|"NO"| PREAM_PUB["Create Achievement PP_1<br/>+ parsePublicationEntries()"]
    PREAM_Q -->|"YES"| SKIP["Skip preamble<br/>(пп.1 handles it)"]

    SPLIT --> LOOP["For each section пп.X"]
    LOOP --> ACH["Create Achievement entity<br/>type = AchievementType.fromNumber(X)<br/>extract URL from text"]

    ACH --> SEC_Q{{"пп.1 / пп.3 /<br/>пп.12 ?"}}
    SEC_Q -->|"пп.1"| PUB1["parsePublicationEntries<br/>(text, null)<br/>auto-detect type"]
    SEC_Q -->|"пп.3"| PUB3["parsePublicationEntries<br/>(text, TEXTBOOK)"]
    SEC_Q -->|"пп.12"| PUB12["parsePublicationEntries<br/>(text, CONFERENCE)"]
    SEC_Q -->|"інші"| NOPARSE["Achievement only<br/>(no publication parsing)"]

    PUB1 & PUB3 & PUB12 --> LOOP
    NOPARSE --> LOOP

    style P3 fill:#d0bfff,stroke:#7048e8
    style MATCH fill:#e7f5ff,stroke:#1971c2
    style SPLIT fill:#d0bfff,stroke:#7048e8
    style ACH fill:#d3f9d8,stroke:#2f9e44
    style SEC_Q fill:#fff3bf,stroke:#f08c00
    style PREAM_Q fill:#fff3bf,stroke:#f08c00
    style PUB1 fill:#ffe8cc,stroke:#e8590c
    style PUB3 fill:#ffe8cc,stroke:#e8590c
    style PUB12 fill:#ffe8cc,stroke:#e8590c
    style PREAM_PUB fill:#ffe8cc,stroke:#e8590c
```

## Publication Parsing Pipeline

```mermaid
flowchart TD
    PP["parsePublicationEntries(text, teacher, defaultType)"] --> SPLIT["Split by numbered items<br/>Regex: \\d{1,2}[.)] + whitespace"]

    SPLIT --> CLEAN["For each entry: clean text"]
    CLEAN --> DETECT["Detect subheading type changes<br/>'Статті Scopus:' / 'Фахові видання:'"]

    DETECT --> BASIC["Extract basic fields"]
    BASIC --> TYPE["detectPublicationType()"]
    BASIC --> YEAR["Year: regex (20[12]\\d)"]
    BASIC --> DOI["DOI: 10.XXXX/..."]
    BASIC --> URL["URL: https://..."]
    BASIC --> JOURNAL["extractJournalName()<br/>10 strategies cascade"]

    TYPE --> TYPES["SCOPUS | WOS | FAKHOVE<br/>THESES | CONFERENCE<br/>TEXTBOOK | MONOGRAPH | OTHER"]

    JOURNAL --> J_DETAIL["1. // separator<br/>2. textbook «quotes»<br/>3. city:publisher (К.:)<br/>4. APA English keywords<br/>5. збірник/конференція<br/>6-10. fallbacks"]

    BASIC --> AI_CHECK{{"aiParser<br/>!= null ?"}}

    AI_CHECK -->|"YES"| AI["🤖 AI Batch Parse<br/>Mistral via ChatClient<br/>10 entries per API call<br/>━━━━━━━━━━━━━━<br/>→ title, coauthors,<br/>   pages, volume"]

    AI_CHECK -->|"NO"| REGEX["📝 Regex Fallback<br/>decomposePublicationEntry()<br/>━━━━━━━━━━━━━━<br/>→ title, coauthors,<br/>   pages, volume"]

    AI --> AI_OK{{"AI returned<br/>title?"}}
    AI_OK -->|"YES"| USE_AI["Use AI results"]
    AI_OK -->|"NO"| REGEX

    USE_AI --> SAVE
    REGEX --> SAVE

    SAVE[("💾 Save Publication<br/>publicationRepository.save()")]

    style PP fill:#d0bfff,stroke:#7048e8
    style AI_CHECK fill:#fff3bf,stroke:#f08c00
    style AI_OK fill:#fff3bf,stroke:#f08c00
    style AI fill:#d0bfff,stroke:#7048e8
    style REGEX fill:#ffe3e3,stroke:#e03131
    style SAVE fill:#b2f2bb,stroke:#2f9e44
    style TYPES fill:#f1f3f5,stroke:#868e96
    style J_DETAIL fill:#f1f3f5,stroke:#868e96
```

## Qualification Parsing Pipeline

```mermaid
flowchart TD
    QP["parseQualifications(text, teacher)"] --> QSPLIT["Split by numbered items: 1. 2. 3.<br/>Regex: \\d{1,2}[.)] followed by letter<br/>(avoids splitting dates like 24.10.2025)"]

    QSPLIT --> QLOOP["For each entry"]
    QLOOP --> QSUB{{"hasSubItems()?<br/>а) Вид документа<br/>б) Тема<br/>в) Сертифікат"}}

    QSUB -->|"YES"| QMULTI["Sub-items mode:<br/>Line 1 = organization<br/>Split: а) б) в) г)...<br/>Each → parseOne(sub, parentOrg)"]

    QSUB -->|"NO"| QSINGLE["Single entry:<br/>parseOneQualification(text, null)"]

    QMULTI --> PARSE1
    QSINGLE --> PARSE1

    PARSE1["parseOneQualification()"] --> ORG

    subgraph ORG ["Step 1: Organization Detection"]
        O1["parentOrg passed?<br/>→ use directly"]
        O2["First line has keywords?<br/>(інститут, університет, центр,<br/>academy, school, department...)"]
        O3["Step 1b: Org line has quotes?<br/>Extract title from quotes<br/>Clean org: remove 'Certificate of',<br/>'for participating...'"]
    end

    ORG --> TITLE

    subgraph TITLE ["Step 2: Title Extraction (cascade)"]
        T1["2a. Тема: explicit marker"]
        T2["2b. курсу «Назва курсу»"]
        T3["2c. Generic «quotes»"]
        T4["2d. Certificate of Completion ORG. COURSE"]
        T5["2e. Certificate of Type + details"]
        T6["2f. Fallback: first line cleaned"]
        T7["2g. Cleanup: remove dates, URLs,<br/>'за проходження', hours info"]
        T1 --> T2 --> T3 --> T4 --> T5 --> T6 --> T7
    end

    TITLE --> DATES

    subgraph DATES ["Steps 3-8: Other Fields"]
        D1["Dates: з DD.MM.YYYY до DD.MM.YYYY"]
        D2["Dates: DD.MM.YY – DD.MM.YY"]
        D3["Dates: DD місяця - DD місяця YYYY року"]
        D4["Дата видачі → endDate fallback"]
        D5["Hours: (\\d+) годин"]
        D6["Credits: (\\d+) кредит"]
        D7["Certificate #: Сертифікат №"]
        D8["URL: https://..."]
    end

    DATES --> QSAVE[("💾 Save Qualification")]

    style QP fill:#d0bfff,stroke:#7048e8
    style QSUB fill:#fff3bf,stroke:#f08c00
    style QMULTI fill:#d3f9d8,stroke:#2f9e44
    style QSINGLE fill:#ffe8cc,stroke:#e8590c
    style PARSE1 fill:#e7f5ff,stroke:#1971c2
    style QSAVE fill:#b2f2bb,stroke:#2f9e44
    style ORG fill:#f1f3f5,stroke:#868e96
    style TITLE fill:#f1f3f5,stroke:#868e96
    style DATES fill:#f1f3f5,stroke:#868e96
```

## Scientific Qualification Parsing (Col 4)

```mermaid
flowchart TD
    SCI["parseScientific(teacher, col4)"] --> DEG & TTL & SPEC & DISS & DIP

    DEG["Academic Degree Detection"]
    DEG --> DEG1["Full form: Кандидат технічних наук"]
    DEG --> DEG2["Abbreviation: к.т.н. → expand<br/>via SCIENCE_ABBREV map (24 entries)<br/>т→технічних, в→військових..."]
    DEG --> DEG3["Fallback: д-р / канд. / PhD"]

    TTL["Academic Title Detection"]
    TTL --> TTL1["Explicit: Вчене звання: Доцент"]
    TTL --> TTL2["Attestat code: ДЦ→Доцент,<br/>ПР→Професор, СД→Ст.дослідник"]
    TTL --> TTL3["Fallback: text search"]

    SPEC["Scientific Specialty<br/>XX.XX.XX — Назва"]
    DISS["Dissertation Topic<br/>Тема: / на тему:"]
    DIP["Diploma / Attestat<br/>Диплом ДК №058623<br/>Атестат ..."]

    style SCI fill:#d0bfff,stroke:#7048e8
    style DEG fill:#e7f5ff,stroke:#1971c2
    style TTL fill:#e7f5ff,stroke:#1971c2
```

## Data Flow Summary

```mermaid
flowchart LR
    DOCX["📄 DOCX File<br/>(кадрове забезпечення)"] --> IMPORT["DataImportService"]

    IMPORT --> T[("👤 Teachers")]
    IMPORT --> P[("📚 Publications")]
    IMPORT --> A[("🏆 Achievements")]
    IMPORT --> Q[("📋 Qualifications")]
    IMPORT --> D[("📖 Disciplines")]
    IMPORT --> C[("💼 Career Records")]
    IMPORT --> L[("🌐 Language Skills")]

    AI["🤖 Mistral AI<br/>(optional)"] -.->|"batch parse<br/>title/coauthors"| P

    style DOCX fill:#d0bfff,stroke:#7048e8
    style IMPORT fill:#e7f5ff,stroke:#1971c2
    style AI fill:#fff3bf,stroke:#f08c00
    style T fill:#b2f2bb,stroke:#2f9e44
    style P fill:#b2f2bb,stroke:#2f9e44
    style A fill:#b2f2bb,stroke:#2f9e44
    style Q fill:#b2f2bb,stroke:#2f9e44
    style D fill:#b2f2bb,stroke:#2f9e44
    style C fill:#b2f2bb,stroke:#2f9e44
    style L fill:#b2f2bb,stroke:#2f9e44
```
