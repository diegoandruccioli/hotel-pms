import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { axe } from 'vitest-axe';
import { ErrorBoundary } from './ErrorBoundary';

const { mockT } = vi.hoisted(() => ({ mockT: (key: string) => key }));

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: mockT, i18n: { language: 'en' } }),
  withTranslation: () => (WrappedComponent: React.ComponentType<{ t: (key: string) => string }>) => {
    const Wrapped = (props: object) => <WrappedComponent {...props} t={mockT} />;
    Wrapped.displayName = 'WithTranslation';
    return Wrapped;
  },
  initReactI18next: { type: '3rdParty', init: vi.fn() },
}));

const ThrowingChild = ({ shouldThrow }: { shouldThrow: boolean }) => {
  if (shouldThrow) throw new Error('Test render error');
  return <div data-testid="child-ok">OK</div>;
};

describe('ErrorBoundary', () => {
  let consoleErrorSpy: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    consoleErrorSpy = vi.spyOn(console, 'error').mockImplementation(() => undefined);
  });

  afterEach(() => {
    consoleErrorSpy.mockRestore();
  });

  it('renders children normally when no error thrown', () => {
    render(
      <ErrorBoundary>
        <ThrowingChild shouldThrow={false} />
      </ErrorBoundary>,
    );
    expect(screen.getByTestId('child-ok')).toBeInTheDocument();
  });

  it('shows fallback UI when child throws', () => {
    render(
      <ErrorBoundary>
        <ThrowingChild shouldThrow />
      </ErrorBoundary>,
    );
    expect(screen.getByRole('alert')).toBeInTheDocument();
    expect(screen.getByText('error_unexpected_title')).toBeInTheDocument();
    expect(screen.getByText('Test render error')).toBeInTheDocument();
  });

  it('shows the translated fallback message when the error has no message', () => {
    const ThrowingNoMessage = () => {
      throw new Error();
    };
    render(
      <ErrorBoundary>
        <ThrowingNoMessage />
      </ErrorBoundary>,
    );
    expect(screen.getByText('error_unexpected_fallback')).toBeInTheDocument();
  });

  it('fallback has a reload button', () => {
    const reloadMock = vi.fn();
    Object.defineProperty(window, 'location', {
      value: { reload: reloadMock },
      writable: true,
    });

    render(
      <ErrorBoundary>
        <ThrowingChild shouldThrow />
      </ErrorBoundary>,
    );
    fireEvent.click(screen.getByText('error_reload_button'));
    expect(reloadMock).toHaveBeenCalledOnce();
  });

  it('logs the error to console.error', () => {
    render(
      <ErrorBoundary>
        <ThrowingChild shouldThrow />
      </ErrorBoundary>,
    );
    expect(consoleErrorSpy).toHaveBeenCalled();
  });

  it('should have no accessibility violations in the fallback UI', async () => {
    const { container } = render(
      <ErrorBoundary>
        <ThrowingChild shouldThrow />
      </ErrorBoundary>,
    );
    const results = await axe(container);
    expect(results).toHaveNoViolations();
  });
});
