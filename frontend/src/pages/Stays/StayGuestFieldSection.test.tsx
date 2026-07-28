import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { axe } from 'vitest-axe';
import { GuestFieldSection } from './StayGuestFieldSection';
import { emptyGuest } from './stayGuestFieldHelpers';
import { stayService } from '../../services/stayService';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, opts?: Record<string, unknown>) => {
      if (opts && typeof opts === 'object') {
        return Object.entries(opts).reduce(
          (s, [k, v]) => s.replace(`{{${k}}}`, String(v)),
          key,
        );
      }
      return key;
    },
  }),
  initReactI18next: { type: '3rdParty', init: vi.fn() },
}));

vi.mock('../../services/stayService');

const ITALIA_STATO = { codice: '100000100', descrizione: 'ITALIA' };
const FRANCIA_STATO = { codice: '109000100', descrizione: 'FRANCIA' };
const STATI = [ITALIA_STATO, FRANCIA_STATO];
const PASOR_TIPDOC = { codice: 'PASOR', descrizione: 'PASSAPORTO ORDINARIO' };
const TIPDOC = [PASOR_TIPDOC];
const FIANO_COMUNE = { codice: '412058036', descrizione: 'FIANO ROMANO', provincia: 'RM' };
const GUEST_WITH_DOC = { ...emptyGuest(false), documentType: 'PASOR', documentNumber: 'X123' };
const GUEST_WITHOUT_DOC_TYPE = { ...emptyGuest(false), travellerType: 'FAMILIARE' as const };
const GUEST_ITALIAN_BORN = { ...emptyGuest(true), _statoDiNascita: '100000100' };

describe('GuestFieldSection', () => {
  const onChange = vi.fn();
  const onRemove = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(stayService.searchLookupComuni).mockResolvedValue([FIANO_COMUNE]);
  });

  it('renders the base required fields', () => {
    render(
      <GuestFieldSection
        guest={emptyGuest(true)}
        index={0}
        canRemove={false}
        stati={STATI}
        tipdoc={TIPDOC}
        onRemove={onRemove}
        onChange={onChange}
      />,
    );
    expect(screen.getByLabelText(/label_first_name/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/label_last_name/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/label_gender/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/label_date_of_birth/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/label_guest_type/i)).toBeInTheDocument();
  });

  it('shows the primary badge only when the guest is primary', () => {
    const { rerender } = render(
      <GuestFieldSection
        guest={emptyGuest(true)}
        index={0}
        canRemove={false}
        stati={STATI}
        tipdoc={TIPDOC}
        onRemove={onRemove}
        onChange={onChange}
      />,
    );
    expect(screen.getByText('guest_badge_primary')).toBeInTheDocument();

    rerender(
      <GuestFieldSection
        guest={emptyGuest(false)}
        index={1}
        canRemove
        stati={STATI}
        tipdoc={TIPDOC}
        onRemove={onRemove}
        onChange={onChange}
      />,
    );
    expect(screen.queryByText('guest_badge_primary')).not.toBeInTheDocument();
  });

  it('shows the remove button only when canRemove is true, and calls onRemove with the index', () => {
    const { rerender } = render(
      <GuestFieldSection
        guest={emptyGuest(false)}
        index={2}
        canRemove={false}
        stati={STATI}
        tipdoc={TIPDOC}
        onRemove={onRemove}
        onChange={onChange}
      />,
    );
    expect(screen.queryByText('btn_remove')).not.toBeInTheDocument();

    rerender(
      <GuestFieldSection
        guest={emptyGuest(false)}
        index={2}
        canRemove
        stati={STATI}
        tipdoc={TIPDOC}
        onRemove={onRemove}
        onChange={onChange}
      />,
    );
    fireEvent.click(screen.getByText('btn_remove'));
    expect(onRemove).toHaveBeenCalledWith(2);
  });

  it('calls onChange with the field name/value on simple text field edits', () => {
    render(
      <GuestFieldSection
        guest={emptyGuest(true)}
        index={0}
        canRemove={false}
        stati={STATI}
        tipdoc={TIPDOC}
        onRemove={onRemove}
        onChange={onChange}
      />,
    );
    fireEvent.change(screen.getByLabelText(/label_first_name/i), { target: { value: 'Mario' } });
    expect(onChange).toHaveBeenCalledWith(0, { firstName: 'Mario' });
  });

  it('clears document fields when switching to a traveller type without a document', () => {
    render(
      <GuestFieldSection
        guest={GUEST_WITH_DOC}
        index={1}
        canRemove
        stati={STATI}
        tipdoc={TIPDOC}
        onRemove={onRemove}
        onChange={onChange}
      />,
    );
    fireEvent.change(screen.getByLabelText(/label_guest_type/i), { target: { value: 'FAMILIARE' } });
    expect(onChange).toHaveBeenCalledWith(1, {
      travellerType: 'FAMILIARE', documentType: '', documentNumber: '', documentPlaceOfIssue: '',
    });
  });

  it('keeps document fields untouched when switching between document-requiring types', () => {
    render(
      <GuestFieldSection
        guest={emptyGuest(true)}
        index={0}
        canRemove={false}
        stati={STATI}
        tipdoc={TIPDOC}
        onRemove={onRemove}
        onChange={onChange}
      />,
    );
    fireEvent.change(screen.getByLabelText(/label_guest_type/i), { target: { value: 'CAPOFAMIGLIA' } });
    expect(onChange).toHaveBeenCalledWith(0, { travellerType: 'CAPOFAMIGLIA' });
  });

  it('does not render document fields for a traveller type without a document', () => {
    render(
      <GuestFieldSection
        guest={GUEST_WITHOUT_DOC_TYPE}
        index={1}
        canRemove
        stati={STATI}
        tipdoc={TIPDOC}
        onRemove={onRemove}
        onChange={onChange}
      />,
    );
    expect(screen.queryByLabelText(/label_doc_number/i)).not.toBeInTheDocument();
  });

  it('reveals the birth-municipality autocomplete only when born in Italy, and looks it up from the server', async () => {
    render(
      <GuestFieldSection
        guest={emptyGuest(true)}
        index={0}
        canRemove={false}
        stati={STATI}
        tipdoc={TIPDOC}
        onRemove={onRemove}
        onChange={onChange}
      />,
    );
    expect(screen.queryByLabelText(/label_comune_nascita/i)).not.toBeInTheDocument();

    const statoNascita = screen.getByLabelText(/label_stato_nascita/i);
    fireEvent.change(statoNascita, { target: { value: 'ITALIA' } });
    const option = await screen.findByRole('option', { name: /ITALIA/ });
    fireEvent.mouseDown(option);

    expect(onChange).toHaveBeenCalledWith(0, { _statoDiNascita: '100000100', placeOfBirth: '' });
  });

  it('sets placeOfBirth directly to the stato codice when born outside Italy', async () => {
    render(
      <GuestFieldSection
        guest={emptyGuest(true)}
        index={0}
        canRemove={false}
        stati={STATI}
        tipdoc={TIPDOC}
        onRemove={onRemove}
        onChange={onChange}
      />,
    );
    const statoNascita = screen.getByLabelText(/label_stato_nascita/i);
    fireEvent.change(statoNascita, { target: { value: 'FRANCIA' } });
    const option = await screen.findByRole('option', { name: /FRANCIA/ });
    fireEvent.mouseDown(option);

    expect(onChange).toHaveBeenCalledWith(0, { _statoDiNascita: '109000100', placeOfBirth: '109000100' });
  });

  it('searches and selects a birth municipality when born in Italy', async () => {
    render(
      <GuestFieldSection
        guest={GUEST_ITALIAN_BORN}
        index={0}
        canRemove={false}
        stati={STATI}
        tipdoc={TIPDOC}
        onRemove={onRemove}
        onChange={onChange}
      />,
    );
    const comune = screen.getByLabelText(/label_comune_nascita/i);
    fireEvent.change(comune, { target: { value: 'Fiano' } });

    await waitFor(() => expect(stayService.searchLookupComuni).toHaveBeenCalledWith('Fiano'), { timeout: 5000 });
    const option = await screen.findByRole('option', { name: /FIANO ROMANO/ }, { timeout: 5000 });
    fireEvent.mouseDown(option);

    expect(onChange).toHaveBeenCalledWith(0, { placeOfBirth: '412058036' });
  });

  it('toggles the primary-guest checkbox', () => {
    render(
      <GuestFieldSection
        guest={emptyGuest(false)}
        index={1}
        canRemove
        stati={STATI}
        tipdoc={TIPDOC}
        onRemove={onRemove}
        onChange={onChange}
      />,
    );
    fireEvent.click(screen.getByLabelText(/label_primary_guest/i));
    expect(onChange).toHaveBeenCalledWith(1, { isPrimaryGuest: true });
  });

  it('has no accessibility violations', async () => {
    const { container } = render(
      <GuestFieldSection
        guest={emptyGuest(true)}
        index={0}
        canRemove={false}
        stati={STATI}
        tipdoc={TIPDOC}
        onRemove={onRemove}
        onChange={onChange}
      />,
    );
    const results = await axe(container);
    expect(results).toHaveNoViolations();
  });
});
