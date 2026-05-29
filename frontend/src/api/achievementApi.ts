import api from './axiosInstance';
import { Achievement, AchievementProgress, ComplianceReport, AchievementValidationResponse, AchievementValidationSuggestion, BatchReclassifyItem, ValidationSessionInfo } from '@/types/achievement';

export const getAchievements = (teacherId?: number) =>
  teacherId
    ? api.get<Achievement[]>(`/teachers/${teacherId}/achievements`).then(r => r.data)
    : api.get<Achievement[]>('/achievements').then(r => r.data);

export const createAchievement = (data: Partial<Achievement>) =>
  api.post<Achievement>('/achievements', data).then(r => r.data);

export const updateAchievement = (id: number, data: Partial<Achievement>) =>
  api.put<Achievement>(`/achievements/${id}`, data).then(r => r.data);

export const deleteAchievement = (id: number) =>
  api.delete(`/achievements/${id}`);

export const getComplianceAll = (departmentId?: number) =>
  api.get<ComplianceReport[]>('/compliance', { params: { departmentId } }).then(r => r.data);

export const getComplianceByTeacher = (teacherId: number) =>
  api.get<ComplianceReport>(`/compliance/${teacherId}`).then(r => r.data);

export const refreshComplianceAll = () =>
  api.post<{ refreshed: number }>('/compliance/refresh').then(r => r.data);

export const refreshComplianceForTeacher = (teacherId: number) =>
  api.post<ComplianceReport>(`/compliance/refresh/${teacherId}`).then(r => r.data);

export const refreshComplianceForDepartment = (departmentId: number) =>
  api.post<{ refreshed: number }>(`/compliance/refresh/department/${departmentId}`).then(r => r.data);

// --- Progress (lightweight, no AI) ---

export const getAchievementProgress = (teacherId: number) =>
  api.get<AchievementProgress[]>(`/teachers/${teacherId}/achievements/progress`).then(r => r.data);

// --- AI Validation ---

export const getAiValidationStatus = () =>
  api.get<{ available: boolean }>('/achievements/ai/status').then(r => r.data);

export const validateAchievementsByIds = (achievementIds: number[]) =>
  api.post<AchievementValidationResponse>('/achievements/validate', { achievementIds }).then(r => r.data);

export const validateAchievementsByTeacher = (teacherId: number) =>
  api.post<AchievementValidationResponse>('/achievements/validate', { teacherId }).then(r => r.data);

export const batchReclassify = (items: BatchReclassifyItem[]) =>
  api.put<{ updated: number }>('/achievements/batch-reclassify', { items }).then(r => r.data);

export const validateSingleAchievement = (description: string, achievementType: string) =>
  api.post<AchievementValidationSuggestion>('/achievements/validate-single', { description, achievementType }).then(r => r.data);

// --- Validation History ---

export const getValidationHistory = (teacherId: number) =>
  api.get<ValidationSessionInfo[]>(`/teachers/${teacherId}/validation/history`).then(r => r.data);

export const getLatestValidation = (teacherId: number) =>
  api.get<AchievementValidationResponse>(`/teachers/${teacherId}/validation/latest`).then(r => r.data);

export const getSessionResults = (sessionId: string) =>
  api.get<AchievementValidationResponse>(`/validation/session/${sessionId}`).then(r => r.data);
