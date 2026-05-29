// ── Спільний інтерфейс для всіх ppData сутностей ──

export interface BaseAuditEntity {
  id: number;
  teacher?: { id: number; lastName: string; firstName: string; patronymic?: string };
  documentUrl?: string;
  createdAt?: string;
  updatedAt?: string;
  createdBy?: string;
  updatedBy?: string;
}

// ── Енуми ──

export type DegreeType = 'PHD' | 'DOCTOR_OF_SCIENCE';
export type AttestationRole = 'OPPONENT' | 'REVIEWER' | 'CHAIR' | 'COUNCIL_MEMBER';
export type EditorialRole = 'THEME_LEADER' | 'CHIEF_EDITOR' | 'BOARD_MEMBER' | 'REVIEWER';
export type ExpertCouncilType = 'MON' | 'NAZYAVO' | 'ACCREDITATION';
export type InternationalProgram = 'ERASMUS' | 'HORIZON' | 'NATO' | 'GRANT' | 'OTHER';
export type OlympiadLevel = 'STUDENT' | 'SCHOOL';
export type OlympiadRole = 'SUPERVISOR' | 'JURY' | 'COMMITTEE' | 'GROUP_LEADER' | 'COACH' | 'CURATOR';
export type Pp14ActivityType = 'OLYMPIAD' | 'SCIENTIFIC_COMPETITION' | 'COMPETITION' | 'SCIENTIFIC_GROUP' | 'SPORTS' | 'ARTS' | 'OTHER';
export type CompetitionScope = 'INTERNATIONAL' | 'NATIONAL';
export type MissionType = 'UN_PEACEKEEPING' | 'NATO_EXERCISE';

// ── пп.6 Наукове керівництво ──

export interface ScientificSupervision extends BaseAuditEntity {
  studentName: string;
  topic?: string;
  defenseDate?: string;
  degreeType?: DegreeType;
  diplomaNumber?: string;
}

// ── пп.7 Атестаційна діяльність ──

export interface AttestationActivity extends BaseAuditEntity {
  role: AttestationRole;
  councilName?: string;
  studentName?: string;
  /** Дата захисту (для разових ролей: OPPONENT/REVIEWER/CHAIR). */
  defenseDate?: string;
  /** Початок членства (тільки для COUNCIL_MEMBER — постійна спецрада). */
  dateFrom?: string;
  /** Кінець членства (тільки для COUNCIL_MEMBER). */
  dateTo?: string;
}

// ── пп.8 Редакційно-видавнича діяльність ──

export interface EditorialActivity extends BaseAuditEntity {
  role: EditorialRole;
  journalOrProjectName?: string;
  dateFrom?: string;
  dateTo?: string;
}

// ── пп.9 Експертні ради ──

export interface ExpertCouncil extends BaseAuditEntity {
  councilName: string;
  type?: ExpertCouncilType;
  role?: string;
  dateFrom?: string;
  dateTo?: string;
  orderNumber?: string;
}

// ── пп.10 Міжнародні проєкти ──

export interface InternationalProject extends BaseAuditEntity {
  projectName: string;
  program?: InternationalProgram;
  role?: string;
  dateFrom?: string;
  dateTo?: string;
}

// ── пп.11 Наукове консультування ──

export interface ScientificConsulting extends BaseAuditEntity {
  organizationName: string;
  contractNumber?: string;
  dateFrom?: string;
  dateTo?: string;
  yearsCount?: number;
}

// ── пп.13 Іноземна мова у навчанні ──

export interface ForeignLanguageTeaching extends BaseAuditEntity {
  disciplineName: string;
  language?: string;
  hours?: number;
  academicYear?: string;
  semester?: number;
}

// ── пп.14+15 Олімпіади / гуртки / конкурси ──

export interface OlympiadGuidance extends BaseAuditEntity {
  level?: OlympiadLevel;
  activityType?: Pp14ActivityType;
  competitionScope?: CompetitionScope;
  olympiadName?: string;
  studentName?: string;
  result?: string;
  year?: number;
  role?: OlympiadRole;
  competitionName?: string;
  departmentName?: string;
  participantCount?: number;
  academicYear?: string;
  orderNumber?: string;
  orderDate?: string;
  description?: string;
}

// ── пп.17+18 Миротворчі/НАТО ──

export interface MilitaryMission extends BaseAuditEntity {
  missionType: MissionType;
  missionName?: string;
  country?: string;
  dateFrom?: string;
  dateTo?: string;
}

// ── пп.19 Професійні об'єднання ──

export interface ProfessionalAssociation extends BaseAuditEntity {
  organizationName: string;
  role?: string;
  dateFrom?: string;
  dateTo?: string;
  certificateNumber?: string;
}

// ── пп.20 Практичний досвід ──

export interface PracticalExperience extends BaseAuditEntity {
  organizationName: string;
  position?: string;
  dateFrom?: string;
  dateTo?: string;
  yearsCount?: number;
}

// ── Мітки для енумів ──

export const DEGREE_TYPE_LABELS: Record<DegreeType, string> = {
  PHD: 'Кандидат наук / PhD',
  DOCTOR_OF_SCIENCE: 'Доктор наук',
};

export const ATTESTATION_ROLE_LABELS: Record<AttestationRole, string> = {
  OPPONENT: 'Опонент',
  REVIEWER: 'Рецензент',
  CHAIR: 'Голова',
  COUNCIL_MEMBER: 'Член постійної спецради',
};

export const EDITORIAL_ROLE_LABELS: Record<EditorialRole, string> = {
  THEME_LEADER: 'Керівник теми',
  CHIEF_EDITOR: 'Головний редактор',
  BOARD_MEMBER: 'Член редколегії',
  REVIEWER: 'Рецензент',
};

export const EXPERT_COUNCIL_TYPE_LABELS: Record<ExpertCouncilType, string> = {
  MON: 'МОН',
  NAZYAVO: 'НАЗЯВО',
  ACCREDITATION: 'Акредитаційна',
};

export const INTERNATIONAL_PROGRAM_LABELS: Record<InternationalProgram, string> = {
  ERASMUS: 'Erasmus+',
  HORIZON: 'Horizon Europe',
  NATO: 'NATO',
  GRANT: 'Грант',
  OTHER: 'Інше',
};

export const OLYMPIAD_LEVEL_LABELS: Record<OlympiadLevel, string> = {
  STUDENT: 'Студентська',
  SCHOOL: 'Шкільна/МАН',
};

export const OLYMPIAD_ROLE_LABELS: Record<OlympiadRole, string> = {
  SUPERVISOR: 'Керівник',
  JURY: 'Журі',
  COMMITTEE: 'Оргкомітет',
  GROUP_LEADER: 'Керівник гуртка',
  COACH: 'Тренер',
  CURATOR: 'Куратор',
};

export const PP14_ACTIVITY_TYPE_LABELS: Record<Pp14ActivityType, string> = {
  OLYMPIAD: 'Олімпіада',
  SCIENTIFIC_COMPETITION: 'Конкурс наукових робіт',
  COMPETITION: 'Конкурс (хакатон тощо)',
  SCIENTIFIC_GROUP: 'Науковий гурток',
  SPORTS: 'Спортивні змагання',
  ARTS: 'Мистецький конкурс',
  OTHER: 'Інше',
};

export const COMPETITION_SCOPE_LABELS: Record<CompetitionScope, string> = {
  INTERNATIONAL: 'Міжнародний',
  NATIONAL: 'Всеукраїнський',
};

export const MISSION_TYPE_LABELS: Record<MissionType, string> = {
  UN_PEACEKEEPING: 'Миротворча операція ООН',
  NATO_EXERCISE: 'Навчання НАТО',
};

// ── Конфігурація вкладок ppData ──

export interface PpTabConfig {
  key: string;
  label: string;
  ppNumber: string;
  entityPath: string;
}

export const PP_TABS: PpTabConfig[] = [
  { key: 'scientific-supervision', label: 'пп.6 Наукове керівництво', ppNumber: '6', entityPath: 'scientific-supervision' },
  { key: 'attestation-activity', label: 'пп.7 Атестація', ppNumber: '7', entityPath: 'attestation-activity' },
  { key: 'editorial-activity', label: 'пп.8 Редакційна', ppNumber: '8', entityPath: 'editorial-activity' },
  { key: 'expert-council', label: 'пп.9 Експертна рада', ppNumber: '9', entityPath: 'expert-council' },
  { key: 'international-project', label: 'пп.10 Міжнародні', ppNumber: '10', entityPath: 'international-project' },
  { key: 'scientific-consulting', label: 'пп.11 Консультування', ppNumber: '11', entityPath: 'scientific-consulting' },
  { key: 'foreign-language-teaching', label: 'пп.13 Іноз. мова', ppNumber: '13', entityPath: 'foreign-language-teaching' },
  { key: 'olympiad-guidance', label: 'пп.14-15 Олімпіади/гуртки', ppNumber: '14-15', entityPath: 'olympiad-guidance' },
  { key: 'military-mission', label: 'пп.17-18 Військові місії', ppNumber: '17-18', entityPath: 'military-mission' },
  { key: 'professional-association', label: 'пп.19 Проф. об\'єднання', ppNumber: '19', entityPath: 'professional-association' },
  // пп.20 Практичний досвід — визначається автоматично з послужного списку (CareerRecord)
];
