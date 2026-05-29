export interface EducationalProgram {
  id: number;
  name: string;
  shortCode?: string;
  educationLevel?: string;
  educationForm?: string;
  degree?: string;
  educationalQualification?: string;
  fieldOfKnowledge?: string;
  professionalQualification?: string;
  specialty?: string;
  credits?: number;
  specialization?: string;
  duration?: string;
  enrollmentYear?: number;
  department?: { id: number; number?: string; name: string };
  createdAt?: string;
  updatedAt?: string;
}

export interface ProgramStaffStats {
  degree?: string;
  totalTeachers: number;
  mainWithDegreeCount: number;
  mainWithDegreePercent: number;
  point35Compliant: boolean;
  doctorsOrProfessorsCount: number;
  doctorsOrProfessorsPercent: number;
  qualifiedMainCount: number;
  point35cCompliant: boolean;
  disciplinesTotal: number;
  disciplinesFullyStaffed: number;
  point36Compliant: boolean;
}

export interface TeacherQualificationDto {
  teacherId: number;
  teacherName: string;
  position?: string;
  employmentType?: string;
  academicDegree?: string;
  academicTitle?: string;
  point38TypeCount: number;
  point36Compliant: boolean;
  hasMatchingDiploma: boolean;
  hasMatchingDegree: boolean;
  hasPracticalExperience: boolean;
  hasDissertationSupervision: boolean;
  point37aCompliant: boolean;
  qualifiedPublicationsCount: number;
  point37bCompliant: boolean;
  point37Compliant: boolean;
  fullyCompliant: boolean;
}

export interface DisciplineStaffingDto {
  disciplineId: number;
  teachers: TeacherQualificationDto[];
  staffed: boolean;
}
