export interface GanttEvent {
  id: number;
  teacher?: { id: number; lastName: string; firstName: string };
  title: string;
  eventType?: string;
  startDate: string;
  endDate: string;
  status?: string;
  academicYear?: string;
  semester?: number;
  color?: string;
  notes?: string;
}
