import { Injectable, computed, signal } from '@angular/core';
import { AuthError, Session, SupabaseClient, createClient } from '@supabase/supabase-js';

import { environment } from '../../../environments/environment';

/**
 * Sign-in with a 6-digit email code through Supabase Auth (API.md 2.1). supabase-js is used for
 * sign-in only; all data goes through Spring Boot (PLAN.md section 2).
 * <p>
 * Supabase keeps the session and refreshes it, so the code is asked once per device. The
 * session lives under "msal2.auth" in localStorage, never under a "mizaniya" key (CLAUDE.md rule 15).
 */
@Injectable({ providedIn: 'root' })
export class SupabaseAuth {
  static readonly STORAGE_KEY = 'msal2.auth';

  private readonly client: SupabaseClient = createClient(environment.supabaseUrl, environment.supabasePublishableKey, {
    auth: {
      storageKey: SupabaseAuth.STORAGE_KEY,
      persistSession: true,
      autoRefreshToken: true,
      // Until SMTP sends the code (phase 10), the email holds a link; opening it signs in here
      detectSessionInUrl: true,
    },
  });

  private readonly session = signal<Session | null>(null);
  private readonly ready = signal(false);

  /** True once the saved session (if any) has been read. */
  readonly isReady = this.ready.asReadonly();
  readonly isSignedIn = computed(() => this.session() !== null);
  readonly email = computed(() => this.session()?.user.email ?? null);

  constructor() {
    this.client.auth.onAuthStateChange((_event, session) => this.session.set(session));
    void this.client.auth.getSession().then(({ data }) => {
      this.session.set(data.session);
      this.ready.set(true);
    });
  }

  /** Waits until the saved session has been read. */
  async whenReady(): Promise<void> {
    if (this.ready()) return;
    const { data } = await this.client.auth.getSession();
    this.session.set(data.session);
    this.ready.set(true);
  }

  /** The current access token (refreshed by supabase-js), or null. */
  async accessToken(): Promise<string | null> {
    const { data } = await this.client.auth.getSession();
    return data.session?.access_token ?? null;
  }

  /** Sends the code. Only invited users exist, so no account is ever created here. */
  async sendCode(email: string): Promise<string | null> {
    const { error } = await this.client.auth.signInWithOtp({
      email: email.trim(),
      // The app's own address (with /M-SAL2.0/ on GitHub Pages), allowed in Supabase's redirect list
      options: { shouldCreateUser: false, emailRedirectTo: document.baseURI },
    });
    return error ? SupabaseAuth.message(error) : null;
  }

  /** Checks the code; on success the session is saved. */
  async verifyCode(email: string, code: string): Promise<string | null> {
    const { error } = await this.client.auth.verifyOtp({ email: email.trim(), token: code, type: 'email' });
    return error ? (error.status === 429 ? SupabaseAuth.message(error) : 'الرمز غير صحيح.') : null;
  }

  async signOut(): Promise<void> {
    await this.client.auth.signOut();
    this.session.set(null);
  }

  /** Arabic messages for Supabase errors (S-01). */
  static message(error: AuthError): string {
    if (error.status === 429) return 'طلبات كثيرة، حاول بعد قليل.';
    if (error.status === 422 || error.code === 'otp_disabled' || /signups not allowed/i.test(error.message)) {
      return 'هذا البريد غير مدعو لاستخدام التطبيق.';
    }
    if (error.status === 0 || /fetch/i.test(error.message)) return 'لا يوجد اتصال بالإنترنت.';
    return 'تعذّر إرسال الرمز، حاول مرة أخرى.';
  }
}
