import { describe, it, expect } from 'vitest';
import { AxiosError } from 'axios';
import { getErrorMessage } from './errorMessage';

function axiosErrorWithDetail(detail: unknown): AxiosError {
  return new AxiosError('Request failed with status code 409', 'ERR_BAD_REQUEST', undefined, undefined, {
    status: 409,
    statusText: 'Conflict',
    headers: {},
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    config: {} as any,
    data: { detail },
  });
}

describe('getErrorMessage', () => {
  it('BUG-4: prefers the backend detail over the generic Axios status message', () => {
    const err = axiosErrorWithDetail('Non puoi effettuare il checkout: fattura non ancora pagata');
    expect(getErrorMessage(err, 'fallback')).toBe('Non puoi effettuare il checkout: fattura non ancora pagata');
  });

  it('falls back when detail is missing', () => {
    const err = axiosErrorWithDetail(undefined);
    expect(getErrorMessage(err, 'fallback')).toBe('fallback');
  });

  it('falls back when detail is blank', () => {
    const err = axiosErrorWithDetail('   ');
    expect(getErrorMessage(err, 'fallback')).toBe('fallback');
  });

  it('falls back for a non-Axios error', () => {
    expect(getErrorMessage(new Error('boom'), 'fallback')).toBe('fallback');
  });

  it('falls back for a non-error value', () => {
    expect(getErrorMessage('boom', 'fallback')).toBe('fallback');
  });
});
