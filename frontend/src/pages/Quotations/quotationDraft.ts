import type { TFunction } from 'i18next';

export const DEFAULT_VALID_DAYS = 7;
export const MAX_OPTIONS = 5;

export interface OptionDraft {
  label: string;
  selectedRoomIds: string[];
}

/**
 * Name shown for an option: the one typed by the user, or "Option N" / "Opzione N" in the UI
 * language. The draft keeps an unnamed option as '' so the default follows the language.
 */
export const optionDisplayName = (label: string, index: number, t: TFunction): string =>
  label.trim() || t('quotations:option_default_name', { number: index + 1 });
