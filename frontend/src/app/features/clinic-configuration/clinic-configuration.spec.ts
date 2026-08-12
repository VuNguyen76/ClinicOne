import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { provideRouter } from '@angular/router';
import { AuthApiService } from '../../core/auth/auth-api.service';
import { ClinicConfiguration } from './clinic-configuration';
import { vi } from 'vitest';

describe('ClinicConfiguration', () => {
  let fixture: ComponentFixture<ClinicConfiguration>;
  const api = {
    getClinicConfiguration: vi.fn(),
    updateClinicConfiguration: vi.fn(),
  } as unknown as Pick<AuthApiService, 'getClinicConfiguration' | 'updateClinicConfiguration'>;

  beforeEach(async () => {
    vi.mocked(api.getClinicConfiguration).mockReturnValue(of({
      id: 'config', unitName: 'ClinicOne', departmentName: 'Khám bệnh', holdMinutes: 10,
      cancellationThresholdHours: 12, updatedBy: 'SYSTEM', updatedAt: '2026-08-10T01:00:00Z',
    }));
    vi.mocked(api.updateClinicConfiguration).mockReturnValue(of({
      id: 'config', unitName: 'ClinicOne', departmentName: 'Khám bệnh', holdMinutes: 10,
      cancellationThresholdHours: 12, updatedBy: 'SYSTEM', updatedAt: '2026-08-10T01:00:00Z',
    }));
    await TestBed.configureTestingModule({
      imports: [ClinicConfiguration],
      providers: [provideRouter([]), { provide: AuthApiService, useValue: api }],
    }).compileComponents();
    fixture = TestBed.createComponent(ClinicConfiguration);
    fixture.detectChanges();
  });

  it('loads the SRS configuration defaults for editing', () => {
    expect(fixture.nativeElement.textContent).toContain('ClinicOne');
    expect(fixture.nativeElement.textContent).toContain('10');
    expect(fixture.nativeElement.textContent).toContain('15 phút');
  });

  it('submits the configured values through the admin API', () => {
    const button = fixture.nativeElement.querySelector('button[type="submit"]') as HTMLButtonElement;
    button.click();
    expect(api.updateClinicConfiguration).toHaveBeenCalledWith({
      unitName: 'ClinicOne', departmentName: 'Khám bệnh', holdMinutes: 10, cancellationThresholdHours: 12,
    });
  });
});
