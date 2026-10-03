import { Component, type ErrorInfo, type ReactNode } from 'react';
import { withTranslation, type WithTranslation } from 'react-i18next';
import { MaterialIcon } from './MaterialIcon';
import { M3Button } from './m3';

interface Props extends WithTranslation {
  children: ReactNode;
}

interface State {
  hasError: boolean;
  error: Error | null;
}

/**
 * Class component — cannot use the useTranslation hook, wrapped with the
 * withTranslation HOC instead (exported below as ErrorBoundary).
 */
class ErrorBoundaryBase extends Component<Props, State> {
  constructor(props: Props) {
    super(props);
    this.state = { hasError: false, error: null };
  }

  static getDerivedStateFromError(error: Error): State {
    return { hasError: true, error };
  }

  componentDidCatch(error: Error, info: ErrorInfo): void {
    console.error('[ErrorBoundary] Unhandled render error:', error, info.componentStack);
  }

  private handleReload = (): void => {
    window.location.reload();
  };

  render(): ReactNode {
    const { t } = this.props;
    if (this.state.hasError) {
      return (
        <div
          role="alert"
          className="flex h-full min-h-[60vh] items-center justify-center bg-surface p-6"
        >
          <div className="flex max-w-sm flex-col items-center gap-4 rounded-2xl border border-outline-variant bg-surface-container p-8 text-center shadow-elevation-1">
            <MaterialIcon name="error" size={48} className="text-error" />
            <h2 className="text-lg font-display font-semibold text-on-surface">
              {t('error_unexpected_title')}
            </h2>
            <p className="text-sm font-body text-on-surface-variant">
              {this.state.error?.message || t('error_unexpected_fallback')}
            </p>
            <M3Button type="button" onClick={this.handleReload} className="mt-2">
              {t('error_reload_button')}
            </M3Button>
          </div>
        </div>
      );
    }
    return this.props.children;
  }
}

export const ErrorBoundary = withTranslation('common')(ErrorBoundaryBase);
