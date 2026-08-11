/**
 * Calendar helpers shared by patient and staff screens.
 *
 * Business dates in ClinicOne belong to the clinic's configured timezone,
 * not to the timezone of the device that happens to open the browser.
 */
export const CLINIC_TIME_ZONE = 'Asia/Ho_Chi_Minh';

export function clinicTodayIso(now: Date = new Date()): string {
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: CLINIC_TIME_ZONE,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).formatToParts(now);
  const values = Object.fromEntries(parts.map((part) => [part.type, part.value]));
  return `${values['year']}-${values['month']}-${values['day']}`;
}

/** Creates a local Date representing the clinic's current calendar date. */
export function clinicTodayDate(now: Date = new Date()): Date {
  const [year, month, day] = clinicTodayIso(now).split('-').map(Number);
  return new Date(year, month - 1, day);
}
