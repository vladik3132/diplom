import axiosInstance from './axiosInstance';
import { Department, Faculty, DepartmentComplianceSummary, StaffPosition } from '../types/teacher';

// ── Faculty ──────────────────────────────────────────────────────────

export const getFaculties = async (): Promise<Faculty[]> => {
  const response = await axiosInstance.get<Faculty[]>('/faculties');
  return response.data;
};

export const createFaculty = async (data: Partial<Faculty>): Promise<Faculty> => {
  const response = await axiosInstance.post<Faculty>('/faculties', data);
  return response.data;
};

export const updateFaculty = async (id: number, data: Partial<Faculty>): Promise<Faculty> => {
  const response = await axiosInstance.put<Faculty>(`/faculties/${id}`, data);
  return response.data;
};

export const deleteFaculty = async (id: number): Promise<void> => {
  await axiosInstance.delete(`/faculties/${id}`);
};

// ── Department ───────────────────────────────────────────────────────

export const getDepartments = async (): Promise<Department[]> => {
  const response = await axiosInstance.get<Department[]>('/departments');
  return response.data;
};

export const createDepartment = async (data: { name: string; faculty?: { id: number } | null }): Promise<Department> => {
  const response = await axiosInstance.post<Department>('/departments', data);
  return response.data;
};

export const updateDepartment = async (id: number, data: { name: string; faculty?: { id: number } | null }): Promise<Department> => {
  const response = await axiosInstance.put<Department>(`/departments/${id}`, data);
  return response.data;
};

export const deleteDepartment = async (id: number): Promise<void> => {
  await axiosInstance.delete(`/departments/${id}`);
};

// ── Department Compliance ────────────────────────────────────────────

export const getDepartmentComplianceSummaries = async (): Promise<DepartmentComplianceSummary[]> => {
  const response = await axiosInstance.get<DepartmentComplianceSummary[]>('/departments/compliance-summary');
  return response.data;
};

export const getDepartmentComplianceSummary = async (id: number): Promise<DepartmentComplianceSummary> => {
  const response = await axiosInstance.get<DepartmentComplianceSummary>(`/departments/${id}/compliance-summary`);
  return response.data;
};

// ── Staff Positions ──────────────────────────────────────────────────

export const getStaffPositions = async (deptId: number): Promise<StaffPosition[]> => {
  const response = await axiosInstance.get<StaffPosition[]>(`/departments/${deptId}/staff-positions`);
  return response.data;
};

export const createStaffPosition = async (deptId: number, data: Partial<StaffPosition>): Promise<StaffPosition> => {
  const response = await axiosInstance.post<StaffPosition>(`/departments/${deptId}/staff-positions`, data);
  return response.data;
};

export const updateStaffPosition = async (deptId: number, id: number, data: Partial<StaffPosition>): Promise<StaffPosition> => {
  const response = await axiosInstance.put<StaffPosition>(`/departments/${deptId}/staff-positions/${id}`, data);
  return response.data;
};

export const deleteStaffPosition = async (deptId: number, id: number): Promise<void> => {
  await axiosInstance.delete(`/departments/${deptId}/staff-positions/${id}`);
};

export const batchImportStaffPositions = async (deptId: number, positions: Partial<StaffPosition>[]): Promise<StaffPosition[]> => {
  const response = await axiosInstance.post<StaffPosition[]>(`/departments/${deptId}/staff-positions/batch`, positions);
  return response.data;
};

export const relinkStaffPositions = async (deptId: number): Promise<{ linked: number }> => {
  const response = await axiosInstance.post<{ linked: number }>(`/departments/${deptId}/staff-positions/relink`);
  return response.data;
};
