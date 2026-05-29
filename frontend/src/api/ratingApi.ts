import axiosInstance from './axiosInstance';
import type {
  OpenLesson,
  MethodologicalExperiment,
  AcademicMobility,
  ForeignInternship,
  ProgramWorkingGroup,
} from '../types/rating';

// ── OpenLesson ──

export const getOpenLessons = async (teacherId: number): Promise<OpenLesson[]> => {
  const { data } = await axiosInstance.get<OpenLesson[]>(`/teachers/${teacherId}/open-lessons`);
  return data;
};

export const createOpenLesson = async (teacherId: number, dto: Partial<OpenLesson>): Promise<OpenLesson> => {
  const { data } = await axiosInstance.post<OpenLesson>(`/teachers/${teacherId}/open-lessons`, dto);
  return data;
};

export const updateOpenLesson = async (teacherId: number, id: number, dto: Partial<OpenLesson>): Promise<OpenLesson> => {
  const { data } = await axiosInstance.put<OpenLesson>(`/teachers/${teacherId}/open-lessons/${id}`, dto);
  return data;
};

export const deleteOpenLesson = async (teacherId: number, id: number): Promise<void> => {
  await axiosInstance.delete(`/teachers/${teacherId}/open-lessons/${id}`);
};

// ── MethodologicalExperiment ──

export const getExperiments = async (teacherId: number): Promise<MethodologicalExperiment[]> => {
  const { data } = await axiosInstance.get<MethodologicalExperiment[]>(`/teachers/${teacherId}/methodological-experiments`);
  return data;
};

export const createExperiment = async (teacherId: number, dto: Partial<MethodologicalExperiment>): Promise<MethodologicalExperiment> => {
  const { data } = await axiosInstance.post<MethodologicalExperiment>(`/teachers/${teacherId}/methodological-experiments`, dto);
  return data;
};

export const updateExperiment = async (teacherId: number, id: number, dto: Partial<MethodologicalExperiment>): Promise<MethodologicalExperiment> => {
  const { data } = await axiosInstance.put<MethodologicalExperiment>(`/teachers/${teacherId}/methodological-experiments/${id}`, dto);
  return data;
};

export const deleteExperiment = async (teacherId: number, id: number): Promise<void> => {
  await axiosInstance.delete(`/teachers/${teacherId}/methodological-experiments/${id}`);
};

// ── AcademicMobility ──

export const getMobilities = async (teacherId: number): Promise<AcademicMobility[]> => {
  const { data } = await axiosInstance.get<AcademicMobility[]>(`/teachers/${teacherId}/academic-mobilities`);
  return data;
};

export const createMobility = async (teacherId: number, dto: Partial<AcademicMobility>): Promise<AcademicMobility> => {
  const { data } = await axiosInstance.post<AcademicMobility>(`/teachers/${teacherId}/academic-mobilities`, dto);
  return data;
};

export const updateMobility = async (teacherId: number, id: number, dto: Partial<AcademicMobility>): Promise<AcademicMobility> => {
  const { data } = await axiosInstance.put<AcademicMobility>(`/teachers/${teacherId}/academic-mobilities/${id}`, dto);
  return data;
};

export const deleteMobility = async (teacherId: number, id: number): Promise<void> => {
  await axiosInstance.delete(`/teachers/${teacherId}/academic-mobilities/${id}`);
};

// ── ForeignInternship — Міжнародне стажування ──

export const getForeignInternships = async (teacherId: number): Promise<ForeignInternship[]> => {
  const { data } = await axiosInstance.get<ForeignInternship[]>(`/teachers/${teacherId}/foreign-internships`);
  return data;
};

export const createForeignInternship = async (
  teacherId: number, dto: Partial<ForeignInternship>,
): Promise<ForeignInternship> => {
  const { data } = await axiosInstance.post<ForeignInternship>(`/teachers/${teacherId}/foreign-internships`, dto);
  return data;
};

export const updateForeignInternship = async (
  teacherId: number, id: number, dto: Partial<ForeignInternship>,
): Promise<ForeignInternship> => {
  const { data } = await axiosInstance.put<ForeignInternship>(`/teachers/${teacherId}/foreign-internships/${id}`, dto);
  return data;
};

export const deleteForeignInternship = async (teacherId: number, id: number): Promise<void> => {
  await axiosInstance.delete(`/teachers/${teacherId}/foreign-internships/${id}`);
};

// ── ProgramWorkingGroup ──

export const getWorkingGroup = async (programId: number): Promise<ProgramWorkingGroup[]> => {
  const { data } = await axiosInstance.get<ProgramWorkingGroup[]>(`/programs/${programId}/working-group`);
  return data;
};

export const addWorkingGroupMember = async (programId: number, dto: Partial<ProgramWorkingGroup>): Promise<ProgramWorkingGroup> => {
  const { data } = await axiosInstance.post<ProgramWorkingGroup>(`/programs/${programId}/working-group`, dto);
  return data;
};

export const updateWorkingGroupMember = async (programId: number, id: number, dto: Partial<ProgramWorkingGroup>): Promise<ProgramWorkingGroup> => {
  const { data } = await axiosInstance.put<ProgramWorkingGroup>(`/programs/${programId}/working-group/${id}`, dto);
  return data;
};

export const removeWorkingGroupMember = async (programId: number, id: number): Promise<void> => {
  await axiosInstance.delete(`/programs/${programId}/working-group/${id}`);
};
