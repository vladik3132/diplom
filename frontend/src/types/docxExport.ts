export interface ColumnHeader {
  columnIndex: number;
  headerText: string;
}

export interface DataField {
  key: string;
  label: string;
  descriptionHint: string;
  group: string;
}

export interface ColumnMapping {
  columnIndex: number;
  headerText: string;
  fieldKeys: string[];
}

export interface DocxExportTemplate {
  id: number;
  name: string;
  templateFileName: string;
  originalFileName?: string;
  columnMappingsJson: string;
  tableIndex?: number;
  headerRowCount?: number;
  createdAt?: string;
  updatedAt?: string;
}

export interface TemplateUploadResponse {
  templateFileName: string;
  originalFileName: string;
  columns: ColumnHeader[];
}

/** Group labels for the data fields dropdown */
export const DATA_FIELD_GROUP_LABELS: Record<string, string> = {
  service: '⚙ Службові',
  preset: '📦 Комбіновані (пресети)',
  teacher: '👤 Викладач: Основна інформація',
  teacher_edu: '🎓 Викладач: Освіта та наука (primary)',
  teacher_edu_all: '🎓 Викладач: Усі ступені/звання + регалії',
  teacher_combat: '⚔ Викладач: Бойовий досвід',
  teacher_contacts: '📧 Викладач: Контакти',
  discipline: '📚 Дисципліни',
  career: '💼 Послужний список',
  language: '🌐 Іноземні мови',
  qualification: '📜 Підвищення кваліфікації',
  publication: '📄 Публікації',
  achievement: '🏆 Досягнення (п.38)',
};
