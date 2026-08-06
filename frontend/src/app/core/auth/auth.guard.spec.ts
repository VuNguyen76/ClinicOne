import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { authGuard, patientGuard, roomManagerGuard, staffGuard } from './auth.guard';

describe('ClinicOne route guards', () => {
  let router: { createUrlTree: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    sessionStorage.clear();
    router = { createUrlTree: vi.fn((commands: unknown[]) => ({ commands })) };
    TestBed.configureTestingModule({ providers: [{ provide: Router, useValue: router }] });
  });

  it('keeps patient routes for patient sessions and redirects them away from staff routes', () => {
    sessionStorage.setItem('clinicOneAccessToken', 'patient-token');

    expect(TestBed.runInInjectionContext(() => patientGuard(null as never, { url: '/appointments' } as never))).toBe(true);
    expect(TestBed.runInInjectionContext(() => staffGuard(null as never, { url: '/admin/rooms' } as never))).toEqual({ commands: ['/staff/login'] });
    expect(TestBed.runInInjectionContext(() => roomManagerGuard(null as never, { url: '/admin/rooms' } as never))).toEqual({ commands: ['/staff/login'] });
  });

  it('allows only room managers to open the room workspace', () => {
    sessionStorage.setItem('clinicOneAccessToken', 'staff-token');
    sessionStorage.setItem('clinicOneStaffRole', 'COORDINATOR');

    expect(TestBed.runInInjectionContext(() => staffGuard(null as never, { url: '/queue/rooms/NOI-01' } as never))).toBe(true);
    expect(TestBed.runInInjectionContext(() => roomManagerGuard(null as never, { url: '/admin/rooms' } as never))).toBe(true);
    expect(TestBed.runInInjectionContext(() => patientGuard(null as never, { url: '/appointments' } as never))).toEqual({ commands: ['/home'] });
  });

  it('blocks a non-manager staff role from room configuration', () => {
    sessionStorage.setItem('clinicOneAccessToken', 'staff-token');
    sessionStorage.setItem('clinicOneStaffRole', 'DOCTOR');

    expect(TestBed.runInInjectionContext(() => staffGuard(null as never, { url: '/queue/rooms/NOI-01' } as never))).toBe(true);
    expect(TestBed.runInInjectionContext(() => roomManagerGuard(null as never, { url: '/admin/rooms' } as never))).toEqual({ commands: ['/home'] });
  });
});
