export const DEFAULT_VALID_DAYS = 7;
export const MAX_OPTIONS = 5;

export interface OptionDraft {
  label: string;
  selectedRoomIds: string[];
}

export const defaultOptionLabel = (index: number) => `Opzione ${index + 1}`;
