import axiosInstance from './axiosInstance';
import type { FileAttachment } from '@/types/fileAttachment';

export const uploadFile = async (
  file: File,
  entityType: string,
  entityId: number,
  teacherId?: number,
): Promise<FileAttachment> => {
  const formData = new FormData();
  formData.append('file', file);
  formData.append('entityType', entityType);
  formData.append('entityId', String(entityId));
  if (teacherId) formData.append('teacherId', String(teacherId));

  const response = await axiosInstance.post<FileAttachment>('/files', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
  return response.data;
};

export const getFiles = async (
  entityType: string,
  entityId: number,
): Promise<FileAttachment[]> => {
  const response = await axiosInstance.get<FileAttachment[]>('/files', {
    params: { entityType, entityId },
  });
  return response.data;
};

export const deleteFile = async (id: number): Promise<void> => {
  await axiosInstance.delete(`/files/${id}`);
};

/** Get JWT token from localStorage for URL-based auth (iframe/img can't send headers) */
const getTokenParam = (): string => {
  const token = localStorage.getItem('keycloak_token') || localStorage.getItem('token');
  return token ? `?token=${encodeURIComponent(token)}` : '';
};

/** Build preview URL with auth token (for iframe/img src) */
export const getFilePreviewUrl = (id: number): string => `/api/files/${id}/preview${getTokenParam()}`;

/** Build download URL with auth token */
export const getFileDownloadUrl = (id: number): string => `/api/files/${id}/download${getTokenParam()}`;

/** Format file size for display */
export const formatFileSize = (bytes: number): string => {
  if (bytes < 1024) return bytes + ' Б';
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' КБ';
  return (bytes / (1024 * 1024)).toFixed(1) + ' МБ';
};
