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

const VI_DATE_FORMATTER = new Intl.DateTimeFormat('vi-VN');

const VI_DATETIME_FORMATTER = new Intl.DateTimeFormat('vi-VN', {
  dateStyle: 'short',
  timeStyle: 'short',
});

const VI_MEDIUM_DATETIME_FORMATTER = new Intl.DateTimeFormat('vi-VN', {
  dateStyle: 'medium',
  timeStyle: 'short',
});

/** Formats an ISO date or YYYY-MM-DD string into Vietnamese dd/MM/yyyy format. */
export function formatClinicDate(value?: string | null): string {
  if (!value) return '';
  const trimmed = value.trim();
  if (!trimmed) return '';
  if (/^\d{4}-\d{2}-\d{2}$/.test(trimmed)) {
    const [year, month, day] = trimmed.split('-').map(Number);
    return VI_DATE_FORMATTER.format(new Date(year, month - 1, day));
  }
  const date = new Date(trimmed);
  return isNaN(date.getTime()) ? trimmed : VI_DATE_FORMATTER.format(date);
}

/** Formats an ISO date or YYYY-MM-DD string into padded dd/MM/yyyy format. */
export function formatClinicPadDate(value?: string | null): string {
  if (!value) return '';
  const trimmed = value.trim();
  if (!trimmed) return '';
  if (/^\d{4}-\d{2}-\d{2}$/.test(trimmed)) {
    const [year, month, day] = trimmed.split('-');
    return `${day}/${month}/${year}`;
  }
  const date = new Date(trimmed);
  if (isNaN(date.getTime())) return trimmed;
  const d = String(date.getDate()).padStart(2, '0');
  const m = String(date.getMonth() + 1).padStart(2, '0');
  return `${d}/${m}/${date.getFullYear()}`;
}

/** Formats a time string (HH:mm:ss or HH:mm) into HH:mm. */
export function formatClinicTime(value?: string | null): string {
  if (!value) return '';
  return value.trim().slice(0, 5);
}

/** Formats a timestamp into short Vietnamese date & time string. */
export function formatClinicDateTime(value?: string | null): string {
  if (!value) return '';
  const date = new Date(value);
  return isNaN(date.getTime()) ? value : VI_DATETIME_FORMATTER.format(date);
}

/** Formats a timestamp into medium Vietnamese date & time string. */
export function formatClinicMediumDateTime(value?: string | null): string {
  if (!value) return '';
  const date = new Date(value);
  return isNaN(date.getTime()) ? value : VI_MEDIUM_DATETIME_FORMATTER.format(date);
}
