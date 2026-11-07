import { Routes } from '@angular/router';

import { authGuard, guestGuard } from './core/auth/guards';
import { serviceGuard } from './core/permissions/service-guard';
import { installGuard } from './core/pwa/install-guard';
import { LastTab } from './features/shell/last-tab';

/** The routes of SCREENS.md section 2. */
export const routes: Routes = [
  {
    path: 'install',
    loadComponent: () => import('./features/install/install-page').then((m) => m.InstallPage),
  },
  {
    // How to set up the iPhone shortcut (BANK-SMS.md B-19): a page of its own, drawn like the install page
    path: 'bank-sms/setup',
    canActivate: [serviceGuard('SRV-011')],
    loadComponent: () => import('./features/settings/shortcut-setup').then((m) => m.ShortcutSetup),
  },
  {
    path: 'login',
    canActivate: [installGuard, guestGuard],
    loadComponent: () => import('./features/auth/login-page').then((m) => m.LoginPage),
  },
  {
    path: '',
    canActivate: [installGuard, authGuard, serviceGuard('SRV-000')],
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
          {
            // Bank messages waiting for review (BANK-SMS.md B-18)
            path: 'pending',
            canActivate: [serviceGuard('SRV-011')],
            loadComponent: () => import('./features/expenses/pending-view').then((m) => m.PendingView),
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
