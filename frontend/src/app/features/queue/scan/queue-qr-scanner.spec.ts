import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { provideRouter } from '@angular/router';
import { QueueQrScanner } from './queue-qr-scanner';

describe('QueueQrScanner', () => {
  let fixture: ComponentFixture<QueueQrScanner>;
  let component: QueueQrScanner;
  let router: Router;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [QueueQrScanner],
      providers: [provideRouter([])],
    }).compileComponents();
    fixture = TestBed.createComponent(QueueQrScanner);
    component = fixture.componentInstance;
    router = TestBed.inject(Router);
    fixture.detectChanges();
  });

  it('shows a camera action for scanning the fixed room QR code', () => {
    expect(fixture.nativeElement.querySelector('[data-testid="start-qr-camera"]')).not.toBeNull();
    expect(fixture.nativeElement.textContent).toContain('Quét mã trước phòng khám');
  });

  it('accepts only ClinicOne room check-in links', () => {
    const navigate = vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);

    (component as any).acceptScannedValue(`${window.location.origin}/queue/check-in/room-token-123`);
    expect(navigate).toHaveBeenCalledWith('/queue/check-in/room-token-123');

    navigate.mockClear();
    (component as any).acceptScannedValue('https://example.com/queue/check-in/room-token-123');
    expect(navigate).not.toHaveBeenCalled();
    expect((component as any).error()).toContain('không thuộc ClinicOne');
  });
});
