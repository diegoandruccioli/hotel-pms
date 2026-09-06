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
 */
export const downloadViaIframe = (url: string): void => {
  const iframe = document.createElement('iframe');
  iframe.style.display = 'none';
  iframe.src = url;
  document.body.appendChild(iframe);
  setTimeout(() => document.body.removeChild(iframe), IFRAME_CLEANUP_DELAY_MS);
};
