import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';
import { LoginComponent } from './features/auth/components/login';

export const routes: Routes = [
  {
    path: 'login',
    component: LoginComponent
  },
  {
    path: 'workouts',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/workouts/components/workouts-placeholder')
        .then(m => m.WorkoutsPlaceholderComponent)
  },
  {
    path: '',
    redirectTo: 'login',
    pathMatch: 'full'
  }
];
