import axiosInstance from './axiosInstance';

export interface LoginResponse {
  token: string;
  email: string;
  role: string;
  teacherId: number | null;
  departmentId: number | null;
}

export const login = async (email: string, password: string): Promise<LoginResponse> => {
  const response = await axiosInstance.post<LoginResponse>('/auth/login', { email, password });
  return response.data;
};

export const fetchMe = async (token?: string): Promise<LoginResponse> => {
  const config = token ? { headers: { Authorization: `Bearer ${token}` } } : {};
  const response = await axiosInstance.get<LoginResponse>('/auth/me', config);
  return response.data;
};

export const refreshToken = async (): Promise<LoginResponse> => {
  const response = await axiosInstance.post<LoginResponse>('/auth/refresh');
  return response.data;
};

export const register = async (email: string, password: string, role: string): Promise<any> => {
  const response = await axiosInstance.post('/auth/register', { email, password, role });
  return response.data;
};
