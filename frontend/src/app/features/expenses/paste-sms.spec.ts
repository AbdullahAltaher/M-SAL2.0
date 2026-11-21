import { looksLikeBankMessage, readBankMessage } from './paste-sms';

const ADIB = 'Transaction of AED 14.75 debited from your a/c *264 at FESCO SUPERMARKET LLC SH ARJAH AE. Avl Bal is AED 689.29';

describe('Pasting a bank message the shortcut missed (BANK-SMS.md B-23)', () => {
  const nav = navigator as unknown as Record<string, unknown>;
  afterEach(() => delete nav['clipboard']);

  it('only a bank-looking text fills the box when the sheet opens', () => {
    expect(looksLikeBankMessage(ADIB)).toBe(true);
    expect(looksLikeBankMessage('رقم الطلب 4411')).toBe(false);
    expect(looksLikeBankMessage('AED 10 on sale')).toBe(false);
  });

  it('reads the clipboard in the tap, and gives up quietly when it cannot', async () => {
    nav['clipboard'] = { readText: () => Promise.resolve('  ' + ADIB + '\n') };
    expect(await readBankMessage()).toBe(ADIB);
    nav['clipboard'] = { readText: () => Promise.resolve('hello') };
    expect(await readBankMessage()).toBe('');
    expect(await readBankMessage(true)).toBe('hello');
    nav['clipboard'] = { readText: () => Promise.reject(new Error('denied')) };
    expect(await readBankMessage(true)).toBe('');
  });
});
