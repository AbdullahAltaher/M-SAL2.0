import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { environment } from '../../../environments/environment';
import { SessionStore } from '../../core/session/session-store';
import { testMe } from '../../testing/test-me';
import { MoreSheet } from './menu-sheets';

describe('MoreSheet (SH-03)', () => {
  async function render(services: string[]): Promise<HTMLElement> {
    await TestBed.configureTestingModule({
      imports: [MoreSheet],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    const loading = TestBed.inject(SessionStore).load();
    TestBed.inject(HttpTestingController).expectOne(`${environment.apiUrl}/me`).flush(testMe(services));
    await loading;
    const fixture = TestBed.createComponent(MoreSheet);
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  it('lists the menu and hides admin from a normal user', async () => {
    const text = (await render(['SRV-000', 'SRV-001'])).textContent ?? '';
    for (const row of ['الحسابات', 'الفئات', 'المحذوفات', 'إضافة دورة', 'الإيصال', 'البيانات والإعدادات']) {
      expect(text).toContain(row);
    }
    expect(text).not.toContain('الإدارة');
  });

  it('shows admin with SRV-009', async () => {
    expect((await render(['SRV-000', 'SRV-001', 'SRV-009'])).textContent).toContain('الإدارة');
  });
});
