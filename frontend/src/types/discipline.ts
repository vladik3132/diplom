export interface Discipline {
  id: number;
  name: string;
  code?: string;
  department?: { id: number; name: string; number?: string };
  educationalProgram?: { id: number; name: string; shortCode?: string; specialty?: string };
  credits?: number;
  totalHours?: number;
  auditoryHours?: number;
  hoursLecture?: number;
  hoursGroup?: number;
  hoursPractical?: number;
  hoursLab?: number;
  hoursSelfStudy?: number;
  examSemesters?: string;
  creditSemesters?: string;
  hoursBySemester?: string;   // JSON string
  creditsBySemester?: string; // JSON string
  controlTypes?: string;      // JSON string
}

export interface TeacherDiscipline {
  id: number;
  teacher?: { id: number; lastName: string; firstName: string };
  discipline?: Discipline;
  academicYear?: string;
  semester?: number;
}

export interface DisciplineDocument {
  id: number;
  discipline?: Discipline;
  teacher?: { id: number; lastName: string; firstName: string };
  type?: string;
  status: 'DRAFT' | 'REVIEW' | 'APPROVED';
  deadline?: string;
  fileUrl?: string;
  notes?: string;
  updatedAt?: string;
}

export const DOCUMENT_STATUS_LABELS: Record<string, string> = {
  DRAFT: 'Чернетка',
  REVIEW: 'На перевірці',
  APPROVED: 'Затверджено',
};
