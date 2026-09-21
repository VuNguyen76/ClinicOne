export const DEFAULT_DOCTOR_AVATAR =
  'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 120 120"><circle cx="60" cy="60" r="60" fill="%23e0f2fe"/><circle cx="60" cy="44" r="20" fill="%230284c7"/><path d="M60 70c-22 0-38 14-38 34v16h76v-16c0-20-16-34-38-34z" fill="%23ffffff"/><path d="M48 70l12 22 12-22" fill="%230284c7"/><path d="M42 84c0 10 8 18 18 18s18-8 18-18" fill="none" stroke="%23334155" stroke-width="3" stroke-linecap="round"/><circle cx="60" cy="102" r="3.5" fill="%230284c7"/></svg>';

export function cleanDoctorName(name: string | null | undefined): string {
  if (!name) return '';
  return name
    .replace(/^((bs\.|ths\.|ckii|cki|bác sĩ|tiến sĩ|ts\.)\s*)+/i, '')
    .trim()
    .toLowerCase();
}

export function matchesDoctorIdentity(params: {
  myStaffId?: string | null;
  myName?: string | null;
  doctorId?: string | null;
  doctorName?: string | null;
}): boolean {
  const { myStaffId, myName, doctorId, doctorName } = params;
  if (myStaffId && doctorId) {
    return myStaffId === doctorId;
  }
  const cleanMy = cleanDoctorName(myName);
  const cleanTarget = cleanDoctorName(doctorName);
  if (cleanMy && cleanTarget) {
    return cleanMy === cleanTarget;
  }
  return false;
}

export function resolveDoctorAvatar(avatarUrl?: string | null): string {
  if (avatarUrl && avatarUrl.trim().length > 0) {
    return avatarUrl.trim();
  }
  return DEFAULT_DOCTOR_AVATAR;
}
