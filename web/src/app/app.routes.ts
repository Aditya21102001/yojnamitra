import { Routes } from '@angular/router';
import { authGuard } from './auth-guard';
import { Dashboard } from './dashboard';
import { Login } from './login';
import { Match } from './match';
import { Register } from './register';

export const routes: Routes = [
  { path: '', component: Match },
  { path: 'login', component: Login },
  { path: 'register', component: Register },
  { path: 'dashboard', component: Dashboard, canActivate: [authGuard] },
  { path: '**', redirectTo: '' },
];
