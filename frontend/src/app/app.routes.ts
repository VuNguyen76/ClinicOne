import { Routes } from '@angular/router';
import { Login } from './features/auth/login/login';
import { Dashboard } from './features/dashboard/dashboard';
import { Home } from './features/home/home';
import { Register } from './features/auth/register/register';
import { PublicPage } from './features/public/public-page';
import { Account } from './features/account/account';
import { Booking } from './features/appointments/booking/booking';
import { AppointmentDetail } from './features/appointments/detail/appointment-detail';
import { MedicalRecords } from './features/medical-records/medical-records';
import { MedicalRecordDetail } from './features/medical-records/medical-record-detail';
import { PatientProfiles } from './features/patient-profiles/patient-profiles';
import { ChangePassword } from './features/auth/change-password/change-password';
import { Notifications } from './features/notifications/notifications';
import { authGuard } from './core/auth/auth.guard';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'home' },
  { path: 'home', component: Home },
  { path: 'login', component: Login },
  { path: 'register', component: Register },
  { path: 'account', component: Account, canActivate: [authGuard] },
  { path: 'change-password', component: ChangePassword, canActivate: [authGuard] },
  { path: 'appointments/new', component: Booking, canActivate: [authGuard] },
  { path: 'appointments/:id', component: AppointmentDetail, canActivate: [authGuard] },
  { path: 'medical-records', component: MedicalRecords, canActivate: [authGuard] },
  { path: 'medical-records/:id', component: MedicalRecordDetail, canActivate: [authGuard] },
  { path: 'patient-profiles', component: PatientProfiles, canActivate: [authGuard] },
  { path: 'notifications', component: Notifications, canActivate: [authGuard] },
  { path: 'about', component: PublicPage, data: { page: 'about' } },
  { path: 'process', component: PublicPage, data: { page: 'process' } },
  { path: 'common-issues', component: PublicPage, data: { page: 'common-issues' } },
  { path: 'support', component: PublicPage, data: { page: 'support' } },
  { path: 'contact', component: PublicPage, data: { page: 'contact' } },
  { path: 'dashboard', component: Dashboard, canActivate: [authGuard] },
  { path: '**', redirectTo: 'home' },
];
