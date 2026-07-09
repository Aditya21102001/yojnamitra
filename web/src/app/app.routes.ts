import { Routes } from '@angular/router';
import { authGuard } from './auth-guard';
import { Dashboard } from './dashboard';
import { ForgotPassword } from './forgot-password';
import { Login } from './login';
import { Match } from './match';
import { Register } from './register';
import { ResetPassword } from './reset-password';

export const routes: Routes = [
  { path: '', component: Match },
  { path: 'login', component: Login },
  { path: 'register', component: Register },
  { path: 'forgot-password', component: ForgotPassword },
  { path: 'reset-password', component: ResetPassword },
  { path: 'dashboard', component: Dashboard, canActivate: [authGuard] },
  { path: '**', redirectTo: '' },
];
