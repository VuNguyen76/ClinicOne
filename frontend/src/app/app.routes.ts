import { Routes } from '@angular/router';
import { Login } from './features/auth/login/login';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'login' },
  { path: 'login', component: Login },
  { path: '**', redirectTo: 'login' },
];
