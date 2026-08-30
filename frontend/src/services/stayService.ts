import api from './api';
import type {
  AlloggiatiComune,
  AlloggiatiFailureSummaryResponse,
  AlloggiatiStato,
  AlloggiatiTipdoc,
  AvailableRoom,
  CityTaxApplicabilityRequest,
  CityTaxApplicabilityResponse,
  CityTaxBackfillResponse,
  CityTaxConfigurationStatusResponse,
  CityTaxRateRequest,
  CityTaxRateResponse,
  CityTaxUnassessedSummaryResponse,
  HotelCategoryHistoryRequest,
  HotelCategoryHistoryResponse,
  HotelSettingsRequest,
  HotelSettingsResponse,
  StayGuestRequest,
  StayGuestResponse,
  StayRequest,
  StayResponse,
} from '../types/stay.types';
import type { SpringPage } from '../types/page.types';

const BASE_PATH = '/api/v1/stays';
const IFRAME_CLEANUP_DELAY_MS = 10000;

export const stayService = {
  getAllStays: async (page = 0, size = 20): Promise<SpringPage<StayResponse>> => {
    const response = await api.get<SpringPage<StayResponse>>(
      `${BASE_PATH}?page=${page}&size=${size}&sort=actualCheckInTime,desc`,
    );
    return response.data;
  },

  getStayById: async (id: string): Promise<StayResponse> => {
    const response = await api.get<StayResponse>(`${BASE_PATH}/${id}`);
    return response.data;
  },

  getStaysByReservationId: async (reservationId: string): Promise<SpringPage<StayResponse>> => {
    const response = await api.get<SpringPage<StayResponse>>(
      `${BASE_PATH}?reservationId=${reservationId}`,
    );
    return response.data;
  },

  createStay: async (data: StayRequest): Promise<StayResponse> => {
    const response = await api.post<StayResponse>(BASE_PATH, data);
    return response.data;
  },

  checkOut: async (id: string): Promise<StayResponse> => {
    const response = await api.put<StayResponse>(`${BASE_PATH}/${id}/check-out`, {});
    return response.data;
  },

  retryInvoiceCreation: async (id: string): Promise<StayResponse> => {
    const response = await api.post<StayResponse>(`${BASE_PATH}/${id}/invoice/retry`, {});
    return response.data;
  },

  retryCheckoutEmail: async (id: string): Promise<StayResponse> => {
    const response = await api.post<StayResponse>(`${BASE_PATH}/${id}/checkout-email/retry`, {});
    return response.data;
  },

  getHotelSettings: async (): Promise<HotelSettingsResponse> => {
    const response = await api.get<HotelSettingsResponse>(`${BASE_PATH}/settings`);
    return response.data;
  },

  updateHotelSettings: async (data: HotelSettingsRequest): Promise<HotelSettingsResponse> => {
    const response = await api.put<HotelSettingsResponse>(`${BASE_PATH}/settings`, data);
    return response.data;
  },

  /**
   * Triggers the browser's native download for the Alloggiati Web fixed-width report
   * via a hidden iframe.
   *
   * Deliberately NOT fetch+Blob+synthetic-<a>-click+immediate-revokeObjectURL: that
   * exact pattern is documented in billingService.ts's downloadPdf as verified-broken
   * in real Chrome (produced no visible file save — a known failure mode for synthetic
   * clicks on blob: URLs, silently dropped, no JS-visible error) and was replaced there
   * with a hidden iframe pointed straight at the endpoint, relying on the server's
   * `Content-Disposition: attachment` header to trigger the native download. Mirrored
   * here for the same reason — this download was verified to silently fail with the
   * old pattern during the 2026-08-24 QA pass (REPORT.md §6 #3b).
   */
  /**
   * The hidden iframe below can't report its own HTTP status back to the
   * caller — unlike billingService.downloadFatturaPAXml (which validates
   * first, THEN downloads), this used to fire the iframe and let the caller
   * show a success toast unconditionally, regardless of whether the request
   * behind it actually succeeded. A failed Alloggiati report (wrong
   * credentials, malformed guest data, a backend 5xx) would still show
   * "Report downloaded" — silently hiding a TULPS art. 109 compliance
   * failure from hotel staff. Awaiting a real GET first (same endpoint,
   * thrown away) surfaces any error through the caller's existing catch
   * block before the iframe — and the caller's `await` already expected a
   * promise, so this needed no caller-side change.
   */
  downloadAlloggiatiReport: async (date: string): Promise<void> => {
    await api.get(`${BASE_PATH}/reports/alloggiati`, { params: { date } });
    const iframe = document.createElement('iframe');
    iframe.style.display = 'none';
    iframe.src = `${BASE_PATH}/reports/alloggiati?date=${encodeURIComponent(date)}`;
    document.body.appendChild(iframe);
    setTimeout(() => document.body.removeChild(iframe), IFRAME_CLEANUP_DELAY_MS);
  },

  getLastCompletedStayForGuest: async (guestId: string): Promise<StayResponse | null> => {
    const response = await api.get<StayResponse>(`${BASE_PATH}/guest/${guestId}/latest`, {
      validateStatus: (s) => s === 200 || s === 204,
    });
    return response.status === 204 ? null : response.data;
  },

  /** Same validate-then-download pattern as downloadAlloggiatiReport above — see its comment. */
  downloadAlloggiatiJson: async (date: string): Promise<void> => {
    await api.get(`${BASE_PATH}/reports/alloggiati/json`, { params: { date } });
    const iframe = document.createElement('iframe');
    iframe.style.display = 'none';
    iframe.src = `${BASE_PATH}/reports/alloggiati/json?date=${encodeURIComponent(date)}`;
    document.body.appendChild(iframe);
    setTimeout(() => document.body.removeChild(iframe), IFRAME_CLEANUP_DELAY_MS);
  },

  getLookupStati: async (): Promise<AlloggiatiStato[]> => {
    const response = await api.get<AlloggiatiStato[]>(`${BASE_PATH}/lookup/stati`);
    return response.data;
  },

  searchLookupComuni: async (q: string, provincia?: string): Promise<AlloggiatiComune[]> => {
    const response = await api.get<AlloggiatiComune[]>(`${BASE_PATH}/lookup/comuni`, {
      params: { q, provincia },
    });
    return response.data;
  },

  submitAlloggiatiReport: async (date: string): Promise<void> => {
    await api.post(`${BASE_PATH}/reports/alloggiati/submit`, null, { params: { date } });
  },

  getLookupTipdoc: async (): Promise<AlloggiatiTipdoc[]> => {
    const response = await api.get<AlloggiatiTipdoc[]>(`${BASE_PATH}/lookup/tipdoc`);
    return response.data;
  },

  getAlloggiatiFailureSummary: async (): Promise<AlloggiatiFailureSummaryResponse> => {
    const response = await api.get<AlloggiatiFailureSummaryResponse>(
      `${BASE_PATH}/reports/alloggiati/failures/summary`,
    );
    return response.data;
  },

  getAvailableRooms: async (): Promise<AvailableRoom[]> => {
    const response = await api.get<{ content: AvailableRoom[] }>('/api/v1/rooms', {
      params: { size: 200 },
    });
    return (response.data.content ?? []).filter((r) => r.status === 'CLEAN');
  },

  // E18: tourist-tax rate rules and hotel category history — both append-only
  // (list + create, no update/delete), ADMIN/OWNER-only writes enforced server-side.

  getHotelCategoryHistory: async (): Promise<HotelCategoryHistoryResponse[]> => {
    const response = await api.get<HotelCategoryHistoryResponse[]>(`${BASE_PATH}/hotel-category`);
    return response.data;
  },

  recordHotelCategory: async (data: HotelCategoryHistoryRequest): Promise<HotelCategoryHistoryResponse> => {
    const response = await api.post<HotelCategoryHistoryResponse>(`${BASE_PATH}/hotel-category`, data);
    return response.data;
  },

  getCityTaxRates: async (): Promise<CityTaxRateResponse[]> => {
    const response = await api.get<CityTaxRateResponse[]>(`${BASE_PATH}/city-tax-rates`);
    return response.data;
  },

  createCityTaxRate: async (data: CityTaxRateRequest): Promise<CityTaxRateResponse> => {
    const response = await api.post<CityTaxRateResponse>(`${BASE_PATH}/city-tax-rates`, data);
    return response.data;
  },

  getCityTaxApplicability: async (): Promise<CityTaxApplicabilityResponse> => {
    const response = await api.get<CityTaxApplicabilityResponse>(`${BASE_PATH}/city-tax-rates/applicability`);
    return response.data;
  },

  updateCityTaxApplicability: async (data: CityTaxApplicabilityRequest): Promise<CityTaxApplicabilityResponse> => {
    const response = await api.put<CityTaxApplicabilityResponse>(`${BASE_PATH}/city-tax-rates/applicability`, data);
    return response.data;
  },

  /** Pre-flight check the check-in form calls before submitting — RECEPTIONIST included. */
  getCityTaxConfigurationStatus: async (): Promise<CityTaxConfigurationStatusResponse> => {
    const response = await api.get<CityTaxConfigurationStatusResponse>(`${BASE_PATH}/city-tax/configuration-status`);
    return response.data;
  },

  getCityTaxUnassessedSummary: async (): Promise<CityTaxUnassessedSummaryResponse> => {
    const response = await api.get<CityTaxUnassessedSummaryResponse>(`${BASE_PATH}/city-tax/unassessed/summary`);
    return response.data;
  },

  previewCityTaxBackfill: async (): Promise<CityTaxBackfillResponse> => {
    const response = await api.get<CityTaxBackfillResponse>(`${BASE_PATH}/city-tax/backfill/preview`);
    return response.data;
  },

  confirmCityTaxBackfill: async (): Promise<CityTaxBackfillResponse> => {
    const response = await api.post<CityTaxBackfillResponse>(`${BASE_PATH}/city-tax/backfill/confirm`);
    return response.data;
  },

  addGuest: async (stayId: string, data: StayGuestRequest): Promise<StayGuestResponse> => {
    const response = await api.post<StayGuestResponse>(`${BASE_PATH}/${stayId}/guests`, data);
    return response.data;
  },

  updateGuest: async (stayId: string, guestId: string, data: StayGuestRequest): Promise<StayGuestResponse> => {
    const response = await api.put<StayGuestResponse>(`${BASE_PATH}/${stayId}/guests/${guestId}`, data);
    return response.data;
  },

  removeGuest: async (stayId: string, guestId: string): Promise<void> => {
    await api.delete(`${BASE_PATH}/${stayId}/guests/${guestId}`);
  },

  recordGuestDeparture: async (stayId: string, guestId: string, departureDate: string): Promise<StayGuestResponse> => {
    const response = await api.put<StayGuestResponse>(
      `${BASE_PATH}/${stayId}/guests/${guestId}/departure`,
      { departureDate },
    );
    return response.data;
  },

  promoteGuestToPrimary: async (stayId: string, guestId: string): Promise<StayGuestResponse> => {
    const response = await api.put<StayGuestResponse>(`${BASE_PATH}/${stayId}/guests/${guestId}/primary`);
    return response.data;
  },

  extendStay: async (stayId: string, newCheckOutDate: string): Promise<StayResponse> => {
    const response = await api.put<StayResponse>(`${BASE_PATH}/${stayId}`, { newCheckOutDate });
    return response.data;
  },
};
