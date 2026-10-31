import { Routes } from '@angular/router';

import { authGuard, guestGuard } from './core/auth/guards';
import { serviceGuard } from './core/permissions/service-guard';
import { LastTab } from './features/shell/last-tab';

/** The routes of SCREENS.md section 2. */
export const routes: Routes = [
  {
    path: 'login',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/auth/login-page').then((m) => m.LoginPage),
  },
  {
    path: '',
    canActivate: [authGuard, serviceGuard('SRV-000')],
    loadComponent: () => import('./features/shell/shell').then((m) => m.Shell),
    children: [
      { path: '', pathMatch: 'full', redirectTo: () => LastTab.path() },
      {
        path: 'budget',
        canActivate: [serviceGuard('SRV-002')],
        loadComponent: () => import('./features/budget/budget-page').then((m) => m.BudgetPage),
      },
      {
        path: 'expenses',
        loadComponent: () => import('./features/expenses/expenses-layout').then((m) => m.ExpensesLayout),
        children: [
          { path: '', pathMatch: 'full', redirectTo: 'home' },
          {
            path: 'home',
            canActivate: [serviceGuard('SRV-001')],
            loadComponent: () => import('./features/expenses/home-view').then((m) => m.HomeView),
          },
          {
            path: 'entries',
            canActivate: [serviceGuard('SRV-001')],
            loadComponent: () => import('./features/expenses/entries-view').then((m) => m.EntriesView),
          },
          {
            path: 'stats',
            canActivate: [serviceGuard('SRV-006')],
            loadComponent: () => import('./features/stats/stats-view').then((m) => m.StatsView),
          },
        ],
      },
      {
        path: 'admin',
        canActivate: [serviceGuard('SRV-009')],
        loadComponent: () => import('./features/admin/admin-page').then((m) => m.AdminPage),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
