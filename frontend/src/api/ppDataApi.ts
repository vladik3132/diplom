import api from './axiosInstance';

/**
 * Генерик CRUD API для всіх ppData сутностей.
 * Всі ендпоінти: /api/teachers/{teacherId}/{entityPath}
 */

export const getPpDataList = <T>(teacherId: number, entityPath: string) =>
  api.get<T[]>(`/teachers/${teacherId}/${entityPath}`).then(r => r.data);

export const getPpDataItem = <T>(teacherId: number, entityPath: string, id: number) =>
  api.get<T>(`/teachers/${teacherId}/${entityPath}/${id}`).then(r => r.data);

export const createPpData = <T>(teacherId: number, entityPath: string, data: Partial<T>) =>
  api.post<T>(`/teachers/${teacherId}/${entityPath}`, data).then(r => r.data);

export const updatePpData = <T>(teacherId: number, entityPath: string, id: number, data: Partial<T>) =>
  api.put<T>(`/teachers/${teacherId}/${entityPath}/${id}`, data).then(r => r.data);

export const deletePpData = (teacherId: number, entityPath: string, id: number) =>
  api.delete(`/teachers/${teacherId}/${entityPath}/${id}`);

// ── AI валідація даних п.38 ──

export interface PpDataValidationItem {
  entityType: string;
  ppNumber: number;
  ppLabel: string;
  entityId: number;
  entitySummary: string;
  status: 'OK' | 'WARNING' | 'ERROR';
  reasoning: string;
}

export interface PpDataValidationResponse {
  sessionId: string;
  totalChecked: number;
  validCount: number;
  warningCount: number;
  errorCount: number;
  validatedAt: string;
  items: PpDataValidationItem[];
}

export interface PpDataValidationSession {
  sessionId: string;
  validatedAt: string;
  totalChecked: number;
  validCount: number;
  warningCount: number;
  errorCount: number;
}

export const validatePpData = (teacherId: number) =>
  api.post<PpDataValidationResponse>(`/teachers/${teacherId}/ppdata/validate`).then(r => r.data);

export const validateSinglePpData = (teacherId: number, entityType: string, entityId: number) =>
  api.post<PpDataValidationItem>(`/teachers/${teacherId}/ppdata/validate/${entityType}/${entityId}`).then(r => r.data);

export const getPpDataValidationHistory = (teacherId: number) =>
  api.get<PpDataValidationSession[]>(`/teachers/${teacherId}/ppdata/validation-history`).then(r => r.data);

export const getPpDataValidationSession = (teacherId: number, sessionId: string) =>
  api.get<PpDataValidationResponse>(`/teachers/${teacherId}/ppdata/validation-session/${sessionId}`).then(r => r.data);

export const getPpDataAiStatus = (teacherId: number) =>
  api.get<{ available: boolean }>(`/teachers/${teacherId}/ppdata/ai-status`).then(r => r.data);

/** Останні статуси валідації: ключ "entityType:entityId" → { status, reasoning } */
export const getPpDataValidationStatuses = (teacherId: number) =>
  api.get<Record<string, { status: string; reasoning: string }>>(`/teachers/${teacherId}/ppdata/validation-statuses`).then(r => r.data);
