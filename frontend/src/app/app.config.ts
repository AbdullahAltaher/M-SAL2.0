import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { ApplicationConfig, inject, provideAppInitializer, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter, withComponentInputBinding } from '@angular/router';

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
  ],
};
