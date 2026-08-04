import { Routes } from '@angular/router';
import { Login } from './features/auth/login/login';
import { AppShell } from './layout/app-shell/app-shell';
import { Dashboard } from './features/dashboard/dashboard';
import { Home } from './features/home/home';
import { Register } from './features/auth/register/register';
import { PublicPage } from './features/public/public-page';
import { Account } from './features/account/account';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'home' },
  { path: 'home', component: Home },
  { path: 'login', component: Login },
  { path: 'register', component: Register },
  { path: 'about', component: PublicPage, data: { page: 'about' } },
  { path: 'process', component: PublicPage, data: { page: 'process' } },
  { path: 'common-issues', component: PublicPage, data: { page: 'common-issues' } },
  { path: 'support', component: PublicPage, data: { page: 'support' } },
  { path: 'contact', component: PublicPage, data: { page: 'contact' } },
  {
    path: '',
    component: AppShell,
    children: [
      { path: 'dashboard', component: Dashboard },
      { path: 'account', component: Account },
    ]
  },
  { path: '**', redirectTo: 'home' },
];
