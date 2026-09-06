import api from '../services/api';

const IFRAME_CLEANUP_DELAY_MS = 10000;

/**
 * Triggers the browser's native download for a server endpoint via a hidden iframe.
 *
 * Deliberately NOT fetch+Blob+synthetic-<a>-click: that pattern was verified end-to-end
 * (network 200, valid bytes, anchor configured correctly, click() invoked) yet produced no
 * visible file save in real Chrome — a known failure mode for synthetic clicks on blob:
 * URLs (silently dropped by some browsers/extensions, no JS-visible error). A hidden iframe
 * pointed straight at the endpoint relies on the server's `Content-Disposition: attachment`
 * header to trigger the OS-level native download, the same mechanism as a real <a href>
 * click — and keeps any non-download error response (e.g. a 500) contained inside the
 * iframe instead of navigating the SPA away.
 *
 * An iframe `src` navigation is a raw browser request: it carries the httpOnly access-token
 * cookie, but it never goes through `api`'s Axios response interceptor — so unlike every
 * other call in the app, it cannot benefit from the interceptor's silent 401-then-refresh-
 * then-retry (T-AUTH-04). Once the short-lived access token expired, every export/download
 * in the app failed with a bare, unrecoverable 401 no matter how valid the user's session
 * otherwise was (found in live QA, 2026-09: an export that worked moments after login broke
 * a few minutes later with no visible error). Awaiting a cheap authenticated GET through
 * `api` first — thrown away, its only purpose is to run through the interceptor — refreshes
 * the token when needed before the iframe fires with a cookie that's now guaranteed fresh.
 * If the session is truly gone (refresh itself fails), `api`'s interceptor already redirects
 * to `/login`; this rejects the same way so callers don't fire an iframe pointed at a session
 * that's ending anyway.
 */
export const downloadViaIframe = async (
  url: string,
  cleanupDelayMs: number = IFRAME_CLEANUP_DELAY_MS,
): Promise<void> => {
  await api.get('/api/v1/auth/me');
  const iframe = document.createElement('iframe');
  iframe.style.display = 'none';
  iframe.src = url;
  document.body.appendChild(iframe);
  setTimeout(() => document.body.removeChild(iframe), cleanupDelayMs);
};
