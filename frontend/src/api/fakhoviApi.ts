import api from './axiosInstance';
import { FakhovyiJournal, VerificationResult } from '@/types/fakhove';

/** Завантаження Excel з фаховими виданнями */
export const uploadFakhoviExcel = (file: File) => {
  const formData = new FormData();
  formData.append('file', file);
  return api.post<{ imported: number }>('/fakhovi-journals/upload-fakhovi', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  }).then(r => r.data);
};

/** Отримати список всіх фахових видань */
export const getFakhoviJournals = () =>
  api.get<FakhovyiJournal[]>('/fakhovi-journals').then(r => r.data);

/** Пошук журналів за назвою */
export const searchFakhoviJournals = (query: string) =>
  api.get<FakhovyiJournal[]>('/fakhovi-journals/search', {
    params: { q: query },
  }).then(r => r.data);

/** Верифікація журналу */
export const verifyJournal = (name: string, issn?: string) =>
  api.get<VerificationResult>('/fakhovi-journals/verify', {
    params: { name, issn },
  }).then(r => r.data);

/** Отримати к-сть записів */
export const getFakhoviCount = () =>
  api.get<{ fakhovi: number; scopus: number }>('/fakhovi-journals/count').then(r => r.data);
