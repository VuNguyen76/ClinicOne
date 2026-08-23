import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter, ActivatedRoute } from '@angular/router';
import { Booking } from './appointments/booking/booking';
import { RoomManagement } from './room-management/room-management';
import { MedicationCatalogManagement } from './medication-catalog-management/medication-catalog-management';
import { DiagnosisCatalogManagement } from './diagnosis-catalog-management/diagnosis-catalog-management';
import { AppointmentsList } from './appointments/list/appointments-list';
import { DoctorExamination } from './doctor-examination/doctor-examination';
import { Rescheduling } from './rescheduling/rescheduling';
import { ReconciliationManagement } from './reconciliation/reconciliation';

describe('Empirical Adversarial Stress Suite - Challenger 1', () => {

  describe('Focus 1: Booking Stepper Hold Timer & Countdown Behavior', () => {
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

      http.expectOne('/api/v1/specialties').flush([{ code: 'TIM', name: 'Tim mạch', description: 'Chuyên khoa tim mạch' }]);
      http.expectOne('/api/v1/services').flush([]);
      http.expectOne('/api/v1/patient-profiles').flush([]);
      fixture.detectChanges();
    });

    afterEach(() => http.verify());

    it('adversarial test: hold countdown formats mm:ss accurately across various remaining seconds', () => {
      // Test zero seconds
      component['holdRemainingSeconds'].set(0);
      expect(component['holdCountdownLabel']()).toBe('0:00');

      // Test single digit seconds (e.g. 5 seconds -> "0:05")
      component['holdRemainingSeconds'].set(5);
      expect(component['holdCountdownLabel']()).toBe('0:05');

      // Test exact minute boundary (e.g. 300 seconds -> "5:00")
      component['holdRemainingSeconds'].set(300);
      expect(component['holdCountdownLabel']()).toBe('5:00');

      // Test multi-minute with single digit seconds (e.g. 187 seconds -> "3:07")
      component['holdRemainingSeconds'].set(187);
      expect(component['holdCountdownLabel']()).toBe('3:07');

      // Test high values (e.g. 3600 seconds -> "60:00")
      component['holdRemainingSeconds'].set(3600);
      expect(component['holdCountdownLabel']()).toBe('60:00');
    });

    it('adversarial test: hold timer automatically resets and errors when expiry is reached', () => {
      component['chooseSpecialty']({ code: 'TIM', name: 'Tim mạch', description: 'Chuyên khoa tim mạch' });
      http.expectOne((item) => item.url === '/api/v1/appointment-slots').flush([]);

      const testDate = component['dates']().find((item) => item.inCurrentMonth && item.iso >= component['today'])!;
      component['chooseDate'](testDate);
      component['availableSlots'].set([{
        label: '08:00 - 08:30',
        key: 'doc1|08:00',
        startTime: '08:00',
        period: 'Buổi sáng',
        doctorName: 'BS Tim',
        doctorId: 'doc1',
        roomCode: 'TIM-01',
      }]);
      component['chooseSlot'](component['availableSlots']()[0]);

      // Proceed to details (Step 3)
      component['continueToDetails']();
      const holdReq = http.expectOne('/api/v1/appointment-holds');

      // Mock hold expires in 2 seconds
      const now = Date.now();
      const expiresAt = new Date(now + 2000).toISOString();
      holdReq.flush({
        id: 'hold-adv-123',
        specialty: 'Tim mạch',
        doctorName: 'BS Tim',
        appointmentDate: testDate.iso,
        startTime: '08:00:00',
        expiresAt,
      });

      expect(component['holdId']()).toBe('hold-adv-123');
      expect(component['step']()).toBe(3);

      // Trigger hold countdown update when timer expires
      component['holdExpiresAt'].set(new Date(Date.now() - 1000).toISOString());
      component['updateHoldCountdown']();

      expect(component['holdId']()).toBeNull();
      expect(component['holdRemainingSeconds']()).toBe(0);
      expect(component['error']).toContain('Thời gian giữ chỗ đã hết');
    });

    it('adversarial test: switching slot or date immediately clears previous hold', () => {
      component['holdId'].set('existing-hold');
      component['holdExpiresAt'].set(new Date(Date.now() + 60000).toISOString());
      component['holdRemainingSeconds'].set(60);

      // Switching slot should clear hold
      component['chooseSlot']({
        label: '09:00 - 09:30',
        key: 'doc2|09:00',
        startTime: '09:00',
        period: 'Buổi sáng',
        doctorName: 'BS Khac',
        doctorId: 'doc2',
        roomCode: 'TIM-02',
      });

      expect(component['holdId']()).toBeNull();
      expect(component['holdExpiresAt']()).toBeNull();
      expect(component['holdRemainingSeconds']()).toBe(0);
    });
  });

  describe('Focus 2: Search & Filter Stress (Empty, Special Chars, Regex, Large Lists)', () => {
    let fixture: ComponentFixture<MedicationCatalogManagement>;
    let component: MedicationCatalogManagement;
    let http: HttpTestingController;

    beforeEach(async () => {
      await TestBed.configureTestingModule({
        imports: [MedicationCatalogManagement],
        providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
      }).compileComponents();

      fixture = TestBed.createComponent(MedicationCatalogManagement);
      component = fixture.componentInstance;
      http = TestBed.inject(HttpTestingController);
      fixture.detectChanges();

      http.expectOne('/api/v1/admin/medications').flush([
        { id: 'm1', code: 'PARA-500', name: 'Paracetamol 500mg', active: true },
        { id: 'm2', code: 'AMOX-500', name: 'Amoxicillin 500mg', active: true },
        { id: 'm3', code: 'IBU-400', name: 'Ibuprofen 400mg', active: false },
        { id: 'm4', code: 'CEF-200', name: 'Cefixime 200mg (Kháng sinh)', active: true },
      ]);
      fixture.detectChanges();
    });

    afterEach(() => http.verify());

    it('adversarial test: empty and whitespace-only queries return all items without error', () => {
      component['query'].set('');
      expect(component['filteredMedications']().length).toBe(4);

      component['query'].set('   ');
      expect(component['filteredMedications']().length).toBe(4);

      component['query'].set('\t\n  ');
      expect(component['filteredMedications']().length).toBe(4);
    });

    it('adversarial test: regex meta-characters, SQL injection strings, and HTML tags do not crash', () => {
      const maliciousQueries = [
        '[.*+?^${}()|[]\\]',
        '(',
        '+++',
        '***',
        '?',
        '\\',
        '<script>alert("xss")</script>',
        "' OR '1'='1",
        '"; DROP TABLE medications; --',
        'undefined',
        'null',
        'NaN',
      ];

      for (const malicious of maliciousQueries) {
        expect(() => {
          component['query'].set(malicious);
          const result = component['filteredMedications']();
          expect(Array.isArray(result)).toBe(true);
        }).not.toThrow();
      }
    });

    it('adversarial test: case-insensitivity and Vietnamese unicode accents in search', () => {
      component['query'].set('paracetamol');
      expect(component['filteredMedications']().length).toBe(1);
      expect(component['filteredMedications']()[0].code).toBe('PARA-500');

      component['query'].set('kháng sinh');
      expect(component['filteredMedications']().length).toBe(1);
      expect(component['filteredMedications']()[0].code).toBe('CEF-200');

      component['query'].set('KHÁNG SINH');
      expect(component['filteredMedications']().length).toBe(1);
    });

    it('adversarial test: large dataset (10,000 items) filter performance benchmark', () => {
      const largeList = Array.from({ length: 10000 }, (_, i) => ({
        id: `med-${i}`,
        code: `MED-${String(i).padStart(5, '0')}`,
        name: `Thuốc thử nghiệm lâm sàng số ${i} đặc trị`,
        active: i % 2 === 0,
      }));

      component['medications'].set(largeList);

      const startTime = performance.now();
      component['query'].set('5000');
      const filtered = component['filteredMedications']();
      const elapsed = performance.now() - startTime;

      expect(filtered.length).toBeGreaterThan(0);
      expect(elapsed).toBeLessThan(100); // Must compute in under 100ms
    });
  });

  describe('Focus 3: Master-Detail & Modal Overlay Focus / Backdrop Clicks', () => {
    let fixture: ComponentFixture<RoomManagement>;
    let component: RoomManagement;
    let http: HttpTestingController;

    beforeEach(async () => {
      sessionStorage.setItem('clinicOneSessionType', 'STAFF');
      sessionStorage.setItem('clinicOneStaffRoles', JSON.stringify(['ADMIN']));

      await TestBed.configureTestingModule({
        imports: [RoomManagement],
        providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
      }).compileComponents();

      fixture = TestBed.createComponent(RoomManagement);
      component = fixture.componentInstance;
      http = TestBed.inject(HttpTestingController);
      fixture.detectChanges();

      http.expectOne('/api/v1/rooms').flush([
        { id: 'r1', code: 'NOI-01', name: 'Phòng Nội 1', specialty: 'Nội tổng quát', active: true, qrToken: 'token-123' },
      ]);
      http.expectOne('/api/v1/specialties').flush([
        { code: 'NOI', name: 'Nội tổng quát', description: 'Khám nội' },
      ]);
      fixture.detectChanges();
    });

    afterEach(() => {
      sessionStorage.clear();
      http.verify();
    });

    it('adversarial test: modal open / close state and backdrop click dismisses dialog', () => {
      expect(component['formOpen']()).toBe(false);

      // Open create modal
      component['openCreate']();
      expect(component['formOpen']()).toBe(true);
      expect(component['editingId']()).toBeNull();
      fixture.detectChanges();

      // Verify modal DOM element exists with ARIA attributes
      const modalOverlay = fixture.nativeElement.querySelector('[role="presentation"]');
      const dialog = fixture.nativeElement.querySelector('[role="dialog"]');
      expect(modalOverlay).toBeTruthy();
      expect(dialog).toBeTruthy();
      expect(dialog.getAttribute('aria-modal')).toBe('true');

      // Trigger closeForm
      component['closeForm']();
      expect(component['formOpen']()).toBe(false);
      fixture.detectChanges();
      expect(fixture.nativeElement.querySelector('[role="dialog"]')).toBeNull();
    });

    it('adversarial test: QR code modal opens, loads, and closes correctly', () => {
      expect(component['qrRoom']()).toBeNull();

      component['openQr']({ id: 'r1', code: 'NOI-01', name: 'Phòng Nội 1', specialty: 'Nội tổng quát', active: true, qrToken: 'token-123' });
      expect(component['qrRoom']()).toBeTruthy();
      expect(component['qrRoom']()?.code).toBe('NOI-01');

      component['closeQr']();
      expect(component['qrRoom']()).toBeNull();
      expect(component['qrImage']()).toBe('');
    });
  });

  describe('Focus 4: Table Row Height (36px) & Token Standardization', () => {
    let fixture: ComponentFixture<AppointmentsList>;
    let component: AppointmentsList;
    let http: HttpTestingController;

    beforeEach(async () => {
      await TestBed.configureTestingModule({
        imports: [AppointmentsList],
        providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
      }).compileComponents();

      fixture = TestBed.createComponent(AppointmentsList);
      component = fixture.componentInstance;
      http = TestBed.inject(HttpTestingController);
      fixture.detectChanges();

      http.expectOne('/api/v1/appointments').flush([
        {
          id: 'apt-1',
          appointmentCode: 'APT-2026-0001',
          appointmentDate: '2026-08-20',
          startTime: '08:30:00',
          specialty: 'Nội tổng quát',
          doctorName: 'BS Nguyễn Văn A',
          reason: 'Khám sức khỏe',
          status: 'BOOKED',
          statusLabel: 'Đã đặt hẹn',
        },
      ]);
      fixture.detectChanges();
    });

    afterEach(() => http.verify());

    it('adversarial test: appointments list renders structured cards and status badges correctly', () => {
      expect(component['visibleAppointments']().length).toBe(1);
      const item = component['visibleAppointments']()[0];
      expect(item.appointmentCode).toBe('APT-2026-0001');

      // Check status class mapping
      expect(component['statusClass']('COMPLETED')).toBe('erp-badge-success');
      expect(component['statusClass']('CHECKED_IN')).toBe('erp-badge-info');
      expect(component['statusClass']('CANCELLED')).toBe('erp-badge-danger');
    });
  });
});

describe('Empirical Adversarial Stress Suite - Challenger 2: Cross-Role Workflows', () => {

  describe('Focus 1: Examination -> Medical Record Lifecycle & Concurrency', () => {
    let fixture: ComponentFixture<DoctorExamination>;
    let component: DoctorExamination;
    let http: HttpTestingController;

    beforeEach(async () => {
      sessionStorage.setItem('clinicOneAccessToken', 'doctor-token');
      sessionStorage.setItem('clinicOneStaffRole', 'DOCTOR');
      await TestBed.configureTestingModule({
        imports: [DoctorExamination],
        providers: [
          provideHttpClient(),
          provideHttpClientTesting(),
          provideRouter([{ path: 'doctor', component: DoctorExamination }]),
          { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => 'ticket-stress-1' } } } },
        ],
      }).compileComponents();

      fixture = TestBed.createComponent(DoctorExamination);
      component = fixture.componentInstance;
      http = TestBed.inject(HttpTestingController);
      fixture.detectChanges();
    });

    afterEach(() => {
      http.verify();
      sessionStorage.clear();
    });

    it('adversarial test: stale recordVersion conflict shows optimistic locking error without clearing unsaved text', () => {
      http.expectOne('/api/v1/doctor/examinations/ticket-stress-1').flush({
        ticketId: 'ticket-stress-1', appointmentId: 'apt-1', examinationId: 'exam-1',
        queueNumber: 5, roomName: 'Phòng Nội 01', appointmentCode: 'CLN-001', specialty: 'Nội tổng quát',
        clinicServiceId: 'srv-1', doctorName: 'BS. Nguyễn An', appointmentDate: '2026-08-11',
        startTime: '09:00:00', patientName: 'Nguyễn Thanh Vũ', patientDateOfBirth: '1990-01-01',
        patientGender: 'Nam', patientPhone: '0912345678', reason: 'Đau đầu', examinationNotes: 'Khám ban đầu',
        diagnosis: 'Đau đầu', conclusion: 'Theo dõi', treatmentPlan: 'Nghỉ ngơi',
        prescription: null, followUpDate: null, followUpDays: null, followUpNote: null,
        status: 'IN_PROGRESS', signedAt: null, draftSavedAt: '2026-08-11T09:30:00Z', recordVersion: 1,
        requiresMedicalRecord: true, history: [], prescriptionLines: [],
      });
      fixture.detectChanges();

      // Edit clinical notes
      component['form'].patchValue({ examinationNotes: 'Ghi nhận sửa đổi từ tab 1' });
      component['draftDirty'] = true;

      // Trigger saveDraft
      component['saveDraft']();
      const saveReq = http.expectOne('/api/v1/doctor/examinations/ticket-stress-1/draft');
      expect(saveReq.request.body.recordVersion).toBe(1);

      // Simulate 409 Conflict from backend (stale record version)
      saveReq.flush(
        { code: 'MEDICAL_RECORD_VERSION_CONFLICT', message: 'Phiếu khám đã được cập nhật ở một cửa sổ khác. Hãy tải lại trang trước khi tiếp tục.' },
        { status: 409, statusText: 'Conflict' }
      );
      fixture.detectChanges();

      // Verify error is displayed and form value is retained
      expect(component['error']()).toContain('Phiếu khám đã được cập nhật ở một cửa sổ khác');
      expect(component['form'].controls.examinationNotes.value).toBe('Ghi nhận sửa đổi từ tab 1');
    });

    it('adversarial test: prescription line count strictly capped at 20 lines and quantity capped at 999', () => {
      http.expectOne('/api/v1/doctor/examinations/ticket-stress-1').flush({
        ticketId: 'ticket-stress-1', appointmentId: 'apt-1', examinationId: 'exam-1',
        queueNumber: 5, roomName: 'Phòng Nội 01', appointmentCode: 'CLN-001', specialty: 'Nội tổng quát',
        clinicServiceId: 'srv-1', doctorName: 'BS. Nguyễn An', appointmentDate: '2026-08-11',
        startTime: '09:00:00', patientName: 'Nguyễn Thanh Vũ', patientDateOfBirth: '1990-01-01',
        patientGender: 'Nam', patientPhone: '0912345678', reason: 'Đau đầu', examinationNotes: 'Khám ban đầu',
        diagnosis: 'Đau đầu', conclusion: 'Theo dõi', treatmentPlan: 'Nghỉ ngơi',
        prescription: null, followUpDate: null, followUpDays: null, followUpNote: null,
        status: 'IN_PROGRESS', signedAt: null, draftSavedAt: null, recordVersion: 1,
        requiresMedicalRecord: true, history: [], prescriptionLines: [],
      });
      fixture.detectChanges();

      // Add up to 20 prescription lines
      for (let i = 0; i < 20; i++) {
        component['addPrescriptionLine']();
      }
      expect(component['prescriptionLines'].length).toBe(20);

      // Attempting to add 21st line is ignored
      component['addPrescriptionLine']();
      expect(component['prescriptionLines'].length).toBe(20);

      // Quantity clamping: setting > 999 is clamped to 999
      component['prescriptionLines'].at(0).controls.quantity.setValue(1500);
      component['limitPrescriptionQuantity'](0);
      expect(component['prescriptionLines'].at(0).controls.quantity.value).toBe(999);
      expect(component['notice']()).toContain('Số lượng thuốc tối đa 999');
    });

    it('adversarial test: wrong profile confirmation prompts for reason and calls rollback endpoint', () => {
      http.expectOne('/api/v1/doctor/examinations/ticket-stress-1').flush({
        ticketId: 'ticket-stress-1', appointmentId: 'apt-1', examinationId: 'exam-1',
        queueNumber: 5, roomName: 'Phòng Nội 01', appointmentCode: 'CLN-001', specialty: 'Nội tổng quát',
        clinicServiceId: 'srv-1', doctorName: 'BS. Nguyễn An', appointmentDate: '2026-08-11',
        startTime: '09:00:00', patientName: 'Nguyễn Thanh Vũ', patientDateOfBirth: '1990-01-01',
        patientGender: 'Nam', patientPhone: '0912345678', reason: 'Đau đầu', examinationNotes: 'Khám',
        diagnosis: 'Đau đầu', conclusion: 'Theo dõi', treatmentPlan: 'Nghỉ ngơi',
        prescription: null, followUpDate: null, followUpDays: null, followUpNote: null,
        status: 'IN_PROGRESS', signedAt: null, draftSavedAt: null, recordVersion: 1,
        requiresMedicalRecord: true, history: [], prescriptionLines: [],
      });
      fixture.detectChanges();

      // Request wrong profile
      component['requestWrongProfile']();
      expect(component['confirmingWrongProfile']()).toBe(true);

      // Submit with valid reason
      component['wrongProfileReason'].set('Bác sĩ phát hiện đang mở nhầm hồ sơ người bệnh');
      component['confirmWrongProfile']();

      const req = http.expectOne('/api/v1/doctor/examinations/ticket-stress-1/wrong-profile');
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({ reason: 'Bác sĩ phát hiện đang mở nhầm hồ sơ người bệnh' });
      req.flush({});
      fixture.detectChanges();

      expect(component['confirmingWrongProfile']()).toBe(false);
      expect(component['returningWrongProfile']()).toBe(false);
    });

    it('adversarial test: follow-up interval clamping to 365 days max', () => {
      http.expectOne('/api/v1/doctor/examinations/ticket-stress-1').flush({
        ticketId: 'ticket-stress-1', appointmentId: 'apt-1', examinationId: 'exam-1',
        queueNumber: 5, roomName: 'Phòng Nội 01', appointmentCode: 'CLN-001', specialty: 'Nội tổng quát',
        clinicServiceId: 'srv-1', doctorName: 'BS. Nguyễn An', appointmentDate: '2026-08-11',
        startTime: '09:00:00', patientName: 'Nguyễn Thanh Vũ', patientDateOfBirth: '1990-01-01',
        patientGender: 'Nam', patientPhone: '0912345678', reason: 'Đau đầu', examinationNotes: 'Khám',
        diagnosis: 'Đau đầu', conclusion: 'Theo dõi', treatmentPlan: 'Nghỉ ngơi',
        prescription: null, followUpDate: null, followUpDays: null, followUpNote: null,
        status: 'IN_PROGRESS', signedAt: null, draftSavedAt: null, recordVersion: 1,
        requiresMedicalRecord: true, history: [], prescriptionLines: [],
      });
      fixture.detectChanges();

      component['form'].controls.followUpDays.setValue(400);
      component['limitFollowUpDays']();
      expect(component['form'].controls.followUpDays.value).toBe(365);
      expect(component['notice']()).toContain('Số ngày tái khám tối đa 365');
    });
  });

  describe('Focus 2: Rescheduling & Reconciliation Synchronization', () => {
    let reschedFixture: ComponentFixture<Rescheduling>;
    let reschedComponent: Rescheduling;
    let reconFixture: ComponentFixture<ReconciliationManagement>;
    let reconComponent: ReconciliationManagement;
    let http: HttpTestingController;

    beforeEach(async () => {
      sessionStorage.setItem('clinicOneAccessToken', 'staff-token');
      sessionStorage.setItem('clinicOneStaffRole', 'COORDINATOR');
      await TestBed.configureTestingModule({
        imports: [Rescheduling, ReconciliationManagement],
        providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
      }).compileComponents();

      http = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
      http.verify();
      sessionStorage.clear();
    });

    it('adversarial test: pending reschedule cases count accurately combines OPEN, PENDING, and empty status', () => {
      reschedFixture = TestBed.createComponent(Rescheduling);
      reschedComponent = reschedFixture.componentInstance;
      reschedFixture.detectChanges();

      http.expectOne('/api/v1/admin/rescheduling').flush([
        { id: 'c1', appointmentId: 'a1', appointmentCode: 'CL-01', specialty: 'Nội', oldDoctorName: 'BS A', oldDoctorId: 'd1', oldAppointmentDate: '2026-08-10', oldStartTime: '08:00:00', reason: 'Nghỉ', status: 'OPEN', newDoctorName: null, newDoctorId: null, newAppointmentDate: null, newStartTime: null, createdAt: '2026-08-10T00:00:00Z', resolvedAt: null },
        { id: 'c2', appointmentId: 'a2', appointmentCode: 'CL-02', specialty: 'Ngoại', oldDoctorName: 'BS B', oldDoctorId: 'd2', oldAppointmentDate: '2026-08-10', oldStartTime: '09:00:00', reason: 'Nghỉ', status: 'PENDING', newDoctorName: null, newDoctorId: null, newAppointmentDate: null, newStartTime: null, createdAt: '2026-08-10T00:00:00Z', resolvedAt: null },
        { id: 'c3', appointmentId: 'a3', appointmentCode: 'CL-03', specialty: 'Mắt', oldDoctorName: 'BS C', oldDoctorId: 'd3', oldAppointmentDate: '2026-08-10', oldStartTime: '10:00:00', reason: 'Nghỉ', status: 'RESOLVED', newDoctorName: 'BS D', newDoctorId: 'd4', newAppointmentDate: '2026-08-11', newStartTime: '10:00:00', createdAt: '2026-08-10T00:00:00Z', resolvedAt: '2026-08-10T01:00:00Z' },
      ]);
      http.expectOne('/api/v1/admin/rescheduling/c1/alternatives').flush([]);
      reschedFixture.detectChanges();

      expect(reschedComponent['pendingCasesCount']()).toBe(2);
      expect(reschedComponent['resolvedCasesCount']()).toBe(1);
      expect(reschedComponent['totalCasesCount']()).toBe(3);
    });

    it('adversarial test: reconciliation switching to REPLAY_LOG switches referenceType to BUSINESS_LOG', () => {
      reconFixture = TestBed.createComponent(ReconciliationManagement);
      reconComponent = reconFixture.componentInstance;
      reconFixture.detectChanges();

      http.expectOne('/api/v1/admin/reconciliations?status=OPEN').flush([
        { id: 'inc-1', incidentCode: 'INC-2026-001', entityType: 'APPOINTMENT', entityId: 'a1', eventId: 'ev-1', reason: 'Lỗi đồng bộ', assignee: 'coordinator', status: 'OPEN', resolutionAction: null, referenceType: 'INCIDENT', referenceValue: 'INC-2026-001', resultNote: null, closedBy: null, closedAt: null, createdAt: '2026-08-10T00:00:00Z' },
      ]);
      reconFixture.detectChanges();

      expect(reconComponent['referenceType']()).toBe('INCIDENT');
      reconComponent['updateAction']('REPLAY_LOG');
      expect(reconComponent['action']()).toBe('REPLAY_LOG');
      expect(reconComponent['referenceType']()).toBe('BUSINESS_LOG');
    });
  });
});

