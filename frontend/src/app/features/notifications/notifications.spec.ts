import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Component } from '@angular/core';
import { provideRouter } from '@angular/router';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { Notifications } from './notifications';

@Component({ standalone: true, template: '' })
class DummyPage {}

describe('Notifications', () => {
  let fixture: ComponentFixture<Notifications>;
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Notifications],
      providers: [provideRouter([{ path: 'medical-records/:id', component: DummyPage }]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    fixture = TestBed.createComponent(Notifications);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  it('shows the notifications section with the account side menu', () => {
    http.expectOne('/api/v1/notifications').flush([]);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Thông báo');
    expect(fixture.nativeElement.textContent).toContain('Hồ sơ bệnh nhân');
  });

  it('renders a signed-record notification and marks it read when opened', () => {
    http.expectOne('/api/v1/notifications').flush([{
      id: 'notification-1', type: 'MEDICAL_RECORD_SIGNED', title: 'Phiếu khám đã có kết quả',
      message: 'Bác sĩ đã ký phiếu khám cho lịch hẹn CL-001.', targetUrl: '/medical-records/record-1',
      read: false, createdAt: '2026-08-07T09:30:00Z',
    }]);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Phiếu khám đã có kết quả');
    (fixture.nativeElement.querySelector('button[data-testid="notification-item"]') as HTMLButtonElement).click();
    const request = http.expectOne('/api/v1/notifications/notification-1/read');
    expect(request.request.method).toBe('POST');
    request.flush(null);
  });

  it('marks a restricted notification read without navigating when it has no target', () => {
    http.expectOne('/api/v1/notifications').flush([{
      id: 'notification-2', type: 'APPOINTMENT_CREATED', title: 'Bạn có thông báo mới',
      message: 'Vui lòng hoàn tất kích hoạt tài khoản để xem trong ứng dụng.', targetUrl: null,
      read: false, createdAt: '2026-08-07T09:30:00Z',
    }]);
    fixture.detectChanges();

    expect(() => (fixture.nativeElement.querySelector('button[data-testid="notification-item"]') as HTMLButtonElement).click()).not.toThrow();
    const request = http.expectOne('/api/v1/notifications/notification-2/read');
    expect(request.request.method).toBe('POST');
    request.flush(null);
  });
});
