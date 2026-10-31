import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, from, switchMap, throwError } from 'rxjs';

import { environment } from '../../../environments/environment';
import { SupabaseAuth } from '../auth/supabase-auth';

/**
 * Adds the Supabase JWT to every API call (API.md section 1). A 401 ({@code SYS-E001}) means the
 * session is over: sign out and go to the sign-in screen.
 */
export const apiInterceptor: HttpInterceptorFn = (request, next) => {
  if (!request.url.startsWith(environment.apiUrl)) {
    return next(request);
  }
  const auth = inject(SupabaseAuth);
  const router = inject(Router);
  return from(auth.accessToken()).pipe(
    switchMap((token) => next(token ? request.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : request)),
    catchError((error) => {
      if (error?.status === 401) {
        void auth.signOut().then(() => router.navigateByUrl('/login'));
      }
      return throwError(() => error);
    }),
  );
};
