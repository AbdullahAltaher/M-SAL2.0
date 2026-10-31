import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { environment } from '../../../environments/environment';
import { SupabaseAuth } from '../../core/auth/supabase-auth';
import { Me, SessionStore } from '../../core/session/session-store';
import { Shell } from './shell';

function me(services: string[]): Me {
  return {
    id: 'u1', email: 'sara@test.local', displayName: null, timeZone: 'Asia/Dubai', today: '2026-11-11',
    roles: ['USER'], lastBudgetId: 'b1', exportReminder: { due: false, days: 0, never: true }, version: 1,
    services: Object.fromEntries(services.map((s) => [s, { read: true, write: true }])),
  };
}

describe('Shell', () => {
  async function render(services: string[]): Promise<HTMLElement> {
    await TestBed.configureTestingModule({
      imports: [Shell],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting(),
        { provide: SupabaseAuth, useValue: { signOut: async () => undefined } }],
    }).compileComponents();
    const loading = TestBed.inject(SessionStore).load();
    TestBed.inject(HttpTestingController).expectOne(`${environment.apiUrl}/me`).flush(me(services));
    await loading;
    const fixture = TestBed.createComponent(Shell);
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  it('shows the two tabs and hides admin from a normal user', async () => {
    const el = await render(['SRV-000', 'SRV-001', 'SRV-002']);
    expect(el.querySelector('.tabs')?.textContent).toContain('الميزانية');
    expect(el.querySelector('.tabs')?.textContent).toContain('المصاريف');
    expect(el.textContent).not.toContain('الإدارة');
  });

  it('shows admin with SRV-009', async () => {
    const el = await render(['SRV-000', 'SRV-001', 'SRV-002', 'SRV-009']);
    expect(el.textContent).toContain('الإدارة');
  });
});
