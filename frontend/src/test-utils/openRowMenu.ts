import { fireEvent, screen } from '@testing-library/react';

/**
 * Opens the row-actions menu of the n-th row (0-based). The trigger is named by the
 * `row_actions_for` key under a key-returning `t` mock, or "Actions for …" under real i18n.
 */
export function openRowMenu(index = 0): void {
  const triggers = screen.getAllByRole('button', { name: /row_actions_for|^Actions for / });
  fireEvent.click(triggers[index] as HTMLElement);
}
