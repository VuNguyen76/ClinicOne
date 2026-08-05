import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { Booking } from './booking';

describe('Booking calendar', () => {
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
    http.expectOne('/api/v1/specialties').flush([{ code: 'NOI', name: 'Nội tổng quát', description: 'Khám tổng quát' }]);
    http.expectOne('/api/v1/patient-profiles').flush([]);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  it('renders a complete six-week calendar grid instead of a short date strip', () => {
    expect(component['dates']().length).toBe(42);
    component['chooseSpecialty']({ code: 'NOI', name: 'Nội tổng quát', description: 'Khám tổng quát' });
    http.expectOne((item) => item.url === '/api/v1/appointment-slots').flush([]);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelectorAll('[data-testid="calendar-date"]').length).toBe(42);
  });

  it('loads availability for the complete displayed month', () => {
    component['chooseSpecialty']({ code: 'NOI', name: 'Nội tổng quát', description: 'Khám tổng quát' });
    const request = http.expectOne((item) => item.url === '/api/v1/appointment-slots' && item.params.get('specialty') === 'Nội tổng quát');
    const month = component['calendarMonth']();
    const expectedFrom = `${month.getFullYear()}-${String(month.getMonth() + 1).padStart(2, '0')}-01`;
    const end = new Date(month.getFullYear(), month.getMonth() + 1, 0);
    const expectedTo = `${end.getFullYear()}-${String(end.getMonth() + 1).padStart(2, '0')}-${String(end.getDate()).padStart(2, '0')}`;
    expect(request.request.params.get('from')).toBe(expectedFrom);
    expect(request.request.params.get('to')).toBe(expectedTo);
    request.flush([]);
  });
});
