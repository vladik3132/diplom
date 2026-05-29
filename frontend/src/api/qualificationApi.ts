import api from './axiosInstance';
import { QualificationImprovement } from '@/types/qualification';

export const getQualifications = (teacherId?: number) =>
  api.get<QualificationImprovement[]>('/qualifications', { params: { teacherId } }).then(r => r.data);

export const getQualification = (id: number) =>
  api.get<QualificationImprovement>(`/qualifications/${id}`).then(r => r.data);

export const createQualification = (data: Partial<QualificationImprovement>) =>
  api.post<QualificationImprovement>('/qualifications', data).then(r => r.data);

export const updateQualification = (id: number, data: Partial<QualificationImprovement>) =>
  api.put<QualificationImprovement>(`/qualifications/${id}`, data).then(r => r.data);

export const deleteQualification = (id: number) =>
  api.delete(`/qualifications/${id}`);
