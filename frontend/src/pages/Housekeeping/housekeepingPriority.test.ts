import { describe, it, expect } from 'vitest';
import { priorityRoomNumbers } from './housekeepingPriority';
import type { HousekeepingRow } from '../../types';

const row = (overrides: Partial<HousekeepingRow>): HousekeepingRow => ({
  roomNumber: '101', roomTypeName: 'Standard', taskType: 'VACANT_DIRTY', currentStatus: 'DIRTY', pax: 0,
  expectedDepartureDate: null, turnover: false, departureDateUnknown: false, ...overrides,
});

describe('priorityRoomNumbers', () => {
  it('flags a same-day turnover', () => {
    expect(priorityRoomNumbers([row({ roomNumber: '201', taskType: 'DEPARTURE', turnover: true })]).has('201')).toBe(true);
  });

  it('flags a room being prepared for an arrival', () => {
    expect(priorityRoomNumbers([row({ roomNumber: '202', taskType: 'ARRIVAL_PREP' })]).has('202')).toBe(true);
  });

  it('does not flag departures without turnover, stayovers, vacant or maintenance rooms', () => {
    const rows = [
      row({ roomNumber: '1', taskType: 'DEPARTURE' }),
      row({ roomNumber: '2', taskType: 'STAYOVER' }),
      row({ roomNumber: '3', taskType: 'VACANT_DIRTY' }),
      row({ roomNumber: '4', taskType: 'MAINTENANCE' }),
    ];
    expect(priorityRoomNumbers(rows).size).toBe(0);
  });

  it('returns an empty set for an empty worksheet', () => {
    expect(priorityRoomNumbers([]).size).toBe(0);
  });
});
