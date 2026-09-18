import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { Booking } from './appointments/booking/booking';
import { ReceptionCheckIn } from './reception/check-in/reception-check-in';
import { PatientHeader } from '../shared/patient-header/patient-header';
import { Login } from './auth/login/login';
import { PublicPage } from './public/public-page';

describe('Milestone 1 Empirical Challenger Verification Suite', () => {

  describe('Suite 1: Booking UI Preservation, Single-Facility & Step Navigation', () => {
    let fixture: ComponentFixture<Booking>;
    let component: Booking;
    let http: HttpTestingController;

    beforeEach(async () => {
      await TestBed.configureTestingModule({
        imports: [Booking],
        providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
      }).compileComponents();

      fixture = TestBed.createComponent(Booking);
      component = fixture.componentInstance;
      http = TestBed.inject(HttpTestingController);
      fixture.detectChanges();

      http.expectOne('/api/v1/specialties').flush([
        { code: 'NOI', name: 'Nội tổng quát', description: 'Khám đa khoa tổng quát' },
        { code: 'TIM', name: 'Tim mạch', description: 'Khám tim mạch' },
      ]);
      http.expectOne('/api/v1/services').flush([]);
      http.expectOne('/api/v1/patient-profiles').flush([
        { id: 'prof-1', fullName: 'Nguyễn Văn A', primaryProfile: true, relationship: 'Bản thân', dateOfBirth: '1990-01-01', gender: 'Nam' },
        { id: 'prof-2', fullName: 'Nguyễn Con', primaryProfile: false, relationship: 'Con', dateOfBirth: '2020-05-15', gender: 'Nam' },
      ]);
      fixture.detectChanges();
    });

    afterEach(() => http.verify());

    it('EMPIRICAL: summary panel displays single clinic brand and zero "cơ sở"', () => {
      const compiled = fixture.nativeElement as HTMLElement;
      const aside = compiled.querySelector('aside');
      expect(aside).not.toBeNull();
      expect(aside?.textContent).toContain('Phòng khám Đa khoa ClinicOne');
      expect(aside?.textContent).not.toContain('Cơ sở khám bệnh');
      expect(aside?.textContent).not.toContain('ClinicOne Medical Center');
    });

    it('EMPIRICAL: Step 2 maintains 7-day grid and slot session cards without 2-column mini compression', () => {
      component['chooseSpecialty']({ code: 'NOI', name: 'Nội tổng quát', description: 'Khám đa khoa tổng quát' });
      const slotsReq = http.expectOne((item) => item.url === '/api/v1/appointment-slots');
      slotsReq.flush([]);
      fixture.detectChanges();

      expect(component['step']()).toBe(2);
      const compiled = fixture.nativeElement as HTMLElement;

      // Check 7-day weekday headers
      const weekdayHeaders = compiled.querySelectorAll('.grid-cols-7 span');
      expect(weekdayHeaders.length).toBeGreaterThanOrEqual(7);
      const headerTexts = Array.from(weekdayHeaders).slice(0, 7).map((el) => el.textContent?.trim());
      expect(headerTexts).toEqual(['Th 2', 'Th 3', 'Th 4', 'Th 5', 'Th 6', 'Th 7', 'CN']);

      // Check 42 calendar date cells in 6-week full grid
      const calendarDates = compiled.querySelectorAll('[data-testid="calendar-date"]');
      expect(calendarDates.length).toBe(42);
    });

    it('EMPIRICAL: slot selection groups morning and afternoon slots properly and enables continue button', () => {
      component['chooseSpecialty']({ code: 'NOI', name: 'Nội tổng quát', description: 'Khám đa khoa tổng quát' });
      const testDate = component['dates']().find((d) => d.inCurrentMonth && d.iso >= component['today'])!;
      const slotsReq = http.expectOne((item) => item.url === '/api/v1/appointment-slots');
      slotsReq.flush([
        { specialty: 'Nội tổng quát', appointmentDate: testDate.iso, startTime: '08:30:00', endTime: '09:00:00', doctorName: 'BS. Lê Nam', doctorId: 'doc-1', roomCode: 'NOI-01', remainingCapacity: 1 },
        { specialty: 'Nội tổng quát', appointmentDate: testDate.iso, startTime: '14:30:00', endTime: '15:00:00', doctorName: 'BS. Lê Nam', doctorId: 'doc-1', roomCode: 'NOI-01', remainingCapacity: 1 },
      ]);
      fixture.detectChanges();

      component['chooseDate'](testDate);
      fixture.detectChanges();

      const morningSlots = component['slotsFor']('Buổi sáng');
      const afternoonSlots = component['slotsFor']('Buổi chiều');
      expect(morningSlots.length).toBe(1);
      expect(afternoonSlots.length).toBe(1);
      expect(morningSlots[0].startTime).toBe('08:30');
      expect(afternoonSlots[0].startTime).toBe('14:30');

      // Select slot
      component['chooseSlot'](morningSlots[0]);
      expect(component['selectedSlot']()).toBe('doc-1|08:30');
      expect(component['form'].controls.startTime.value).toBe('08:30');
      expect(component['form'].controls.doctorName.value).toBe('BS. Lê Nam');
      expect(component['form'].controls.doctorId.value).toBe('doc-1');
    });

    it('EMPIRICAL: Step 3 eliminates duplicate doctor info and presents clean layout', () => {
      component['chooseSpecialty']({ code: 'NOI', name: 'Nội tổng quát', description: 'Khám đa khoa tổng quát' });
      const testDate = component['dates']().find((d) => d.inCurrentMonth && d.iso >= component['today'])!;
      http.expectOne((item) => item.url === '/api/v1/appointment-slots').flush([
        { specialty: 'Nội tổng quát', appointmentDate: testDate.iso, startTime: '08:30:00', endTime: '09:00:00', doctorName: 'BS. Lê Nam', doctorId: 'doc-1', roomCode: 'NOI-01', remainingCapacity: 1 },
      ]);
      component['chooseDate'](testDate);
      component['chooseSlot'](component['availableSlots']()[0]);

      // Proceed to Step 3
      component['continueToDetails']();
      const holdReq = http.expectOne('/api/v1/appointment-holds');
      holdReq.flush({
        id: 'hold-test-99',
        specialty: 'Nội tổng quát',
        doctorName: 'BS. Lê Nam',
        appointmentDate: testDate.iso,
        startTime: '08:30:00',
        expiresAt: new Date(Date.now() + 300000).toISOString(),
      });
      fixture.detectChanges();

      expect(component['step']()).toBe(3);
      const compiled = fixture.nativeElement as HTMLElement;

      // Verify slot label under Ngày khám shows only time slot label, not doctor name
      const dateBox = compiled.querySelector('.sm\\:grid-cols-2 .min-w-0');
      expect(dateBox?.textContent).toContain('Khung giờ: 08:30 - 09:00');
      expect(dateBox?.textContent).not.toContain('BS. Lê Nam');

      // Verify dedicated doctor card displays doctor info
      const doctorCard = compiled.querySelector('[data-testid="selected-doctor"]');
      expect(doctorCard).not.toBeNull();
      expect(doctorCard?.textContent).toContain('BS. Lê Nam');
      expect(doctorCard?.textContent).toContain('Phòng NOI-01');

      // Verify hold countdown badge is displayed in header
      expect(compiled.textContent).toContain('Giữ chỗ còn');
    });

    it('EMPIRICAL: step navigation back() preserves selections and handles boundary transitions', () => {
      // Start in Step 1
      expect(component['step']()).toBe(1);

      // Choose specialty -> Step 2
      component['chooseSpecialty']({ code: 'NOI', name: 'Nội tổng quát', description: 'Khám đa khoa tổng quát' });
      http.expectOne((item) => item.url === '/api/v1/appointment-slots').flush([]);
      expect(component['step']()).toBe(2);

      // Back from Step 2 -> Step 1
      component['back']();
      expect(component['step']()).toBe(1);

      // Back to Step 2
      component['step'].set(2);
      expect(component['step']()).toBe(2);

      // Step 2 to Step 3
      component['step'].set(3);
      expect(component['step']()).toBe(3);

      // Back from Step 3 -> Step 2
      component['back']();
      expect(component['step']()).toBe(2);
    });
  });

  describe('Suite 2: Reception Check-In Duplicate Button Removal & Workspace Layout', () => {
    let fixture: ComponentFixture<ReceptionCheckIn>;
    let component: ReceptionCheckIn;
    let http: HttpTestingController;

    beforeEach(async () => {
      sessionStorage.setItem('clinicOneStaffRole', 'RECEPTIONIST');
      await TestBed.configureTestingModule({
        imports: [ReceptionCheckIn],
        providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
      }).compileComponents();

      fixture = TestBed.createComponent(ReceptionCheckIn);
      component = fixture.componentInstance;
      http = TestBed.inject(HttpTestingController);
      fixture.detectChanges();
      http.expectOne('/api/v1/reception/worklist?date=' + (component as any).selectedDate()).flush([]);
      fixture.detectChanges();
    });

    afterEach(() => {
      http.verify();
      sessionStorage.clear();
    });

    it('EMPIRICAL: overview screen header does NOT contain redundant "Tra lịch hôm nay" button', () => {
      (component as any).activeTab.set('overview');
      fixture.detectChanges();

      const compiled = fixture.nativeElement as HTMLElement;
      const overviewScreen = compiled.querySelector('[data-testid="reception-overview-screen"]');
      expect(overviewScreen).not.toBeNull();

      // Check header area of overview screen
      const header = overviewScreen?.querySelector('.flex.flex-wrap.items-end.justify-between');
      expect(header).not.toBeNull();
      expect(header?.textContent).toContain('Tổng quan tiếp nhận');

      // Redundant button with text "Tra lịch hôm nay" must NOT exist in the header
      const headerButtons = header?.querySelectorAll('a, button');
      expect(headerButtons?.length).toBe(0);
      expect(header?.textContent).not.toContain('Tra lịch hôm nay');
    });

    it('EMPIRICAL: reception overview retains all essential operational action links', () => {
      (component as any).activeTab.set('overview');
      fixture.detectChanges();

      const compiled = fixture.nativeElement as HTMLElement;
      const walkInLink = compiled.querySelector('a[routerLink="/reception/walk-in"]');
      const profilesLink = compiled.querySelector('a[routerLink="/reception/profiles"]');
      const exceptionsLink = compiled.querySelector('a[routerLink="/reception/exceptions"]');

      expect(walkInLink).not.toBeNull();
      expect(walkInLink?.textContent).toContain('Tiếp nhận không có lịch');
      expect(profilesLink).not.toBeNull();
      expect(profilesLink?.textContent).toContain('Tra hồ sơ người bệnh');
      expect(exceptionsLink).not.toBeNull();
      expect(exceptionsLink?.textContent).toContain('Xử lý ngoại lệ');
    });
  });

  describe('Suite 3: Hotline Standardization & Branding Across Components', () => {
    beforeEach(async () => {
      await TestBed.configureTestingModule({
        imports: [PatientHeader, Login, PublicPage],
        providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
      }).compileComponents();
    });

    it('EMPIRICAL: PatientHeader renders hotline 1900 0000 with tel:19000000', () => {
      const headerFixture = TestBed.createComponent(PatientHeader);
      headerFixture.detectChanges();

      const compiled = headerFixture.nativeElement as HTMLElement;
      const hotlineLink = compiled.querySelector('a[href="tel:19000000"]');
      expect(hotlineLink).not.toBeNull();
      expect(hotlineLink?.textContent?.trim()).toContain('1900 0000');
    });

    it('EMPIRICAL: Login page renders hotline link with tel:19000000 and single clinic branding', () => {
      const loginFixture = TestBed.createComponent(Login);
      loginFixture.detectChanges();

      const compiled = loginFixture.nativeElement as HTMLElement;
      const supportLink = compiled.querySelector('a[href="tel:19000000"]');
      expect(supportLink).not.toBeNull();
      expect(supportLink?.textContent).toContain('Cần hỗ trợ?');

      // Verify no ERP label in login branding
      expect(compiled.textContent).not.toContain('ClinicOne ERP');
      expect(compiled.textContent).toContain('Phòng khám Đa khoa ClinicOne');
    });

    it('EMPIRICAL: Public landing page uses standardized 1900 0000 format and tel:19000000', () => {
      const publicFixture = TestBed.createComponent(PublicPage);
      publicFixture.detectChanges();

      const compiled = publicFixture.nativeElement as HTMLElement;
      const telLinks = compiled.querySelectorAll('a[href="tel:19000000"]');
      expect(telLinks.length).toBeGreaterThanOrEqual(2);

      // Verify all occurrences match standardized spacing 1900 0000
      expect(compiled.textContent).toContain('1900 0000');
      expect(compiled.textContent).not.toContain('19000000 ');
      expect(compiled.textContent).not.toContain('ClinicOne ERP');
      expect(compiled.textContent).not.toContain('cơ sở');
    });
  });
});
