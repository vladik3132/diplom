export type PublicationType =
  | 'ARTICLE'
  | 'PATENT'
  | 'DECLARATIVE_PATENT'
  | 'COPYRIGHT'
  | 'TEXTBOOK'
  | 'STUDY_GUIDE'
  | 'MONOGRAPH'
  | 'METHODICAL'
  | 'APPROBATION'
  | 'POPULAR_SCIENTIFIC'
  | 'OTHER';

export type MethodicalSubtype = 'PRACTICUM' | 'METHODICAL_GUIDELINES' | 'E_COURSE' | 'LECTURE_NOTES' | 'WORK_PROGRAM';
export type ApprobationSubtype = 'SCOPUS_WOS' | 'INTERNATIONAL' | 'DOMESTIC';

export type ArticleCategory = 'SCOPUS' | 'WOS' | 'CATEGORY_A' | 'CATEGORY_B';

export type PublicationStatus = 'NOT_VALIDATED' | 'AI_VALIDATED' | 'NEEDS_ATTENTION' | 'HEAD_VALIDATED' | 'OUTDATED';

export type AchievementType =
  | 'PP_1_PUBLICATIONS'
  | 'PP_2_PATENTS'
  | 'PP_3_TEXTBOOK'
  | 'PP_4_METHODICAL'
  | 'PP_12_APPROBATION';

export interface Publication {
  id: number;
  teacher?: { id: number; lastName: string; firstName: string; patronymic?: string };
  title: string;
  type: PublicationType;
  articleCategory?: ArticleCategory;
  status?: PublicationStatus;
  ppType?: AchievementType;
  journalName?: string;
  year?: number;
  /** ISO date YYYY-MM-DD. При парсингу з тільки роком виставляється YYYY-01-01. */
  publicationDate?: string;
  volume?: string;
  pages?: string;
  doi?: string;
  url?: string;
  authors?: string;
  // ДСТУ 8302:2015
  dstuCitation?: string;
  publisher?: string;
  city?: string;
  totalPages?: number;
  isbn?: string;
  issn?: string;
  conferenceInfo?: string;
  authorSheetCount?: number;
  // Відповідність напряму кафедри
  fieldRelevant?: boolean | null;
  // Підтипи для рейтингування
  methodicalSubtype?: MethodicalSubtype;
  approbationSubtype?: ApprobationSubtype;
  // Трекінг
  rawText?: string;
  sourceSection?: string;
  documentUrl?: string;
  // Аудит
  createdAt?: string;
  updatedAt?: string;
  createdBy?: string;
  updatedBy?: string;
}

export const PUBLICATION_TYPE_LABELS: Record<PublicationType, string> = {
  ARTICLE: 'Наукова стаття',
  PATENT: 'Патент',
  DECLARATIVE_PATENT: 'Деклараційний патент',
  COPYRIGHT: 'Авторське свідоцтво',
  TEXTBOOK: 'Підручник',
  STUDY_GUIDE: 'Навч. посібник',
  MONOGRAPH: 'Монографія',
  METHODICAL: 'Навч.-методичне',
  APPROBATION: 'Апробації, інші публікації',
  POPULAR_SCIENTIFIC: 'Науково-популярне',
  OTHER: 'Інше',
};

export const ARTICLE_CATEGORY_LABELS: Record<ArticleCategory, string> = {
  SCOPUS: 'Scopus',
  WOS: 'Web of Science',
  CATEGORY_A: 'Категорія А',
  CATEGORY_B: 'Категорія Б',
};

export const METHODICAL_SUBTYPE_LABELS: Record<MethodicalSubtype, string> = {
  PRACTICUM: 'Практикум',
  METHODICAL_GUIDELINES: 'Навч.-методичні вказівки',
  E_COURSE: 'Електронний курс',
  LECTURE_NOTES: 'Конспект лекцій',
  WORK_PROGRAM: 'Робоча програма (РПНД)',
};

export const APPROBATION_SUBTYPE_LABELS: Record<ApprobationSubtype, string> = {
  SCOPUS_WOS: 'Scopus / Web of Science',
  INTERNATIONAL: 'Міжнародний журнал',
  DOMESTIC: 'Вітчизняний журнал',
};

export const PUBLICATION_STATUS_LABELS: Record<PublicationStatus, string> = {
  NOT_VALIDATED: 'Не перевірено',
  AI_VALIDATED: 'Перевірено ШІ',
  NEEDS_ATTENTION: 'Потребує уваги',
  HEAD_VALIDATED: 'Затверджено',
  OUTDATED: 'Застаріло',
};

export const PP_TYPE_LABELS: Record<AchievementType, string> = {
  PP_1_PUBLICATIONS: 'пп.1',
  PP_2_PATENTS: 'пп.2',
  PP_3_TEXTBOOK: 'пп.3',
  PP_4_METHODICAL: 'пп.4',
  PP_12_APPROBATION: 'пп.12',
};
