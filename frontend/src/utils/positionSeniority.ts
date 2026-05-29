/**
 * Канонічний словник старшинства академічних посад (синхронізований з
 * `backend/.../common/PositionSeniority.java`).
 *
 * Менший ранг = вища посада. Невідома посада → UNKNOWN_RANK = 99.
 *
 * ВАЖЛИВО: порядок ключів має значення — `Object.entries(...)` повертає їх
 * у порядку вставки. Тому довші специфічні ключі («Старший науковий
 * співробітник») мають йти ПЕРЕД коротшими («Науковий співробітник»),
 * інакше у `rankOf` коротший ключ матчиться першим.
 */

export const UNKNOWN_POSITION_RANK = 99;

const SENIORITY: Record<string, number> = {
  'Начальник кафедри': 1,
  'Заступник начальника кафедри': 2,
  'Завідувач кафедри': 1,
  'Декан': 1,
  'Заступник декана': 2,
  'Професор': 3,
  // Має йти ПЕРЕД 'Науковий співробітник' — інакше будь-який "старший науковий"
  // буде матчитись на "науковий співробітник" і отримає ранг 7 замість 4.
  'Старший науковий співробітник': 4,
  'Доцент': 4,
  'Старший викладач': 5,
  'Викладач': 6,
  'Науковий співробітник': 7,
  'Асистент': 8,
};

/**
 * Повертає ранг посади за її назвою. Case-insensitive includes-match.
 * Якщо назва порожня або не матчиться жодним ключем — UNKNOWN_POSITION_RANK.
 */
export function rankOfPosition(positionTitle: string | null | undefined): number {
  if (!positionTitle) return UNKNOWN_POSITION_RANK;
  const lower = positionTitle.toLowerCase();
  for (const [key, rank] of Object.entries(SENIORITY)) {
    if (lower.includes(key.toLowerCase())) {
      return rank;
    }
  }
  return UNKNOWN_POSITION_RANK;
}
