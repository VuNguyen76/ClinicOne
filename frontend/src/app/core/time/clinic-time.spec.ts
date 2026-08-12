import { clinicTodayDate, clinicTodayIso } from './clinic-time';

describe('clinic calendar helpers', () => {
  it('uses Vietnam time even when the browser instant is near midnight UTC', () => {
    expect(clinicTodayIso(new Date('2026-08-10T17:30:00.000Z'))).toBe('2026-08-11');
  });

  it('returns a date object for the clinic calendar day', () => {
    const date = clinicTodayDate(new Date('2026-08-10T17:30:00.000Z'));
    expect([date.getFullYear(), date.getMonth() + 1, date.getDate()]).toEqual([2026, 8, 11]);
  });
});
