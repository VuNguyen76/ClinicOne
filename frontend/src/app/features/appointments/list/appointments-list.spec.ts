import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { AppointmentsList } from './appointments-list';

describe('AppointmentsList', () => {
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
        id: 'a-1', appointmentCode: 'CL-001', specialty: 'Nội tổng quát', doctorName: 'BS. An',
        appointmentDate: '2026-08-08', startTime: '08:30:00', reason: 'Đau đầu', status: 'BOOKED', statusLabel: 'Đã đặt',
      },
      {
        id: 'a-2', appointmentCode: 'CL-002', specialty: 'Tai mũi họng', doctorName: 'BS. Bình',
        appointmentDate: '2026-07-08', startTime: '09:00:00', reason: 'Đau họng', status: 'CANCELLED', statusLabel: 'Đã hủy',
      },
    ]);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  it('shows all appointments and their reference codes', () => {
    expect(fixture.nativeElement.textContent).toContain('Nội tổng quát');
    expect(fixture.nativeElement.textContent).toContain('CL-001');
    expect(fixture.nativeElement.textContent).toContain('Tai mũi họng');
  });

  it('filters appointments by status', () => {
    component['setFilter']('BOOKED');
    fixture.detectChanges();

    expect(component['visibleAppointments']().length).toBe(1);
    expect(fixture.nativeElement.textContent).toContain('Nội tổng quát');
    expect(fixture.nativeElement.textContent).not.toContain('Tai mũi họng');
  });
});
