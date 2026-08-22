import '@testing-library/jest-dom/vitest';
import 'vitest-axe/extend-expect';
import * as matchers from 'vitest-axe/matchers';
import { expect } from 'vitest';

expect.extend(matchers);

class ResizeObserverStub {
  observe() {}
  unobserve() {}
  disconnect() {}
}
window.ResizeObserver = window.ResizeObserver ?? ResizeObserverStub;

// jsdom doesn't implement scrollIntoView; cmdk calls it to keep the
// highlighted item in view as arrow keys move selection.
Element.prototype.scrollIntoView = Element.prototype.scrollIntoView ?? (() => {});

// jsdom doesn't implement EventSource. This inert stub (never connects,
// never fires) is enough for every test that just renders MainLayout and
// doesn't care about the realtime stream; useServerEvents.test.ts installs
// its own richer mock to actually exercise message handling.
class EventSourceStub {
  onmessage: ((event: MessageEvent<string>) => void) | null = null;
  onerror: ((event: Event) => void) | null = null;
  close() {}
}
window.EventSource = window.EventSource ?? (EventSourceStub as unknown as typeof EventSource);

Object.defineProperty(window, 'matchMedia', {
  writable: true,
  value: (query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: () => {},
    removeListener: () => {},
    addEventListener: () => {},
    removeEventListener: () => {},
    dispatchEvent: () => false,
  }),
});

interface CustomMatchers<R = unknown> {
  toHaveNoViolations(): R;
}

declare module 'vitest' {
  // eslint-disable-next-line @typescript-eslint/no-empty-object-type, @typescript-eslint/no-explicit-any
  interface Assertion<T = any> extends CustomMatchers<T> {}
  // eslint-disable-next-line @typescript-eslint/no-empty-object-type
  interface AsymmetricMatchersContaining extends CustomMatchers {}
}
