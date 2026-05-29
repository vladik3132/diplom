import axiosInstance from './axiosInstance';

export interface RatingPeriodDto {
  id: number;
  name: string;
  startDate: string;
  endDate: string;
  active: boolean;
}

export interface TeacherRatingDetail {
  id: number;
  criterion: string;
  criterionLabel: string;
  count: number;
  pointsPerUnit: number;
  score: number;
}

export interface TeacherRatingSummary {
  teacherId: number;
  teacherName: string;
  departmentName: string;
  departmentNumber?: string;
  position?: string;
  militaryRank?: string;
  totalScore: number;
  rank: number;
  details?: TeacherRatingDetail[];
}

export interface DepartmentRatingSummary {
  departmentId: number;
  departmentName: string;
  facultyName: string;
  teacherCount: number;
  totalScore: number;
  averageScore: number;
  rank: number;
  teachers?: TeacherRatingSummary[];
}

// ── Periods ──

export const getRatingPeriods = async (): Promise<RatingPeriodDto[]> => {
  const { data } = await axiosInstance.get<RatingPeriodDto[]>('/rating/periods');
  return data;
};

export const createRatingPeriod = async (dto: Partial<RatingPeriodDto>): Promise<RatingPeriodDto> => {
  const { data } = await axiosInstance.post<RatingPeriodDto>('/rating/periods', dto);
  return data;
};

export const createRatingPeriodForYear = async (year: number): Promise<RatingPeriodDto> => {
  const { data } = await axiosInstance.post<RatingPeriodDto>(`/rating/periods/for-year/${year}`);
  return data;
};

// ── Calculation ──

export const calculateAllRatings = async (periodId: number): Promise<{ teachersCalculated: number }> => {
  const { data } = await axiosInstance.post(`/rating/calculate/${periodId}`);
  return data;
};

export const calculateTeacherRating = async (periodId: number, teacherId: number): Promise<TeacherRatingSummary> => {
  const { data } = await axiosInstance.post<TeacherRatingSummary>(`/rating/calculate/${periodId}/teacher/${teacherId}`);
  return data;
};

// ── Rankings ──

export const getTeacherRankings = async (periodId: number, departmentId?: number): Promise<TeacherRatingSummary[]> => {
  const params = departmentId ? { departmentId } : {};
  const { data } = await axiosInstance.get<TeacherRatingSummary[]>(`/rating/${periodId}/teachers`, { params });
  return data;
};

export const getTeacherRatingDetails = async (periodId: number, teacherId: number): Promise<TeacherRatingSummary> => {
  const { data } = await axiosInstance.get<TeacherRatingSummary>(`/rating/${periodId}/teachers/${teacherId}`);
  return data;
};

export const getDepartmentRankings = async (periodId: number): Promise<DepartmentRatingSummary[]> => {
  const { data } = await axiosInstance.get<DepartmentRatingSummary[]>(`/rating/${periodId}/departments`);
  return data;
};

export interface FacultyRatingSummary {
  facultyId: number;
  facultyName: string;
  departmentCount: number;
  teacherCount: number;
  totalScore: number;
  averageScore: number;
  rank: number;
  departments?: DepartmentRatingSummary[];
}

export const getFacultyRankings = async (periodId: number): Promise<FacultyRatingSummary[]> => {
  const { data } = await axiosInstance.get<FacultyRatingSummary[]>(`/rating/${periodId}/faculties`);
  return data;
};

// ── Protocol ──

export interface CommissionMember {
  role: 'CHAIR' | 'VICE_CHAIR' | 'SECRETARY' | 'MEMBER';
  rank: string;
  name: string;
  shortName: string;
}

export interface ProtocolRequest {
  institutionName: string;
  protocolDate: string;
  protocolNumber: string;
  orderNumber: string;
  orderDate: string;
  commissionMembers: CommissionMember[];
}

export const generateProtocol = async (periodId: number, request: ProtocolRequest): Promise<Blob> => {
  const { data } = await axiosInstance.post(`/rating/${periodId}/protocol`, request, {
    responseType: 'blob',
  });
  return data;
};

// ── Protocol settings (persistence) ──

export interface ProtocolSettingsData {
  institutionName?: string;
  orderNumber?: string;
  orderDate?: string;
  commissionMembers?: CommissionMember[];
}

export const getProtocolSettings = async (): Promise<ProtocolSettingsData> => {
  const { data } = await axiosInstance.get<ProtocolSettingsData>('/rating/protocol-settings');
  return data;
};

export const saveProtocolSettings = async (settings: ProtocolSettingsData): Promise<void> => {
  await axiosInstance.post('/rating/protocol-settings', settings);
};

// ── Department settings (admin) ──

export interface RatingDepartmentSetting {
  departmentId: number;
  number: string;
  name: string;
  facultyName: string | null;
  ratingExcluded: boolean;
}

export const getRatingDepartmentSettings = async (): Promise<RatingDepartmentSetting[]> => {
  const { data } = await axiosInstance.get<RatingDepartmentSetting[]>('/rating/department-settings');
  return data;
};

export const updateRatingDepartmentSettings = async (
  excludedDepartmentIds: number[],
): Promise<RatingDepartmentSetting[]> => {
  const { data } = await axiosInstance.put<RatingDepartmentSetting[]>('/rating/department-settings', {
    excludedDepartmentIds,
  });
  return data;
};

// ── Criterion records ──

export interface CriterionRecord {
  id: number;
  title: string;
  subtitle?: string;
  entityType: string;
}

export const getCriterionRecords = async (
  periodId: number,
  teacherId: number,
  criterion: string,
): Promise<CriterionRecord[]> => {
  const { data } = await axiosInstance.get<CriterionRecord[]>(
    `/rating/${periodId}/teachers/${teacherId}/criterion/${criterion}/records`,
  );
  return data;
};
