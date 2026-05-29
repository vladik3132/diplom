export type QualificationCategory = 'GENERAL' | 'MILITARY_COURSE';

export type MilitaryCourseLevel = 'L2' | 'L3' | 'L4';

export interface QualificationImprovement {
  id: number;
  teacher?: { id: number; lastName: string; firstName: string };
  title: string;
  organization?: string;
  startDate?: string;
  endDate?: string;
  hours?: number;
  credits?: number;
  certificateNumber?: string;
  certificateDate?: string;  // ISO date
  certificateUrl?: string;
  country?: string;
  category?: QualificationCategory;
  militaryCourseLevel?: MilitaryCourseLevel;
  createdAt?: string;
}

export const QUALIFICATION_CATEGORY_LABELS: Record<QualificationCategory, string> = {
  GENERAL: 'Загальне',
  MILITARY_COURSE: 'Курси ВО (L2 L3 L4)',
};

export const MILITARY_COURSE_LEVEL_LABELS: Record<MilitaryCourseLevel, string> = {
  L2: 'L2 (5 балів)',
  L3: 'L3 (10 балів)',
  L4: 'L4 (15 балів)',
};
