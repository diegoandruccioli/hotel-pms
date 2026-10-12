/**
 * Deterministic mock data for the visual suite: fixed ids, names, amounts and dates (all
 * relative to FROZEN_DATE), no Date.now() or randomness. Shapes mirror src/types/*.
 */

export const FROZEN_DATE = '2026-10-08';

const day = (offset: number): string => {
  const d = new Date(`${FROZEN_DATE}T12:00:00Z`);
  d.setUTCDate(d.getUTCDate() + offset);
  return d.toISOString().slice(0, 10);
};

export const springPage = <T>(content: T[], size = 20) => ({
  content,
  totalElements: content.length,
  totalPages: 1,
  number: 0,
  size,
  numberOfElements: content.length,
  first: true,
  last: true,
  empty: content.length === 0,
});

export const ROOMS = [
  { id: 'rm-101', roomNumber: '101', floor: 1, status: 'DIRTY', roomType: { id: 'rt-1', name: 'Standard', basePrice: 80 }, hotelId: 'h-001', active: true },
  { id: 'rm-102', roomNumber: '102', floor: 1, status: 'CLEAN', roomType: { id: 'rt-1', name: 'Standard', basePrice: 80 }, hotelId: 'h-001', active: true },
  { id: 'rm-103', roomNumber: '103', floor: 1, status: 'OCCUPIED', roomType: { id: 'rt-1', name: 'Standard', basePrice: 80 }, hotelId: 'h-001', active: true },
  { id: 'rm-201', roomNumber: '201', floor: 2, status: 'MAINTENANCE', roomType: { id: 'rt-2', name: 'Suite', basePrice: 200 }, hotelId: 'h-001', active: true },
  { id: 'rm-202', roomNumber: '202', floor: 2, status: 'OCCUPIED', roomType: { id: 'rt-2', name: 'Suite', basePrice: 200 }, hotelId: 'h-001', active: true },
  { id: 'rm-203', roomNumber: '203', floor: 2, status: 'CLEAN', roomType: { id: 'rt-2', name: 'Suite', basePrice: 200 }, hotelId: 'h-001', active: true },
];

const reservation = (
  id: string,
  guestId: string,
  guestFullName: string,
  status: string,
  inOffset: number,
  outOffset: number,
  roomId: string | null,
) => ({
  id,
  guestId,
  guestFullName,
  checkInDate: day(inOffset),
  checkOutDate: day(outOffset),
  status,
  expectedGuests: 2,
  actualGuests: 0,
  active: true,
  lineItems: roomId ? [{ roomId, active: true }] : [],
});

export const RESERVATIONS = [
  reservation('res-001', 'g-001', 'Mario Rossi', 'CONFIRMED', 0, 2, 'rm-101'),
  reservation('res-002', 'g-002', 'Anna Bianchi', 'CHECKED_IN', -2, 1, 'rm-103'),
  reservation('res-003', 'g-003', 'Luca Verdi', 'PENDING', 1, 4, 'rm-203'),
  reservation('res-004', 'g-004', 'Giulia Neri', 'CHECKED_OUT', -5, -2, 'rm-102'),
  reservation('res-005', 'g-005', 'Paolo Gialli', 'CANCELLED', 2, 3, null),
  reservation('res-006', 'g-006', 'Sara Blu', 'NO_SHOW', -1, 1, 'rm-202'),
];

export const GUESTS = [
  ['g-001', 'Mario', 'Rossi', 'Roma'],
  ['g-002', 'Anna', 'Bianchi', 'Milano'],
  ['g-003', 'Luca', 'Verdi', 'Firenze'],
  ['g-004', 'Giulia', 'Neri', 'Torino'],
  ['g-005', 'Paolo', 'Gialli', 'Napoli'],
].map(([id, firstName, lastName, city]) => ({
  id,
  firstName,
  lastName,
  email: `${firstName.toLowerCase()}.${lastName.toLowerCase()}@example.com`,
  phone: '+39 333 1234567',
  city,
  country: 'IT',
  identityDocuments: [],
  createdAt: '2026-01-01T10:00:00',
  updatedAt: '2026-01-01T10:00:00',
}));

const invoice = (n: number, status: string, sdiStatus: string, documentType: string, amount: number) => ({
  id: `inv-00${n}`,
  hotelId: 'h-001',
  invoiceNumber: `2026/000${n}`,
  issueDate: `2026-10-0${n}T10:00:00`,
  totalAmount: amount,
  status,
  reservationId: `res-00${n}`,
  guestId: `g-00${n}`,
  stayId: `stay-00${n}`,
  documentType,
  sdiStatus,
  payments: [],
  charges: [],
});

export const INVOICES = [
  invoice(1, 'ISSUED', 'NOT_SENT', 'FATTURA', 320),
  invoice(2, 'PAID', 'ACCEPTED', 'FATTURA', 540),
  invoice(3, 'ISSUED', 'SENT', 'FATTURA', 180),
  invoice(4, 'ISSUED', 'REJECTED', 'FATTURA', 95),
  invoice(5, 'CANCELLED', 'NOT_SENT', 'RICEVUTA', 60),
].map((inv, i) => ({ invoice: inv, guestName: GUESTS[i]?.lastName ? `${GUESTS[i].lastName} ${GUESTS[i].firstName}` : null }));

export const DAY_SHEET = {
  date: FROZEN_DATE,
  todayArrivals: 1,
  todayDepartures: 1,
  guestsInHouse: 4,
  currentStays: 2,
  availableRooms: 2,
  roomStatusCounts: { CLEAN: 2, DIRTY: 1, MAINTENANCE: 1, OCCUPIED: 2 },
};

export const TREND_POINTS = [4, 5, 3, 6, 7, 5, 6].map((guestsInHouse, i) => ({
  date: day(i - 6),
  arrivals: 1 + (i % 3),
  departures: 1 + ((i + 1) % 3),
  guestsInHouse,
  availableRooms: 8 - (i % 4),
}));

export const OWNER_SUMMARY = {
  startDate: day(-30),
  endDate: FROZEN_DATE,
  totalRevenue: 18450,
  totalInvoices: 42,
  paidInvoices: 37,
  pendingRevenue: 2310,
};

export const DUE_OUT_STAYS = [
  {
    id: 'stay-002',
    reservationId: 'res-002',
    guestId: 'g-002',
    roomId: 'rm-103',
    status: 'CHECKED_IN',
    createdAt: '2026-10-06T10:00:00',
    updatedAt: '2026-10-06T10:00:00',
    alloggiatiSent: false,
    alloggiatiSendFailed: false,
    guestDisplayName: 'Bianchi Anna',
    roomNumber: '103',
    expectedCheckOutDate: day(0),
    invoiceCreationFailed: false,
    checkoutEmailFailed: false,
  },
];
