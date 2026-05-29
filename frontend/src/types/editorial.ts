export interface EditorialPlan {
  id: number;
  department?: { id: number; name: string };
  academicYear?: string;
  title: string;
  fileUrl?: string;
  uploadedBy?: number;
  createdAt?: string;
}

export interface EditorialPlanItem {
  id: number;
  plan?: EditorialPlan;
  teacher?: { id: number; lastName: string; firstName: string };
  title: string;
  type?: string;
  plannedDate?: string;
  actualDate?: string;
  status: 'PLANNED' | 'IN_PROGRESS' | 'COMPLETED' | 'OVERDUE';
}

export const EDITORIAL_STATUS_LABELS: Record<string, string> = {
  PLANNED: 'Заплановано',
  IN_PROGRESS: 'В процесі',
  COMPLETED: 'Виконано',
  OVERDUE: 'Прострочено',
};
