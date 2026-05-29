import type { ComplianceReport } from './achievement';

export interface Teacher {
  id: number;
  lastName: string;
  firstName: string;
  patronymic?: string;
  /** ISO date (YYYY-MM-DD). Раніше зберігався лише рік (birthYear), тепер — повна дата. */
  dateOfBirth?: string;
  militaryRank?: string;
  /** Primary посада викладача — з staff_positions (за seniority). Live-збагачення на бекенді. */
  effectivePosition?: string;
  /** Сума ставок усіх штатних позицій (0.5+0.5 = 1.0 тощо). */
  totalRate?: number;
  /** Primary staff_position має прапорець bootstrapped — UI показує ⚠️ і пропонує перевірити. */
  bootstrappedPosition?: boolean;
  employmentType: 'MAIN' | 'PART_TIME';
  departmentId?: number;
  departmentNumber?: string;
  departmentName?: string;
  experienceStartDate?: string; // ISO date, e.g. "2001-09-01"
  experienceYears?: number; // computed by backend
  university?: string;
  universitySpeciality?: string;
  universityDiploma?: string;
  universityGraduationYear?: number;
  universityDiplomaDate?: string;  // ISO date
  // ── Computed: primary (найвищий за рангом) ступінь та звання з academic_degrees/titles. ──
  // НЕ зберігаються на Teacher entity — обчислюються бекендом у TeacherDto.fromEntity + enrich.
  academicDegree?: string;
  academicTitle?: string;
  /** Кількість записів у academic_degrees (для UI індикації "має ще ступені"). */
  academicDegreesCount?: number;
  academicTitlesCount?: number;
  combatVeteranStatus: boolean;
  combatExperienceDates?: string;
  combatVeteranDoc?: string;
  combatVeteranDocDate?: string;  // ISO date
  combatVeteranDocIssuedBy?: string;
  // Військова освіта
  militaryEducationLevel?: 'OPERATIONAL' | 'STRATEGIC';
  militaryEducationDiploma?: string;
  militaryEducationDiplomaDate?: string;
  militaryEducationIssuedBy?: string;
  orcidId?: string;
  googleScholarUrl?: string;
  scopusId?: string;
  wosId?: string;
  email?: string;
  phone?: string;
}

export interface Department {
  id: number;
  number?: string;
  name: string;
  facultyId: number;
}

export interface Faculty {
  id: number;
  name: string;
}

export interface Education {
  id?: number;
  teacherId?: number;
  institution?: string;
  city?: string;
  degree?: string;
  speciality?: string;
  qualification?: string;
  graduationYear?: number;
  diploma?: string;
  diplomaDate?: string;
}

export interface AcademicDegree {
  id?: number;
  teacherId?: number;
  /** Назва ступеня (Доктор технічних наук, Доктор філософії, Кандидат технічних наук, ...) */
  degree?: string;
  /** Спеціальність ступеня (шифр+назва) */
  speciality?: string;
  /** Тема дисертації */
  dissertationTopic?: string;
  /** Реквізити диплома */
  diploma?: string;
  /** Дата видачі */
  diplomaDate?: string;
  /** Ким видано */
  issuedBy?: string;
}

export interface AcademicTitle {
  id?: number;
  teacherId?: number;
  /** Повна назва звання (напр. "Доцент кафедри комп'ютерних наук") */
  titleName?: string;
  /** Реквізити атестата */
  attestat?: string;
  /** Дата видачі атестата */
  attestatDate?: string;
  /** Ким видано */
  issuedBy?: string;
}

export interface MilitaryEducation {
  id?: number;
  teacherId?: number;
  level?: 'OPERATIONAL' | 'STRATEGIC';
  institution?: string;
  speciality?: string;
  diploma?: string;
  diplomaDate?: string;
  issuedBy?: string;
  graduationYear?: number;
}

export interface CareerRecord {
  id: number;
  teacher?: { id: number };
  position: string;
  organization: string;
  startDate?: string;
  endDate?: string;
  notes?: string;
}

export interface LanguageSkill {
  id: number;
  teacher?: { id: number };
  language: string;
  level?: string;
  certificateDetails?: string;
  certificateNumber?: string;
  certificateDate?: string;
  certificateOrganization?: string;
  certificateUrl?: string;
  // СМР (Стандартизований мовний рівень)
  smr1?: number;
  smr2?: number;
  smr3?: number;
  smr4?: number;
  smrLevel?: number; // computed: min(smr1..smr4)
}

export interface StaffPosition {
  id: number;
  orderNumber: number;
  positionTitle: string;
  militaryRankCategory?: string;
  militarySpecialtyCode?: string;
  tariffGrade?: number;
  rate?: number;
  importedTeacherName?: string;
  department?: { id: number; name: string };
  teacher?: { id: number; lastName: string; firstName: string; patronymic?: string } | null;
  /**
   * Запис створено автоматично під час bootstrap-міграції (з поля Teacher.position).
   * UI показує попередження поряд із посадою — адмін має перевірити та оновити
   * деталі (ставка, ШПК, тариф, ВОС). Будь-яке редагування знімає прапорець.
   */
  bootstrapped?: boolean;
}

// ── Допоміжні brief-DTO для розширеної статистики кафедри ──

export interface TeacherDegreeBrief {
  teacherId: number;
  fullName: string;
  degreeName?: string;
  speciality?: string;
}

export interface TeacherTitleBrief {
  teacherId: number;
  fullName: string;
  titleName?: string;
}

export interface PublicationYearBucket {
  year: number;
  scopus: number;
  wos: number;
  categoryA: number;
  categoryB: number;
}

export interface Point37TeacherBrief {
  teacherId: number;
  fullName: string;
  a1Diploma: boolean;
  a2Degree: boolean;
  a3Practical: boolean;
  a4Supervision: boolean;
  blockB: boolean;
  point37Compliant: boolean;
}

export interface DepartmentComplianceSummary {
  departmentId: number;
  /** Номер кафедри (наприклад "11", "22"). Не той самий що id. */
  departmentNumber?: string | null;
  departmentName: string;
  facultyName: string | null;
  totalTeachers: number;
  mainEmploymentTeachers: number;
  partTimeTeachers: number;
  withDegreeAndMainCount: number;
  withDegreeAndMainPercent: number;
  point35Compliant: boolean;
  doctorsOrProfessorsCount: number;
  doctorsOrProfessorsPercent: number;
  /** К-сть викладачів зі ступенем "Доктор філософії" / "Кандидат наук". */
  phdCount: number;
  /** К-сть викладачів зі ступенем "Доктор наук". */
  doctorOfScienceCount: number;
  /** К-сть викладачів зі званням Доцент / СНС / СНД. */
  docentOrSeniorResearcherCount: number;
  /** К-сть викладачів зі званням Професор (звання, не посада). */
  professorTitleCount: number;
  /** Закон про вищу освіту: ≥3 особи зі ступенем за спеціальністю кафедри. */
  defendedInSpecialtyCount: number;
  defendedInSpecialtyRequirement: number;
  defendedInSpecialtyCompliant: boolean;
  defendedInSpecialtyTeachers?: string[];
  point38Compliant: number;
  point38Warning: number;
  point38NonCompliant: number;
  point38Exempt: number;
  overallStatus: 'GOOD' | 'WARNING' | 'CRITICAL';
  /**
   * Detailed per-teacher compliance reports.
   * Порожній список у /departments/compliance-summary (slim payload для списку),
   * заповнений у /departments/{id}/compliance-summary (drill-in сторінка).
   */
  teacherReports?: ComplianceReport[];

  // ── Row 1: Основне місце роботи (MAIN) ──
  mainAllTeachers?: TeacherDegreeBrief[];
  mainTotalTeachers?: number;
  mainPhdTeachers?: TeacherDegreeBrief[];
  mainCandidateTeachers?: TeacherDegreeBrief[];
  mainDoctorOfScienceTeachers?: TeacherDegreeBrief[];
  mainDocentTeachers?: TeacherTitleBrief[];
  mainSnsTeachers?: TeacherTitleBrief[];
  mainSndTeachers?: TeacherTitleBrief[];
  mainProfessorTitleTeachers?: TeacherTitleBrief[];
  mainPoint38CompliantCount?: number;
  mainPoint38CompliantTeachers?: string[];

  // ── Row 2: Сумісники (PART_TIME) ──
  partTimeAllTeachers?: TeacherDegreeBrief[];
  partTimeTotalTeachers?: number;
  partTimePhdTeachers?: TeacherDegreeBrief[];
  partTimeCandidateTeachers?: TeacherDegreeBrief[];
  partTimeDoctorOfScienceTeachers?: TeacherDegreeBrief[];
  partTimeDocentTeachers?: TeacherTitleBrief[];
  partTimeSnsTeachers?: TeacherTitleBrief[];
  partTimeSndTeachers?: TeacherTitleBrief[];
  partTimeProfessorTitleTeachers?: TeacherTitleBrief[];
  partTimePoint38CompliantCount?: number;
  partTimePoint38CompliantTeachers?: string[];

  // ── Row 3: Додаткові метрики ──
  militaryCount?: number;
  militaryTeachers?: string[];
  civilianCount?: number;
  civilianTeachers?: string[];
  averageAgeYears?: number;
  vacantPositionsCount?: number;
  departmentRatingRank?: number;
  departmentRatingTotalDepts?: number;
  departmentRatingTotalScore?: number;
  publicationsByYear?: PublicationYearBucket[];

  // ── Укомплектованість штату ──
  totalStaffRate?: number;
  occupiedStaffRate?: number;
  staffingPercent?: number;

  // ── п.37 (MAIN) ──
  point37CompliantCount?: number;
  point37TeachersDetail?: Point37TeacherBrief[];
}

