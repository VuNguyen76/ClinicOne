import { routes } from './app.routes';
import { adminGuard, clinicalStaffGuard, coordinatorRouteGuard, doctorGuard, queueSupervisorGuard, roomManagerGuard } from './core/auth/auth.guard';

describe('ClinicOne route-to-API role contracts', () => {
  it('provides a dedicated coordinator landing route for queue supervision', async () => {
    const route = routes.find((item) => item.path === 'coordinator');

    expect(route?.canActivate).toContain(queueSupervisorGuard);
    const component = await route?.loadComponent?.();
    expect((component as { name?: string } | undefined)?.name).toContain('StaffDashboard');
  });

  it('provides coordinator-prefixed URLs for every management workspace in their menu', () => {
    const coordinatorPaths = [
      'coordinator/rooms', 'coordinator/doctors', 'coordinator/services', 'coordinator/specialties',
      'coordinator/medical-record-templates', 'coordinator/schedule-templates', 'coordinator/doctor-time-off',
      'coordinator/statistics', 'coordinator/rescheduling', 'coordinator/reconciliations', 'coordinator/business-audit',
      'coordinator/medications', 'coordinator/diagnoses',
    ];
    const protectedLegacyPaths = [
      'admin/rooms', 'admin/doctors', 'admin/services', 'admin/specialties',
      'admin/medical-record-templates', 'admin/schedule-templates', 'admin/doctor-time-off',
      'admin/statistics', 'admin/rescheduling', 'admin/reconciliations', 'admin/business-audit', 'admin/medications', 'admin/diagnoses',
    ];

    for (const path of coordinatorPaths) expect(routes.some((route) => route.path === path)).toBe(true);
    for (const path of protectedLegacyPaths) {
      expect(routes.find((route) => route.path === path)?.canActivate).toContain(coordinatorRouteGuard);
    }
  });

  it('restricts schedule-template management to roles allowed to load the doctor directory', () => {
    const route = routes.find((item) => item.path === 'admin/schedule-templates');

    expect(route?.canActivate).toContain(roomManagerGuard);
  });

  it('restricts doctor time-off management to clinical staff who can load doctor data', () => {
    const route = routes.find((item) => item.path === 'admin/doctor-time-off');

    expect(route?.canActivate).toContain(clinicalStaffGuard);
  });

  it('exposes a doctor-only appointment agenda page', async () => {
    const route = routes.find((item) => item.path === 'doctor/appointments');

    expect(route?.canActivate).toContain(doctorGuard);
    const component = await route?.loadComponent?.();
    expect((component as { name?: string } | undefined)?.name).toContain('DoctorAppointments');
  });

  it('loads the reason catalog page instead of redirecting users to diagnoses', async () => {
    const route = routes.find((item) => item.path === 'admin/reason-catalog');

    expect(route?.redirectTo).toBeUndefined();
    expect(route?.canActivate).toContain(adminGuard);
    expect(route?.loadComponent).toBeDefined();
    const component = await route?.loadComponent?.();
    expect((component as { name?: string } | undefined)?.name).toContain('ReasonCatalogManagement');
  });

  it('loads clinic configuration instead of redirecting administrators to room management', async () => {
    const route = routes.find((item) => item.path === 'admin/configuration');

    expect(route?.redirectTo).toBeUndefined();
    expect(route?.canActivate).toContain(adminGuard);
    expect(route?.loadComponent).toBeDefined();
    const component = await route?.loadComponent?.();
    expect((component as { name?: string } | undefined)?.name).toContain('ClinicConfiguration');
  });

  it('folds the legacy reception appointments route into the daily overview', async () => {
    const route = routes.find((item) => item.path === 'reception/appointments');

    expect(route?.data?.['receptionView']).toBe('overview');
    const component = await route?.loadComponent?.();
    expect((component as { name?: string } | undefined)?.name).toContain('ReceptionCheckIn');
  });
});
