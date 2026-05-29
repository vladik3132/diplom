import api from './axiosInstance';
import { EditorialPlan, EditorialPlanItem } from '@/types/editorial';

export const getEditorialPlans = (departmentId?: number) =>
  api.get<EditorialPlan[]>('/editorial/plans', { params: { departmentId } }).then(r => r.data);

export const getEditorialPlan = (id: number) =>
  api.get<EditorialPlan>(`/editorial/plans/${id}`).then(r => r.data);

export const createEditorialPlan = (data: Partial<EditorialPlan>) =>
  api.post<EditorialPlan>('/editorial/plans', data).then(r => r.data);

export const updateEditorialPlan = (id: number, data: Partial<EditorialPlan>) =>
  api.put<EditorialPlan>(`/editorial/plans/${id}`, data).then(r => r.data);

export const deleteEditorialPlan = (id: number) =>
  api.delete(`/editorial/plans/${id}`);

export const getEditorialItems = (planId: number) =>
  api.get<EditorialPlanItem[]>(`/editorial/plans/${planId}/items`).then(r => r.data);

export const createEditorialItem = (data: Partial<EditorialPlanItem>) =>
  api.post<EditorialPlanItem>('/editorial/items', data).then(r => r.data);

export const updateEditorialItem = (id: number, data: Partial<EditorialPlanItem>) =>
  api.put<EditorialPlanItem>(`/editorial/items/${id}`, data).then(r => r.data);

export const deleteEditorialItem = (id: number) =>
  api.delete(`/editorial/items/${id}`);
