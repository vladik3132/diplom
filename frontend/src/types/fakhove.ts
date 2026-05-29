export type JournalCategory = 'CATEGORY_A' | 'CATEGORY_B';

export interface FakhovyiJournal {
  id: number;
  name: string;
  founders?: string;
  specialtyCodes?: string;
  inclusionDate?: string;
  category?: JournalCategory;
  nameNormalized?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface VerificationResult {
  isFakhove: boolean;
  category?: JournalCategory;
  isScopus: boolean;
  matchedFakhoveName?: string;
  matchedScopusName?: string;
}

export const JOURNAL_CATEGORY_LABELS: Record<JournalCategory, string> = {
  CATEGORY_A: 'Категорія А',
  CATEGORY_B: 'Категорія Б',
};
