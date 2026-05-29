import axiosInstance from './axiosInstance';
import type {
  ColumnHeader,
  DataField,
  DocxExportTemplate,
  TemplateUploadResponse,
} from '../types/docxExport';

// ── Upload & parse ──────────────────────────────────────────────────

export const uploadTemplate = async (
  file: File,
  tableIndex = 0,
  headerRowCount = 1,
): Promise<TemplateUploadResponse> => {
  const formData = new FormData();
  formData.append('file', file);
  formData.append('tableIndex', String(tableIndex));
  formData.append('headerRowCount', String(headerRowCount));

  const response = await axiosInstance.post<TemplateUploadResponse>(
    '/docx-export/upload-template',
    formData,
    { headers: { 'Content-Type': 'multipart/form-data' } },
  );
  return response.data;
};

export const parseHeaders = async (
  templateFileName: string,
  tableIndex = 0,
  headerRowCount = 1,
): Promise<ColumnHeader[]> => {
  const response = await axiosInstance.post<ColumnHeader[]>(
    '/docx-export/parse-headers',
    { templateFileName, tableIndex, headerRowCount },
  );
  return response.data;
};

// ── Data fields catalog ─────────────────────────────────────────────

export const getDataFields = async (): Promise<DataField[]> => {
  const response = await axiosInstance.get<DataField[]>('/docx-export/data-fields');
  return response.data;
};

// ── CRUD for mappings ───────────────────────────────────────────────

export const getMappings = async (): Promise<DocxExportTemplate[]> => {
  const response = await axiosInstance.get<DocxExportTemplate[]>('/docx-export/mappings');
  return response.data;
};

export const getMapping = async (id: number): Promise<DocxExportTemplate> => {
  const response = await axiosInstance.get<DocxExportTemplate>(`/docx-export/mappings/${id}`);
  return response.data;
};

export const createMapping = async (
  data: Omit<DocxExportTemplate, 'id' | 'createdAt' | 'updatedAt'>,
): Promise<DocxExportTemplate> => {
  const response = await axiosInstance.post<DocxExportTemplate>('/docx-export/mappings', data);
  return response.data;
};

export const updateMapping = async (
  id: number,
  data: Partial<DocxExportTemplate>,
): Promise<DocxExportTemplate> => {
  const response = await axiosInstance.put<DocxExportTemplate>(`/docx-export/mappings/${id}`, data);
  return response.data;
};

export const deleteMapping = async (id: number): Promise<void> => {
  await axiosInstance.delete(`/docx-export/mappings/${id}`);
};

// ── Export ───────────────────────────────────────────────────────────

export const exportDepartment = async (templateId: number, departmentId: number): Promise<void> => {
  const response = await axiosInstance.get(
    `/docx-export/generate/${templateId}/department/${departmentId}`,
    { responseType: 'blob' },
  );

  // Extract filename from Content-Disposition header if available
  const contentDisposition = response.headers['content-disposition'];
  let filename = `export_department_${departmentId}.docx`;
  if (contentDisposition) {
    const match = contentDisposition.match(/filename\*?=(?:UTF-8'')?(.+)/i);
    if (match) {
      filename = decodeURIComponent(match[1].replace(/['"]/g, ''));
    }
  }

  const url = window.URL.createObjectURL(new Blob([response.data]));
  const link = document.createElement('a');
  link.href = url;
  link.setAttribute('download', filename);
  document.body.appendChild(link);
  link.click();
  link.remove();
  window.URL.revokeObjectURL(url);
};

export const exportByProgram = async (templateId: number, programId: number): Promise<void> => {
  const response = await axiosInstance.get(
    `/docx-export/generate/${templateId}/program/${programId}`,
    { responseType: 'blob' },
  );

  const contentDisposition = response.headers['content-disposition'];
  let filename = `export_program_${programId}.docx`;
  if (contentDisposition) {
    const match = contentDisposition.match(/filename\*?=(?:UTF-8'')?(.+)/i);
    if (match) {
      filename = decodeURIComponent(match[1].replace(/['"]/g, ''));
    }
  }

  const url = window.URL.createObjectURL(new Blob([response.data]));
  const link = document.createElement('a');
  link.href = url;
  link.setAttribute('download', filename);
  document.body.appendChild(link);
  link.click();
  link.remove();
  window.URL.revokeObjectURL(url);
};

export const exportByTeachers = async (templateId: number, teacherIds: number[]): Promise<void> => {
  const response = await axiosInstance.post(
    `/docx-export/generate-teachers/${templateId}`,
    teacherIds,
    { responseType: 'blob' },
  );

  const contentDisposition = response.headers['content-disposition'];
  let filename = `export_teachers_${teacherIds.length}.docx`;
  if (contentDisposition) {
    const match = contentDisposition.match(/filename\*?=(?:UTF-8'')?(.+)/i);
    if (match) {
      filename = decodeURIComponent(match[1].replace(/['"]/g, ''));
    }
  }

  const url = window.URL.createObjectURL(new Blob([response.data]));
  const link = document.createElement('a');
  link.href = url;
  link.setAttribute('download', filename);
  document.body.appendChild(link);
  link.click();
  link.remove();
  window.URL.revokeObjectURL(url);
};

export const exportDepartments = async (templateId: number, departmentIds: number[]): Promise<void> => {
  const response = await axiosInstance.post(
    `/docx-export/generate-multi/${templateId}`,
    departmentIds.length > 0 ? departmentIds : [],
    { responseType: 'blob' },
  );

  const contentDisposition = response.headers['content-disposition'];
  let filename = departmentIds.length === 0 ? 'export_all.docx' : `export_departments.docx`;
  if (contentDisposition) {
    const match = contentDisposition.match(/filename\*?=(?:UTF-8'')?(.+)/i);
    if (match) {
      filename = decodeURIComponent(match[1].replace(/['"]/g, ''));
    }
  }

  const url = window.URL.createObjectURL(new Blob([response.data]));
  const link = document.createElement('a');
  link.href = url;
  link.setAttribute('download', filename);
  document.body.appendChild(link);
  link.click();
  link.remove();
  window.URL.revokeObjectURL(url);
};
