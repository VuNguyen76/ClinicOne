import { DEFAULT_DOCTOR_AVATAR, cleanDoctorName, matchesDoctorIdentity, resolveDoctorAvatar } from './doctor-utils';

describe('doctor-utils', () => {
  describe('cleanDoctorName', () => {
    it('removes Vietnamese doctor titles and trims', () => {
      expect(cleanDoctorName('BS. Nguyễn An')).toBe('nguyễn an');
      expect(cleanDoctorName('ThS. Bác sĩ Lê Thu Hà')).toBe('lê thu hà');
      expect(cleanDoctorName('CKII Trần Minh')).toBe('trần minh');
      expect(cleanDoctorName('Tiến sĩ Hoàng Thanh Nga')).toBe('hoàng thanh nga');
      expect(cleanDoctorName('TS. Vũ Đình Toàn')).toBe('vũ đình toàn');
      expect(cleanDoctorName('')).toBe('');
      expect(cleanDoctorName(null)).toBe('');
    });
  });

  describe('matchesDoctorIdentity', () => {
    it('matches by staff ID strictly when staffId is available', () => {
      expect(matchesDoctorIdentity({
        myStaffId: 'staff-123',
        doctorId: 'staff-123',
        myName: 'BS. An',
        doctorName: 'BS. An',
      })).toBe(true);

      expect(matchesDoctorIdentity({
        myStaffId: 'staff-123',
        doctorId: 'staff-456',
        myName: 'BS. An',
        doctorName: 'BS. An',
      })).toBe(false);
    });

    it('falls back to exact cleaned name matching without false positive substring matching', () => {
      // "An" should NOT match "Đặng Mai Lan" or "Hoàng Thanh Nga"
      expect(matchesDoctorIdentity({
        myName: 'BS. Nguyễn An',
        doctorName: 'Bác sĩ Nguyễn An',
      })).toBe(true);

      expect(matchesDoctorIdentity({
        myName: 'BS. Nguyễn An',
        doctorName: 'BS. Đặng Mai Lan',
      })).toBe(false);

      expect(matchesDoctorIdentity({
        myName: 'BS. An',
        doctorName: 'BS. Hoàng Thanh Nga',
      })).toBe(false);
    });
  });

  describe('resolveDoctorAvatar', () => {
    it('returns custom avatarUrl when provided', () => {
      expect(resolveDoctorAvatar('https://clinic.com/avatar.jpg')).toBe('https://clinic.com/avatar.jpg');
    });

    it('returns neutral default SVG avatar when avatarUrl is missing or empty', () => {
      expect(resolveDoctorAvatar(null)).toBe(DEFAULT_DOCTOR_AVATAR);
      expect(resolveDoctorAvatar('')).toBe(DEFAULT_DOCTOR_AVATAR);
      expect(resolveDoctorAvatar('   ')).toBe(DEFAULT_DOCTOR_AVATAR);
    });
  });
});
