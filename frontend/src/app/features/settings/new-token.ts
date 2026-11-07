import { Injectable, signal } from '@angular/core';

/**
 * The shortcut's token right after it was made (BANK-SMS.md B-19), so the setup steps can offer
 * to copy it. Kept in memory only: never in storage or the browser history, and gone on reload.
 */
@Injectable({ providedIn: 'root' })
export class NewSmsToken {
  readonly value = signal<string | null>(null);
}
