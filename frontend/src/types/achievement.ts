export type ComplianceStatus = 'COMPLIANT' | 'WARNING' | 'NON_COMPLIANT' | 'EXEMPT';

export interface Achievement {
  id: number;
  teacherId: number;
  achievementType: string;
  title: string;
  description?: string;
  dateAchieved?: string;
  documentUrl?: string;
  verified: boolean;
  verifiedBy?: string;
  notes?: string;
  createdAt?: string;
}

export interface ComplianceReport {
  teacherId: number;
  teacherName: string;
  status: ComplianceStatus;
  exemptionReason?: string;
  achievementCount: number;
  uniqueTypeCount: number;
  achievementTypes: string[];
  missingInfo?: string[];
  publicationsCount: number;
  diplomaMatchesDepartment: boolean;
  degreeMatchesDepartment: boolean;
  qualificationMatchesDepartment: boolean;
  /** Хоча б одне вчене звання відповідає напряму кафедри (AI). */
  titleMatchesDepartment: boolean;
  relevantPublicationsCount: number;
  // ── Live-збагачення на бекенді для UI списку викладачів кафедри ──
  /** Ефективна посада (зі staff_positions через TeacherPositionService). */
  position?: string;
  /** Військове звання людини (Teacher.militaryRank). */
  militaryRank?: string;
  /** 'MAIN' / 'PART_TIME' — для тегу «Сумісник». */
  employmentType?: string;
  /** Primary staff_position bootstrapped — UI показує ⚠️. */
  bootstrappedPosition?: boolean;
}

export const ACHIEVEMENT_TYPE_LABELS: Record<string, string> = {
  PP_1_PUBLICATIONS: 'пп.1 Наукові публікації (фахові/Scopus/WoS)',
  PP_2_PATENTS: 'пп.2 Патенти/авторське право',
  PP_3_TEXTBOOK: 'пп.3 Підручники/посібники/монографії',
  PP_4_METHODICAL: 'пп.4 Навчально-методичні праці',
  PP_5_DISSERTATION: 'пп.5 Захист дисертації',
  PP_6_SUPERVISION: 'пп.6 Наукове керівництво',
  PP_7_ATTESTATION: 'пп.7 Атестація (опонент/спецрада)',
  PP_8_EDITORIAL: 'пп.8 Керівник теми/редколегія/експерт видання',
  PP_9_EXPERT_COUNCIL: 'пп.9 Експертна рада МОН/НАЗЯВО',
  PP_10_INTERNATIONAL: 'пп.10 Міжнародні проєкти/експертиза',
  PP_11_CONSULTING: 'пп.11 Наукове консультування (≥3 роки)',
  PP_12_APPROBATION: 'пп.12 Апробаційні/науково-популярні публікації',
  PP_13_FOREIGN_LANGUAGE: 'пп.13 Заняття іноземною мовою (≥50 год)',
  PP_14_STUDENT_OLYMPIAD: 'пп.14 Олімпіади/конкурси студентів',
  PP_15_SCHOOL_OLYMPIAD: 'пп.15 Олімпіади/конкурси школярів/МАН',
  PP_16_COMBAT_VETERAN: 'пп.16 Учасник бойових дій',
  PP_17_UN_PEACEKEEPING: 'пп.17 Миротворчі операції ООН',
  PP_18_NATO_EXERCISES: 'пп.18 Навчання НАТО',
  PP_19_PROFESSIONAL_ASSOCIATIONS: 'пп.19 Професійні об\'єднання',
  PP_20_PRACTICAL_EXPERIENCE: 'пп.20 Практичний досвід (≥5 років)',
};

// --- AI Validation types ---

export interface AchievementValidationSuggestion {
  achievementId: number;
  teacherName: string;
  achievementType: string;
  ppNumber: number;
  currentCount: number;
  requiredCount: number;
  progress: number;
  fulfilled: boolean;
  reasoning: string;
  descriptionPreview: string;
}

export interface AchievementValidationResponse {
  totalValidated: number;
  fulfilledCount: number;
  notFulfilledCount: number;
  sessionId: string;
  suggestions: AchievementValidationSuggestion[];
}

export interface BatchReclassifyItem {
  achievementId: number;
  newType: string;
}

export interface AchievementProgress {
  achievementId: number;
  achievementType: string;
  ppNumber: number;
  currentCount: number;
  requiredCount: number;
  progress: number;
  fulfilled: boolean;
  label: string;
  /** Детальне пояснення підрахунку: правила, перелік врахованого/відкинутого, підсумок. Multiline. */
  reasoning?: string;
}

export interface ValidationSessionInfo {
  sessionId: string;
  validatedAt: string;
  totalCount: number;
  fulfilledCount: number;
  notFulfilledCount: number;
}
