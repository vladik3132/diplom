import api from './axiosInstance';
import { Publication } from '@/types/publication';

export const getPublications = (teacherId?: number) =>
  api.get<Publication[]>('/publications', { params: { teacherId } }).then(r => r.data);

export const getPublication = (id: number) =>
  api.get<Publication>(`/publications/${id}`).then(r => r.data);

export const createPublication = (data: Partial<Publication>) =>
  api.post<Publication>('/publications', data).then(r => r.data);

export const updatePublication = (id: number, data: Partial<Publication>) =>
  api.put<Publication>(`/publications/${id}`, data).then(r => r.data);

export const deletePublication = (id: number) =>
  api.delete(`/publications/${id}`);

export const updatePublicationStatus = (id: number, status: string) =>
  api.patch<Publication>(`/publications/${id}/status`, { status }).then(r => r.data);

export const classifyPublication = (id: number, data: { type?: string; articleCategory?: string; methodicalSubtype?: string; approbationSubtype?: string }) =>
  api.patch<Publication>(`/publications/${id}/classify`, data).then(r => r.data);

export interface ScopusVerificationResult {
  found: boolean;
  authorConfirmed: boolean;
  scopusId?: string;
  matchedAuthorName?: string;
  searchMethod?: string;
  scopusTitle?: string;
  error?: string;
}

export const verifyScopus = (id: number) =>
  api.post<ScopusVerificationResult>(`/publications/${id}/verify-scopus`).then(r => r.data);

export const reclassifySubtypes = () =>
  api.post<{ reclassified: number }>('/publications/reclassify-subtypes').then(r => r.data);

export const reclassifySubtypesForDepartment = (departmentId: number) =>
  api.post<{ reclassified: number; departmentId: number }>(
    `/publications/reclassify-subtypes/department/${departmentId}`
  ).then(r => r.data);
