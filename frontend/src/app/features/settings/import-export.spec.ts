import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { environment } from '../../../environments/environment';
import { ImportPreview } from '../../core/api/models';
import { Dialogs } from '../../shared/sheet/dialogs';
import { Sheets } from '../../shared/sheet/sheets';
import { Toasts } from '../../shared/toast/toast';
import { ImportExport } from './import-export';
import { ImportSheet } from './import-sheet';

const COUNTS = { profiles: 1, blocks: 3, rows: 12, accounts: 2, cycles: 4, entries: 186, deleted: 3 };
const PREVIEW: ImportPreview = {
  kind: 'STATE', newer: true, counts: COUNTS,
  current: { profiles: 1, blocks: 1, rows: 1, accounts: 1, cycles: 0, entries: 0, deleted: 0 },
  warnings: [{ code: 'CATEGORY_MISSING', count: 2, message: 'مصاريف بفئة غير موجودة، ستُربط بفئة «أخرى» (2)' }],
  target: null,
};

describe('ImportSheet (SH-09)', () => {
  async function render(preview: ImportPreview): Promise<HTMLElement> {
    await TestBed.configureTestingModule({ imports: [ImportSheet] }).compileComponents();
    const fixture = TestBed.createComponent(ImportSheet);
    fixture.componentRef.setInput('preview', preview);
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  it('shows the file next to the current data, the warnings and the newer-file note', async () => {
    const el = await render(PREVIEW);
    const rows = [...el.querySelectorAll('tbody tr')].map((r) => [...r.querySelectorAll('td')].map((td) => td.textContent?.trim()));
    expect(rows[0]).toEqual(['الأشخاص', '1', '1']);
    expect(rows[5]).toEqual(['المصاريف', '186', '0']);
    expect(rows[6]).toEqual(['المحذوفات', '3', '0']);
    expect(el.textContent).toContain('ملف تصدير كامل.');
    expect(el.textContent).toContain('من نسخة أحدث');
    expect(el.textContent).toContain('«أخرى» (2)');
  });

  it('closes with the chosen mode', async () => {
    const el = await render(PREVIEW);
    const sheets = TestBed.inject(Sheets);
    const result = sheets.open(ImportSheet, 'استيراد');
    const buttons = [...el.querySelectorAll<HTMLButtonElement>('.toolbar button')];
    expect(buttons.map((b) => b.textContent?.trim())).toEqual(['دمج مع بياناتي', 'استبدال الكل', 'إلغاء']);
    buttons[1].click();
    expect(await result).toBe('REPLACE');
  });

  it('names the open budget for an old one-person file', async () => {
    const el = await render({ ...PREVIEW, kind: 'SINGLE', newer: false, target: { id: 'b1', name: 'الأساسي' } });
    expect(el.textContent).toContain('ملف بصيغة قديمة لشخص واحد');
    expect(el.textContent).toContain('دمج في «الأساسي»');
    expect(el.textContent).toContain('استبدال ميزانية «الأساسي»');
  });
});

describe('ImportExport', () => {
  let http: HttpTestingController;
  let io: ImportExport;
  const file = new File(['{}'], 'ميزانية-2026-11-01.json', { type: 'application/json' });

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    io = TestBed.inject(ImportExport);
  });

  it('a refused file changes nothing and says why', async () => {
    const toasts = TestBed.inject(Toasts);
    const done = io.open(file);
    http.expectOne(`${environment.apiUrl}/imports/preview`)
      .flush({ code: 'SRV-007-E002', message: 'صيغة الملف غير معروفة.' }, { status: 400, statusText: 'Bad Request' });
    await done;
    expect(toasts.current()?.text).toBe('صيغة الملف غير معروفة. لم يتغيّر شيء.');
    expect(TestBed.inject(Sheets).current()).toBeNull();
    http.verify();
  });

  it('replacing asks first, and a "no" goes back to the preview', async () => {
    const sheets = TestBed.inject(Sheets);
    const dialogs = TestBed.inject(Dialogs);
    const confirm = vi.spyOn(dialogs, 'confirm').mockResolvedValueOnce(false);
    const done = io.open(file);
    const preview = http.expectOne(`${environment.apiUrl}/imports/preview`);
    expect(preview.request.body).toBeInstanceOf(FormData);
    preview.flush(PREVIEW);
    await vi.waitFor(() => expect(sheets.current()?.component).toBe(ImportSheet));
    sheets.close('REPLACE');
    sheets.finishClose();
    await vi.waitFor(() => expect(confirm).toHaveBeenCalledOnce());
    expect(confirm.mock.calls[0][0]).toContain('استبدال كل البيانات الحالية');
    await vi.waitFor(() => expect(sheets.current()?.component).toBe(ImportSheet));
    sheets.close();
    await done;
    http.expectNone(`${environment.apiUrl}/imports?mode=REPLACE`);
  });

  it('the file name is today in the user\'s time zone', () => {
    expect(io.fileName()).toMatch(/^ميزانية-\d{4}-\d{2}-\d{2}\.json$/);
  });
});
