// ── "Інша діяльність" entities ──

export interface OpenLesson {
  id?: number;
  teacherId?: number;
  topic?: string;
  date?: string;
  hostDepartment?: string;
  lessonType?: string;
  orderNumber?: string;
  orderDate?: string;
  notes?: string;
  documentUrl?: string;
}

export interface MethodologicalExperiment {
  id?: number;
  teacherId?: number;
  title?: string;
  description?: string;
  date?: string;
  orderNumber?: string;
  orderDate?: string;
  notes?: string;
  documentUrl?: string;
}

export interface AcademicMobility {
  id?: number;
  teacherId?: number;
  programName?: string;
  institution?: string;
  country?: string;
  dateFrom?: string;
  dateTo?: string;
  description?: string;
  notes?: string;
  documentUrl?: string;
}

/**
 * Міжнародне стажування. Окрема активність на вкладці "Інші досягнення".
 * Рейтинговий критерій: FOREIGN_INTERNSHIP (10 балів).
 * Раніше нараховувалось з QualificationImprovement за полем country,
 * але курси ПК — це інша активність. Тепер окрема сутність.
 */
export interface ForeignInternship {
  id?: number;
  teacherId?: number;
  programName?: string;
  institution?: string;
  country?: string;
  dateFrom?: string;
  dateTo?: string;
  description?: string;
  notes?: string;
  documentUrl?: string;
}

// ── Робоча група ОПП ──

export type WorkingGroupRole = 'CHAIR' | 'MEMBER';

export interface ProgramWorkingGroup {
  id?: number;
  programId?: number;
  teacherId?: number;
  teacherName?: string;
  role?: WorkingGroupRole;
  orderNumber?: string;
  orderDate?: string;
}

export const WORKING_GROUP_ROLE_LABELS: Record<WorkingGroupRole, string> = {
  CHAIR: 'Голова',
  MEMBER: 'Член',
};

export const LESSON_TYPE_OPTIONS = [
  { value: 'Показове', label: 'Показове' },
  { value: 'Відкрите', label: 'Відкрите' },
  { value: 'Міжкафедральне', label: 'Міжкафедральне' },
];
