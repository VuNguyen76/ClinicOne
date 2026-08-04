import { Routes } from '@angular/router';
import { Login } from './features/auth/login/login';
import { AppShell } from './layout/app-shell/app-shell';
import { Dashboard } from './features/dashboard/dashboard';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'login' },
  { path: 'login', component: Login },
  {
    path: '',
    component: AppShell,
    children: [
      { path: 'dashboard', component: Dashboard }
    ]
  },
  { path: '**', redirectTo: 'login' },
];
