import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';
import { onboardingGuard } from './core/guards/onboarding.guard';
import { LoginComponent } from './features/auth/components/login';

export const routes: Routes = [
  {
    path: 'login',
    component: LoginComponent
  },
  {
    path: 'register',
    loadComponent: () =>
      import('./features/auth/components/register')
        .then(m => m.RegisterComponent)
  },
  {
    path: 'onboarding',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/onboarding/components/onboarding')
        .then(m => m.OnboardingComponent)
  },
  {
    // Shell is eager, guards run once here, all tabs inherit protection
    path: 'home',
    canActivate: [authGuard, onboardingGuard],
    loadComponent: () =>
      import('./features/main/components/main-shell')
        .then(m => m.MainShellComponent),
    children: [
      {
        path: '',
        redirectTo: 'workouts',
        pathMatch: 'full'
      },
      {
        path: 'weight-log',
        loadComponent: () =>
          import('./features/main/components/weight-log/weight-log')
            .then(m => m.WeightLogComponent)
      },
      {
        path: 'workouts',
        loadComponent: () =>
          import('./features/main/components/workouts/workouts-main')
            .then(m => m.WorkoutsMainComponent)
      },
      {
        path: 'goals',
        loadComponent: () =>
          import('./features/main/components/goals/goals')
            .then(m => m.GoalsComponent)
      }
    ]
  },
  {
    path: 'settings',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/main/components/settings/settings')
        .then(m => m.SettingsComponent)
  },
  {
    path: '',
    redirectTo: 'login',
    pathMatch: 'full'
  }
];
