import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { ApplicationConfig, inject, isDevMode, provideAppInitializer, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { provideServiceWorker } from '@angular/service-worker';

import { routes } from './app.routes';
import { apiInterceptor } from './core/api/api-interceptor';
import { SupabaseAuth } from './core/auth/supabase-auth';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    // Read the saved session, and a sign-in link's tokens in the address (#access_token=...),
    // before the router starts: its first redirect would otherwise remove them from the URL
    provideAppInitializer(() => inject(SupabaseAuth).whenReady()),
    provideRouter(routes, withComponentInputBinding()),
    provideHttpClient(withInterceptors([apiInterceptor])),
    // Angular's worker plus the icon badge (public/msal-sw.js). Its scope is /M-SAL2.0/ where
    // the app is served, never the root, so M-SAL is untouched (CLAUDE.md rule 15)
    provideServiceWorker('msal-sw.js', { enabled: !isDevMode(), registrationStrategy: 'registerWhenStable:30000' }),
  ],
};
