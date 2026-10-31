import { Routes } from '@angular/router';

import { authGuard, guestGuard } from './core/auth/guards';
import { serviceGuard } from './core/permissions/service-guard';
import { LastTab } from './features/shell/last-tab';

/** The routes of SCREENS.md section 2. Screens without a page yet show a placeholder (phase 5). */
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
        loadComponent: () => import('./features/shell/placeholder-page').then((m) => m.PlaceholderPage),
        data: { title: 'الميزانية الشهرية' },
      },
      {
        path: 'expenses',
        children: [
          { path: '', pathMatch: 'full', redirectTo: 'home' },
          {
            path: 'home',
            canActivate: [serviceGuard('SRV-001')],
            loadComponent: () => import('./features/shell/placeholder-page').then((m) => m.PlaceholderPage),
            data: { title: 'الرئيسية' },
          },
          {
            path: 'entries',
            canActivate: [serviceGuard('SRV-001')],
            loadComponent: () => import('./features/shell/placeholder-page').then((m) => m.PlaceholderPage),
            data: { title: 'المصاريف' },
          },
          {
            path: 'stats',
            canActivate: [serviceGuard('SRV-006')],
            loadComponent: () => import('./features/shell/placeholder-page').then((m) => m.PlaceholderPage),
            data: { title: 'الإحصاءات' },
          },
        ],
      },
      {
        path: 'admin',
        canActivate: [serviceGuard('SRV-009')],
        loadComponent: () => import('./features/shell/placeholder-page').then((m) => m.PlaceholderPage),
        data: { title: 'الإدارة' },
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
