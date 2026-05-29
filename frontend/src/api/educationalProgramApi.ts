import api from './axiosInstance';
import type { EducationalProgram, ProgramStaffStats, DisciplineStaffingDto } from '@/types/educationalProgram';

export const getEducationalPrograms = () =>
  api.get<EducationalProgram[]>('/educational-programs').then(r => r.data);

export const getEducationalProgram = (id: number) =>
  api.get<EducationalProgram>(`/educational-programs/${id}`).then(r => r.data);

export const getEducationalProgramsByDepartment = (departmentId: number) =>
  api.get<EducationalProgram[]>(`/educational-programs/by-department/${departmentId}`).then(r => r.data);

export const createEducationalProgram = (data: Partial<EducationalProgram> & { departmentId?: number }) =>
  api.post<EducationalProgram>('/educational-programs', data).then(r => r.data);

export const updateEducationalProgram = (id: number, data: Partial<EducationalProgram> & { departmentId?: number }) =>
  api.put<EducationalProgram>(`/educational-programs/${id}`, data).then(r => r.data);

export const deleteEducationalProgram = (id: number) =>
  api.delete(`/educational-programs/${id}`);

export const getProgramStaffStats = (id: number) =>
  api.get<ProgramStaffStats>(`/educational-programs/${id}/staff-stats`).then(r => r.data);

export const getDisciplineStaffing = (id: number) =>
  api.get<Record<number, DisciplineStaffingDto>>(`/educational-programs/${id}/discipline-staffing`).then(r => r.data);
