import api from './axiosInstance';
import { GanttEvent } from '@/types/gantt';

export const getGanttEvents = (teacherId?: number, academicYear?: string) =>
  api.get<GanttEvent[]>('/gantt', { params: { teacherId, academicYear } }).then(r => r.data);

export const getGanttEvent = (id: number) =>
  api.get<GanttEvent>(`/gantt/${id}`).then(r => r.data);

export const createGanttEvent = (data: Partial<GanttEvent>) =>
  api.post<GanttEvent>('/gantt', data).then(r => r.data);

export const updateGanttEvent = (id: number, data: Partial<GanttEvent>) =>
  api.put<GanttEvent>(`/gantt/${id}`, data).then(r => r.data);

export const deleteGanttEvent = (id: number) =>
  api.delete(`/gantt/${id}`);
