export interface FileAttachment {
  id: number;
  entityType: string;
  entityId: number;
  driveFileId?: string;
  localPath?: string;
  originalName: string;
  mimeType: string;
  fileSize: number;
  uploadedBy?: string;
  uploadedAt?: string;
}

export const ENTITY_TYPE_LABELS: Record<string, string> = {
  PUBLICATION: 'Публікація',
  QUALIFICATION_IMPROVEMENT: 'Підвищення кваліфікації',
  LANGUAGE_SKILL: 'Мовний сертифікат',
  TEACHER_PHOTO: 'Фото викладача',
  DISCIPLINE_DOCUMENT: 'Документ дисципліни',
  EDITORIAL_PLAN: 'Видавничий план',
  TEACHER_UNIVERSITY_DIPLOMA: 'Диплом про освіту',
  TEACHER_DEGREE_DIPLOMA: 'Диплом ступеня',
  TEACHER_TITLE_ATTESTAT: 'Атестат звання',
  TEACHER_COMBAT_VETERAN_DOC: 'Посвідчення УБД',
  PP_SCIENTIFIC_SUPERVISION: 'Наукове керівництво',
  PP_ATTESTATION_ACTIVITY: 'Атестаційна діяльність',
  PP_EDITORIAL_ACTIVITY: 'Редакційна діяльність',
  PP_EXPERT_COUNCIL: 'Експертні ради',
  PP_INTERNATIONAL_PROJECT: 'Міжнародні проєкти',
  PP_SCIENTIFIC_CONSULTING: 'Наукове консультування',
  PP_FOREIGN_LANGUAGE_TEACHING: 'Іноземна мова викладання',
  PP_OLYMPIAD_GUIDANCE: 'Олімпіади/конкурси',
  PP_MILITARY_MISSION: 'Миротворчі місії',
  PP_PROFESSIONAL_ASSOCIATION: 'Професійні об\'єднання',
  PP_PRACTICAL_EXPERIENCE: 'Практичний досвід',
};
