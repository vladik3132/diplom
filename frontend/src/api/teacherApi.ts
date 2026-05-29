import axiosInstance from './axiosInstance';
import { Teacher, Education, MilitaryEducation, CareerRecord, LanguageSkill, AcademicDegree, AcademicTitle } from '../types/teacher';

/** Spring Data Page<T> payload shape. */
export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;       // 0-based current page
  size: number;
  first: boolean;
  last: boolean;
  empty: boolean;
  numberOfElements: number;
}

export const getTeachers = async (params?: { departmentId?: number }): Promise<Teacher[]> => {
  const response = await axiosInstance.get<Teacher[]>('/teachers', { params });
  return response.data;
};

/**
 * Server-side paged + filtered teachers list.
 * Використовувати замість getTeachers() у списках — уникає N+1 і фільтрації на клієнті.
 */
export const getTeachersPaged = async (params: {
  page: number;
  size: number;
  search?: string;
  departmentId?: number;
}): Promise<PageResponse<Teacher>> => {
  const clean: Record<string, unknown> = { page: params.page, size: params.size };
  if (params.search && params.search.trim()) clean.search = params.search.trim();
  if (params.departmentId != null) clean.departmentId = params.departmentId;
  const response = await axiosInstance.get<PageResponse<Teacher>>('/teachers', { params: clean });
  return response.data;
};

export const getTeacher = async (id: number): Promise<Teacher> => {
  const response = await axiosInstance.get<Teacher>(`/teachers/${id}`);
  return response.data;
};

export const createTeacher = async (data: Omit<Teacher, 'id'>): Promise<Teacher> => {
  const response = await axiosInstance.post<Teacher>('/teachers', data);
  return response.data;
};

export const updateTeacher = async (id: number, data: Partial<Teacher>): Promise<Teacher> => {
  const response = await axiosInstance.put<Teacher>(`/teachers/${id}`, data);
  return response.data;
};

export const deleteTeacher = async (id: number): Promise<void> => {
  await axiosInstance.delete(`/teachers/${id}`);
};

// ── Educations CRUD ───────────────────────────────────────

export const getEducations = async (teacherId: number): Promise<Education[]> => {
  const response = await axiosInstance.get<Education[]>(`/teachers/${teacherId}/educations`);
  return response.data;
};

export const createEducation = async (teacherId: number, data: Partial<Education>): Promise<Education> => {
  const response = await axiosInstance.post<Education>(`/teachers/${teacherId}/educations`, data);
  return response.data;
};

export const updateEducation = async (teacherId: number, eduId: number, data: Partial<Education>): Promise<Education> => {
  const response = await axiosInstance.put<Education>(`/teachers/${teacherId}/educations/${eduId}`, data);
  return response.data;
};

export const deleteEducation = async (teacherId: number, eduId: number): Promise<void> => {
  await axiosInstance.delete(`/teachers/${teacherId}/educations/${eduId}`);
};

// ── Academic Degrees CRUD ─────────────────────────────────

export const getAcademicDegrees = async (teacherId: number): Promise<AcademicDegree[]> => {
  const response = await axiosInstance.get<AcademicDegree[]>(`/teachers/${teacherId}/academic-degrees`);
  return response.data;
};

export const createAcademicDegree = async (teacherId: number, data: Partial<AcademicDegree>): Promise<AcademicDegree> => {
  const response = await axiosInstance.post<AcademicDegree>(`/teachers/${teacherId}/academic-degrees`, data);
  return response.data;
};

export const updateAcademicDegree = async (teacherId: number, degreeId: number, data: Partial<AcademicDegree>): Promise<AcademicDegree> => {
  const response = await axiosInstance.put<AcademicDegree>(`/teachers/${teacherId}/academic-degrees/${degreeId}`, data);
  return response.data;
};

export const deleteAcademicDegree = async (teacherId: number, degreeId: number): Promise<void> => {
  await axiosInstance.delete(`/teachers/${teacherId}/academic-degrees/${degreeId}`);
};

// ── Academic Titles CRUD ─────────────────────────────────

export const getAcademicTitles = async (teacherId: number): Promise<AcademicTitle[]> => {
  const response = await axiosInstance.get<AcademicTitle[]>(`/teachers/${teacherId}/academic-titles`);
  return response.data;
};

export const createAcademicTitle = async (teacherId: number, data: Partial<AcademicTitle>): Promise<AcademicTitle> => {
  const response = await axiosInstance.post<AcademicTitle>(`/teachers/${teacherId}/academic-titles`, data);
  return response.data;
};

export const updateAcademicTitle = async (teacherId: number, titleId: number, data: Partial<AcademicTitle>): Promise<AcademicTitle> => {
  const response = await axiosInstance.put<AcademicTitle>(`/teachers/${teacherId}/academic-titles/${titleId}`, data);
  return response.data;
};

export const deleteAcademicTitle = async (teacherId: number, titleId: number): Promise<void> => {
  await axiosInstance.delete(`/teachers/${teacherId}/academic-titles/${titleId}`);
};

// ── Military Educations CRUD ─────────────────────────────

export const getMilitaryEducations = async (teacherId: number): Promise<MilitaryEducation[]> => {
  const response = await axiosInstance.get<MilitaryEducation[]>(`/teachers/${teacherId}/military-educations`);
  return response.data;
};

export const createMilitaryEducation = async (teacherId: number, data: Partial<MilitaryEducation>): Promise<MilitaryEducation> => {
  const response = await axiosInstance.post<MilitaryEducation>(`/teachers/${teacherId}/military-educations`, data);
  return response.data;
};

export const updateMilitaryEducation = async (teacherId: number, meId: number, data: Partial<MilitaryEducation>): Promise<MilitaryEducation> => {
  const response = await axiosInstance.put<MilitaryEducation>(`/teachers/${teacherId}/military-educations/${meId}`, data);
  return response.data;
};

export const deleteMilitaryEducation = async (teacherId: number, meId: number): Promise<void> => {
  await axiosInstance.delete(`/teachers/${teacherId}/military-educations/${meId}`);
};

// ── Career Records CRUD ───────────────────────────────────

export const getCareerRecords = async (teacherId: number): Promise<CareerRecord[]> => {
  const response = await axiosInstance.get<CareerRecord[]>(`/teachers/${teacherId}/career`);
  return response.data;
};

export const createCareerRecord = async (teacherId: number, data: Partial<CareerRecord>): Promise<CareerRecord> => {
  const response = await axiosInstance.post<CareerRecord>(`/teachers/${teacherId}/career`, data);
  return response.data;
};

export const updateCareerRecord = async (teacherId: number, recId: number, data: Partial<CareerRecord>): Promise<CareerRecord> => {
  const response = await axiosInstance.put<CareerRecord>(`/teachers/${teacherId}/career/${recId}`, data);
  return response.data;
};

export const deleteCareerRecord = async (teacherId: number, recId: number): Promise<void> => {
  await axiosInstance.delete(`/teachers/${teacherId}/career/${recId}`);
};

// ── Language Skills CRUD ──────────────────────────────────

export const getLanguageSkills = async (teacherId: number): Promise<LanguageSkill[]> => {
  const response = await axiosInstance.get<LanguageSkill[]>(`/teachers/${teacherId}/languages`);
  return response.data;
};

export const createLanguageSkill = async (teacherId: number, data: Partial<LanguageSkill>): Promise<LanguageSkill> => {
  const response = await axiosInstance.post<LanguageSkill>(`/teachers/${teacherId}/languages`, data);
  return response.data;
};

export const updateLanguageSkill = async (teacherId: number, recId: number, data: Partial<LanguageSkill>): Promise<LanguageSkill> => {
  const response = await axiosInstance.put<LanguageSkill>(`/teachers/${teacherId}/languages/${recId}`, data);
  return response.data;
};

export const deleteLanguageSkill = async (teacherId: number, recId: number): Promise<void> => {
  await axiosInstance.delete(`/teachers/${teacherId}/languages/${recId}`);
};
