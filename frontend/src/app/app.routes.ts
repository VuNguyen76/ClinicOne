import { Routes } from '@angular/router';
import { authGuard, patientGuard, roomManagerGuard, staffGuard } from './core/auth/auth.guard';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'home' },
  { path: 'home', loadComponent: () => import('./features/home/home').then((m) => m.Home) },
  { path: 'login', loadComponent: () => import('./features/auth/login/login').then((m) => m.Login) },
  { path: 'staff/login', loadComponent: () => import('./features/staff-auth/staff-login').then((m) => m.StaffLogin) },
  { path: 'register', loadComponent: () => import('./features/auth/register/register').then((m) => m.Register) },
  { path: 'account', loadComponent: () => import('./features/account/account').then((m) => m.Account), canActivate: [patientGuard] },
  { path: 'change-password', loadComponent: () => import('./features/auth/change-password/change-password').then((m) => m.ChangePassword), canActivate: [authGuard] },
  { path: 'appointments/new', loadComponent: () => import('./features/appointments/booking/booking').then((m) => m.Booking), canActivate: [patientGuard] },
  { path: 'appointments', loadComponent: () => import('./features/appointments/list/appointments-list').then((m) => m.AppointmentsList), canActivate: [patientGuard] },
  { path: 'appointments/:id', loadComponent: () => import('./features/appointments/detail/appointment-detail').then((m) => m.AppointmentDetail), canActivate: [patientGuard] },
  { path: 'queue/check-in/:roomCode', loadComponent: () => import('./features/queue/check-in/queue-check-in').then((m) => m.QueueCheckIn), canActivate: [patientGuard] },
  { path: 'queue/rooms/:roomCode', loadComponent: () => import('./features/queue/board/queue-board').then((m) => m.QueueBoard), canActivate: [staffGuard] },
  { path: 'medical-records', loadComponent: () => import('./features/medical-records/medical-records').then((m) => m.MedicalRecords), canActivate: [patientGuard] },
  { path: 'medical-records/:id', loadComponent: () => import('./features/medical-records/medical-record-detail').then((m) => m.MedicalRecordDetail), canActivate: [patientGuard] },
  { path: 'patient-profiles', loadComponent: () => import('./features/patient-profiles/patient-profiles').then((m) => m.PatientProfiles), canActivate: [patientGuard] },
  { path: 'notifications', loadComponent: () => import('./features/notifications/notifications').then((m) => m.Notifications), canActivate: [patientGuard] },
  { path: 'admin/rooms', loadComponent: () => import('./features/room-management/room-management').then((m) => m.RoomManagement), canActivate: [roomManagerGuard] },
  { path: 'about', loadComponent: () => import('./features/public/public-page').then((m) => m.PublicPage), data: { page: 'about' } },
  { path: 'process', loadComponent: () => import('./features/public/public-page').then((m) => m.PublicPage), data: { page: 'process' } },
  { path: 'common-issues', loadComponent: () => import('./features/public/public-page').then((m) => m.PublicPage), data: { page: 'common-issues' } },
  { path: 'support', loadComponent: () => import('./features/public/public-page').then((m) => m.PublicPage), data: { page: 'support' } },
  { path: 'contact', loadComponent: () => import('./features/public/public-page').then((m) => m.PublicPage), data: { page: 'contact' } },
  { path: 'dashboard', loadComponent: () => import('./features/dashboard/dashboard').then((m) => m.Dashboard), canActivate: [patientGuard] },
  { path: '**', redirectTo: 'home' },
];
