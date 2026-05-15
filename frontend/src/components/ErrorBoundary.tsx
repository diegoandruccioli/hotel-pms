import { Component, type CSSProperties, type ErrorInfo, type ReactNode } from 'react';

const ICON_STYLE: CSSProperties = { fontSize: 48 };

interface Props {
  children: ReactNode;
}

interface State {
  hasError: boolean;
  error: Error | null;
}

export class ErrorBoundary extends Component<Props, State> {
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
    if (this.state.hasError) {
      return (
        <div
          role="alert"
          className="flex h-full min-h-[60vh] items-center justify-center bg-surface p-6"
        >
          <div className="flex max-w-sm flex-col items-center gap-4 rounded-2xl border border-outline-variant bg-surface-container p-8 text-center shadow-elevation-1">
            <span className="material-symbols-outlined text-error" style={ICON_STYLE}>
              error
            </span>
            <h2 className="text-lg font-display font-semibold text-on-surface">
              Si è verificato un errore inatteso
            </h2>
            <p className="text-sm font-body text-on-surface-variant">
              {this.state.error?.message ?? 'An unexpected error occurred.'}
            </p>
            <button
              type="button"
              onClick={this.handleReload}
              className="mt-2 rounded-full bg-primary px-6 py-2 text-sm font-medium text-on-primary hover:bg-primary/90 focus:outline-none focus:ring-2 focus:ring-primary focus:ring-offset-2"
            >
              Ricarica pagina
            </button>
          </div>
        </div>
      );
    }
    return this.props.children;
  }
}
