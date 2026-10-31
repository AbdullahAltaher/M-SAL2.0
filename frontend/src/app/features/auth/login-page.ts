import { Component, OnDestroy, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';

import { SupabaseAuth } from '../../core/auth/supabase-auth';
import { SessionStore } from '../../core/session/session-store';
import { toLatinDigits } from '../../core/util/amounts';

/**
 * S-01: sign-in with a 6-digit code by email (SCREENS.md section 3, API.md 2.1).
 * Step 1 asks for the email, step 2 for the code, which iOS can fill from the email.
 */
@Component({
  selector: 'app-login-page',
  imports: [FormsModule],
  templateUrl: './login-page.html',
  styleUrl: './login-page.scss',
})
export class LoginPage implements OnDestroy {
  static readonly RESEND_SECONDS = 60;

  private readonly auth = inject(SupabaseAuth);
  private readonly session = inject(SessionStore);
  private readonly router = inject(Router);

  protected readonly step = signal<'email' | 'code'>('email');
  protected readonly email = signal('');
  protected readonly code = signal('');
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(this.session.lastFailure());
  protected readonly waitSeconds = signal(0);
  protected readonly canResend = computed(() => this.waitSeconds() === 0 && !this.busy());

  private timer: ReturnType<typeof setInterval> | null = null;

  protected async send(): Promise<void> {
    const email = this.email().trim();
    if (!email || this.busy()) return;
    this.busy.set(true);
    this.error.set(null);
    const failure = await this.auth.sendCode(email);
    this.busy.set(false);
    if (failure) {
      this.error.set(failure);
      return;
    }
    this.code.set('');
    this.step.set('code');
    this.startWait();
  }

  /** Keeps only digits (Arabic ones too), and checks the code as soon as it has 6. */
  protected onCode(value: string): void {
    const digits = toLatinDigits(value).replace(/\D/g, '').slice(0, 6);
    this.code.set(digits);
    if (digits.length === 6) void this.verify();
  }

  protected async verify(): Promise<void> {
    if (this.code().length !== 6 || this.busy()) return;
    this.busy.set(true);
    this.error.set(null);
    const failure = await this.auth.verifyCode(this.email(), this.code());
    if (failure) {
      this.busy.set(false);
      this.error.set(failure);
      return;
    }
    try {
      await this.session.load();
      await this.router.navigateByUrl('/');
    } catch (error) {
      // Signed in to Supabase but not allowed in the app (such as SRV-000-E001)
      this.error.set((error as { message: string }).message);
      this.session.clear();
      await this.auth.signOut();
      this.step.set('email');
    } finally {
      this.busy.set(false);
    }
  }

  protected changeEmail(): void {
    this.step.set('email');
    this.error.set(null);
    this.stopWait();
  }

  ngOnDestroy(): void {
    this.stopWait();
  }

  private startWait(): void {
    this.stopWait();
    this.waitSeconds.set(LoginPage.RESEND_SECONDS);
    this.timer = setInterval(() => {
      const left = this.waitSeconds() - 1;
      this.waitSeconds.set(Math.max(0, left));
      if (left <= 0) this.stopWait();
    }, 1000);
  }

  private stopWait(): void {
    if (this.timer) clearInterval(this.timer);
    this.timer = null;
  }
}
