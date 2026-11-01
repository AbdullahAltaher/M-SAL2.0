import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { SupabaseAuth } from '../../core/auth/supabase-auth';
import { SessionStore } from '../../core/session/session-store';
import { LoginPage } from './login-page';

class FakeAuth {
  sendCode = vi.fn(async () => null as string | null);
  verifyCode = vi.fn(async () => 'الرمز غير صحيح.' as string | null);
  signOut = vi.fn(async () => undefined);
}

describe('LoginPage (S-01)', () => {
  let auth: FakeAuth;

  beforeEach(async () => {
    auth = new FakeAuth();
    await TestBed.configureTestingModule({
      imports: [LoginPage],
      providers: [provideRouter([]), { provide: SupabaseAuth, useValue: auth }],
    }).compileComponents();
  });

  it('asks for the email, then for the code', async () => {
    const fixture = TestBed.createComponent(LoginPage);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('أرسل الرمز');

    const page = fixture.componentInstance as unknown as { email: { set(v: string): void }; send(): Promise<void> };
    page.email.set(' sara@test.local ');
    await page.send();
    await fixture.whenStable();
    expect(auth.sendCode).toHaveBeenCalledWith('sara@test.local');
    expect(el.textContent).toContain('الرمز');
    expect(el.querySelector('input#code')?.getAttribute('autocomplete')).toBe('one-time-code');
  });

  it('keeps only digits, Arabic ones too, and checks the code at 6 digits', async () => {
    const fixture = TestBed.createComponent(LoginPage);
    const page = fixture.componentInstance as unknown as {
      email: { set(v: string): void };
      code: () => string;
      error: () => string | null;
      onCode(v: string): void;
    };
    page.email.set('sara@test.local');
    page.onCode('12 3');
    expect(page.code()).toBe('123');
    page.onCode('١٢٣٤٥٦');
    expect(page.code()).toBe('123456');
    await fixture.whenStable();
    await vi.waitFor(() => expect(page.error()).toBe('الرمز غير صحيح.'));
    expect(auth.verifyCode).toHaveBeenCalledWith('sara@test.local', '123456');
  });

  it('"عندي رمز" opens the code box without sending an email', async () => {
    const fixture = TestBed.createComponent(LoginPage);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    const page = fixture.componentInstance as unknown as { email: { set(v: string): void } };
    page.email.set('sara@test.local');
    await fixture.whenStable();
    const button = [...el.querySelectorAll<HTMLButtonElement>('button')].find((b) => b.textContent?.trim() === 'عندي رمز')!;
    button.click();
    await fixture.whenStable();
    expect(auth.sendCode).not.toHaveBeenCalled();
    expect(el.querySelector('input#code')).not.toBeNull();
  });

  it('shows why the user was sent back to sign-in', async () => {
    TestBed.inject(SessionStore).clear('هذا البريد غير مدعو لاستخدام التطبيق.');
    const fixture = TestBed.createComponent(LoginPage);
    await fixture.whenStable();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('هذا البريد غير مدعو لاستخدام التطبيق.');
  });
});
