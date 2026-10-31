import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { environment } from '../../../environments/environment';
import { SupabaseAuth } from '../../core/auth/supabase-auth';
import { testMe } from '../../testing/test-me';
import { Shell } from './shell';

describe('Shell (the shared header)', () => {
  it('shows the people menu with the open budget, "new person", and the two tabs', async () => {
    await TestBed.configureTestingModule({
      imports: [Shell],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting(),
        { provide: SupabaseAuth, useValue: { signOut: async () => undefined } }],
    }).compileComponents();
    const fixture = TestBed.createComponent(Shell);
    const http = TestBed.inject(HttpTestingController);
    http.expectOne(`${environment.apiUrl}/me`).flush(testMe(['SRV-000', 'SRV-001', 'SRV-002']));
    http.expectOne(`${environment.apiUrl}/budgets`).flush([
      { id: 'b1', name: 'الأساسي', monthLabel: 'نوفمبر 2026', sortOrder: 1, version: 0 },
      { id: 'b2', name: 'أمي', monthLabel: 'نوفمبر 2026', sortOrder: 2, version: 0 },
    ]);
    await new Promise((r) => setTimeout(r));
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    const select = el.querySelector<HTMLSelectElement>('#profileSelect')!;
    expect([...select.options].map((o) => o.text)).toEqual(['الأساسي', 'أمي', '＋ شخص جديد…']);
    expect(select.value).toBe('b2');
    expect([...el.querySelectorAll('.tab')].map((t) => t.textContent?.trim())).toEqual(['الميزانية', 'المصاريف']);
    expect(el.querySelector('.kb-proxy')).not.toBeNull();
  });
});
