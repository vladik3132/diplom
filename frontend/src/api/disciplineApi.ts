import api from './axiosInstance';
import { Discipline, TeacherDiscipline, DisciplineDocument } from '@/types/discipline';

export const getDisciplines = (params?: { departmentId?: number; programId?: number }) =>
  api.get<Discipline[]>('/disciplines', { params }).then(r => r.data);

/** Імпорт дисциплін з Excel навчального плану (НП) */
export const importDisciplinesExcel = (file: File, enrollmentYear: number, educationLevel?: string, educationForm?: string) => {
  const formData = new FormData();
  formData.append('file', file);
  formData.append('enrollmentYear', String(enrollmentYear));
  if (educationLevel) formData.append('educationLevel', educationLevel);
  if (educationForm) formData.append('educationForm', educationForm);
  return api.post<{ imported: number }>('/disciplines/import', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  }).then(r => r.data);
};

export const getDiscipline = (id: number) =>
  api.get<Discipline>(`/disciplines/${id}`).then(r => r.data);

export const createDiscipline = (data: Partial<Discipline>) =>
  api.post<Discipline>('/disciplines', data).then(r => r.data);

export const updateDiscipline = (id: number, data: Partial<Discipline>) =>
  api.put<Discipline>(`/disciplines/${id}`, data).then(r => r.data);

export const deleteDiscipline = (id: number) =>
  api.delete(`/disciplines/${id}`);

export const getTeacherDisciplines = (teacherId: number) =>
  api.get<TeacherDiscipline[]>(`/disciplines/teacher/${teacherId}`).then(r => r.data);

export const getDisciplineTeachers = (disciplineId: number) =>
  api.get<TeacherDiscipline[]>(`/disciplines/${disciplineId}/teachers`).then(r => r.data);

export const assignTeacherDiscipline = (data: Partial<TeacherDiscipline>) =>
  api.post<TeacherDiscipline>('/disciplines/assign', data).then(r => r.data);

export const removeTeacherDiscipline = (id: number) =>
  api.delete(`/disciplines/assign/${id}`);

export const getDocumentsByDiscipline = (disciplineId: number) =>
  api.get<DisciplineDocument[]>(`/disciplines/${disciplineId}/documents`).then(r => r.data);

export const getDocumentsByTeacher = (teacherId: number) =>
  api.get<DisciplineDocument[]>(`/disciplines/documents/teacher/${teacherId}`).then(r => r.data);

export const createDocument = (data: Partial<DisciplineDocument>) =>
  api.post<DisciplineDocument>('/disciplines/documents', data).then(r => r.data);

export const updateDocument = (id: number, data: Partial<DisciplineDocument>) =>
  api.put<DisciplineDocument>(`/disciplines/documents/${id}`, data).then(r => r.data);

export const deleteDocument = (id: number) =>
  api.delete(`/disciplines/documents/${id}`);
