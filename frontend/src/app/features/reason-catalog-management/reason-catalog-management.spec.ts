import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { vi } from 'vitest';
import { AuthApiService } from '../../core/auth/auth-api.service';
import { ReasonCatalogManagement } from './reason-catalog-management';

describe('ReasonCatalogManagement', () => {
  let fixture: ComponentFixture<ReasonCatalogManagement>;
  const api = {
    getAdminCancellationReasons: vi.fn(),
    createCancellationReason: vi.fn(),
    setCancellationReasonActive: vi.fn(),
  } as unknown as Pick<AuthApiService, 'getAdminCancellationReasons' | 'createCancellationReason' | 'setCancellationReasonActive'>;

  beforeEach(async () => {
    vi.mocked(api.getAdminCancellationReasons).mockReturnValue(of([
      { id: 'reason-1', type: 'APPOINTMENT_CANCELLATION', code: 'SCHEDULE_CHANGE', label: 'Thay đổi kế hoạch', active: true },
      { id: 'reason-2', type: 'APPOINTMENT_CANCELLATION', code: 'MEDICAL_EMERGENCY', label: 'Có việc đột xuất', active: true },
      { id: 'reason-3', type: 'APPOINTMENT_CANCELLATION', code: 'FOUND_OTHER_PROVIDER', label: 'Đã khám ở nơi khác', active: true },
    ]));
    await TestBed.configureTestingModule({
      imports: [ReasonCatalogManagement],
      providers: [provideRouter([]), { provide: AuthApiService, useValue: api }],
    }).compileComponents();
    fixture = TestBed.createComponent(ReasonCatalogManagement);
    fixture.detectChanges();
  });

  it('shows the seeded cancellation reasons', () => {
    expect(fixture.nativeElement.textContent).toContain('Thay đổi kế hoạch');
    expect(fixture.nativeElement.textContent).toContain('tối thiểu 3 lý do');
  });

  it('creates a new reason through the admin API', () => {
    vi.mocked(api.createCancellationReason).mockReturnValue(of({
      id: 'reason-4', type: 'APPOINTMENT_CANCELLATION', code: 'TRAVEL', label: 'Đi công tác', active: true,
    }));
    const inputs = fixture.nativeElement.querySelectorAll('input');
    (inputs[0] as HTMLInputElement).value = 'TRAVEL';
    inputs[0].dispatchEvent(new Event('input'));
    (inputs[1] as HTMLInputElement).value = 'Đi công tác';
    inputs[1].dispatchEvent(new Event('input'));
    (fixture.nativeElement.querySelector('button[type="submit"]') as HTMLButtonElement).click();
    expect(api.createCancellationReason).toHaveBeenCalledWith('TRAVEL', 'Đi công tác');
  });
});
