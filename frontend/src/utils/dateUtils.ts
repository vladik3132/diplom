import dayjs from 'dayjs';
import customParseFormat from 'dayjs/plugin/customParseFormat';

dayjs.extend(customParseFormat);

/** Стандартний формат дати */
export const DATE_FORMAT = 'DD.MM.YYYY';

/**
 * Парсить дату з рядка — підтримує DD.MM.YYYY та YYYY-MM-DD.
 * Повертає dayjs об'єкт або undefined.
 */
export function parseDate(value: string | null | undefined): dayjs.Dayjs | undefined {
  if (!value) return undefined;
  // dd.MM.yyyy
  let d = dayjs(value, DATE_FORMAT, true);
  if (d.isValid()) return d;
  // ISO
  d = dayjs(value, 'YYYY-MM-DD', true);
  if (d.isValid()) return d;
  // fallback
  d = dayjs(value);
  return d.isValid() ? d : undefined;
}

/**
 * Форматує дату для відображення.
 */
export function formatDate(value: string | null | undefined): string {
  if (!value) return '—';
  const d = parseDate(value);
  return d ? d.format(DATE_FORMAT) : '—';
}

/**
 * Конвертує всі ISO-дати (yyyy-MM-dd) в тексті на dd.MM.yyyy.
 * "2020-06-30–2020-11-20" → "30.06.2020–20.11.2020"
 */
export function formatDatesInText(text: string | null | undefined): string {
  if (!text) return '—';
  return text.replace(/\d{4}-\d{2}-\d{2}/g, (match) => {
    const d = dayjs(match, 'YYYY-MM-DD', true);
    return d.isValid() ? d.format(DATE_FORMAT) : match;
  });
}
