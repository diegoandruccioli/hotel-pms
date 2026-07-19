import { useState, useCallback } from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { axe } from 'vitest-axe';
import { StructuredAddressFields } from './StructuredAddressFields';
import { stayService } from '../services/stayService';
import type { AlloggiatiComune } from '../types/stay.types';

interface ControlledWrapperProps {
  onComuneChange?: (v: string) => void;
  onProvinciaChange?: (v: string) => void;
}

/** Feeds onComuneChange/onProvinciaChange back into the controlled props so
 * typing multiple characters (and selecting an option) actually accumulates,
 * while still letting the test spy on both callbacks. */
const noopCapChange = () => undefined;

const ControlledWrapper = ({ onComuneChange, onProvinciaChange }: ControlledWrapperProps) => {
  const [comune, setComune] = useState('');
  const [provincia, setProvincia] = useState('');
  const handleComuneChange = useCallback((v: string) => {
    setComune(v);
    onComuneChange?.(v);
  }, [onComuneChange]);
  const handleProvinciaChange = useCallback((v: string) => {
    setProvincia(v);
    onProvinciaChange?.(v);
  }, [onProvinciaChange]);
  return (
    <StructuredAddressFields
      idPrefix="test"
      cap=""
      comune={comune}
      provincia={provincia}
      onCapChange={noopCapChange}
      onComuneChange={handleComuneChange}
      onProvinciaChange={handleProvinciaChange}
    />
  );
};

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'en' } }),
  initReactI18next: { type: '3rdParty', init: vi.fn() },
}));

vi.mock('../services/stayService');

const ROMA: AlloggiatiComune = { codice: '058091', descrizione: 'Roma', provincia: 'RM' };

const noop = () => undefined;

describe('StructuredAddressFields', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(stayService.searchLookupComuni).mockResolvedValue([ROMA]);
  });

  it('renders CAP, Comune and Provincia inputs with the given values', () => {
    render(
      <StructuredAddressFields
        idPrefix="test"
        cap="00100"
        comune="Roma"
        provincia="RM"
        onCapChange={noop}
        onComuneChange={noop}
        onProvinciaChange={noop}
      />,
    );

    expect(screen.getByLabelText('label_cap')).toHaveValue('00100');
    expect(screen.getByLabelText('label_comune')).toHaveValue('Roma');
    expect(screen.getByLabelText('label_provincia')).toHaveValue('RM');
  });

  it('calls onCapChange when typing in the CAP field', async () => {
    const user = userEvent.setup();
    const onCapChange = vi.fn();
    render(
      <StructuredAddressFields
        idPrefix="test"
        cap=""
        comune=""
        provincia=""
        onCapChange={onCapChange}
        onComuneChange={noop}
        onProvinciaChange={noop}
      />,
    );

    await user.type(screen.getByLabelText('label_cap'), '1');
    expect(onCapChange).toHaveBeenCalledWith('1');
  });

  it('uppercases Provincia input as the user types', async () => {
    const user = userEvent.setup();
    const onProvinciaChange = vi.fn();
    render(
      <StructuredAddressFields
        idPrefix="test"
        cap=""
        comune=""
        provincia=""
        onCapChange={noop}
        onComuneChange={noop}
        onProvinciaChange={onProvinciaChange}
      />,
    );

    await user.type(screen.getByLabelText('label_provincia'), 'r');
    expect(onProvinciaChange).toHaveBeenCalledWith('R');
  });

  it('searches comuni and selects one, filling Comune and Provincia together', async () => {
    const user = userEvent.setup();
    const onComuneChange = vi.fn();
    const onProvinciaChange = vi.fn();
    render(<ControlledWrapper onComuneChange={onComuneChange} onProvinciaChange={onProvinciaChange} />);

    await user.type(screen.getByLabelText('label_comune'), 'Ro');
    await waitFor(
      () => expect(stayService.searchLookupComuni).toHaveBeenCalledWith('Ro'),
      { timeout: 2000 },
    );
    const option = await screen.findByRole('option', { name: /Roma/ });
    await user.click(option);

    expect(onComuneChange).toHaveBeenCalledWith('Roma');
    expect(onProvinciaChange).toHaveBeenCalledWith('RM');
  });

  it('should have no accessibility violations', async () => {
    const { container } = render(
      <StructuredAddressFields
        idPrefix="test"
        cap="00100"
        comune="Roma"
        provincia="RM"
        onCapChange={noop}
        onComuneChange={noop}
        onProvinciaChange={noop}
      />,
    );
    const results = await axe(container);
    expect(results).toHaveNoViolations();
  }, 30000);
});
