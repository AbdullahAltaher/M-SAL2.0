import { Injectable, signal } from '@angular/core';

export type GratitudeType = 'آية' | 'دعاء' | 'ذكر';

/** One text of gratitude-texts.json (docs/GRATITUDE-TEXTS.md) */
export interface GratitudeText {
  id: string;
  type: GratitudeType;
  text: string;
  source: string;
  grade: string;
  /** 60 letters or fewer without marks: fits the card after adding */
  short: boolean;
  /** Some words of the verse, in order */
  part?: boolean;
  /** A warning tone (waste, little thanks): only on the salary card */
  salaryOnly?: boolean;
}

export type GratitudeCard =
  | { kind: 'added'; key: number; text: GratitudeText; note: string; amount: string | null }
  | { kind: 'salary'; key: number; verse: GratitudeText | null; dua: GratitudeText | null; amount: string | null };

/** How long the card after adding stays (DESIGN.md D-42) */
export const SHOW_MS = 4500;
/** After the coin reaches the account card (D-10) */
export const AFTER_COIN_MS = 950;
const RECENT_KEY = 'msal2.gratitude.recent';
const RECENT_MAX = 30;

/**
 * A random text not shown lately: after adding, a short one without a warning tone; on the
 * salary card, any of the given type.
 */
export function pickText(list: GratitudeText[], recent: string[], where: 'added' | 'verse' | 'dua',
  random: () => number = Math.random): GratitudeText | null {
  const pool = list.filter((t) => where === 'added' ? t.short && !t.salaryOnly
    : where === 'verse' ? t.type === 'آية' : t.type !== 'آية');
  if (!pool.length) return null;
  const fresh = pool.filter((t) => !recent.includes(t.id));
  const from = fresh.length ? fresh : pool;
  return from[Math.floor(random() * from.length)];
}

/**
 * Thanks after adding (D-42): a card in the middle of the screen after every entry added
 * (expense, income, shortcut, copy, an approved bank message), and a full card once when a new
 * cycle starts ("وصل الراتب"). The texts load on first use, in their own file.
 */
@Injectable({ providedIn: 'root' })
export class Gratitude {
  readonly card = signal<GratitudeCard | null>(null);

  private texts: Promise<GratitudeText[]> | null = null;
  private timer: ReturnType<typeof setTimeout> | null = null;
  private key = 0;

  /** After an entry is added: "أُضيف 18.00 د.إ", or a note such as "انتهت المراجعة". */
  added(note: string, amount: string | null, delayMs = AFTER_COIN_MS): void {
    void this.load().then((list) => {
      const text = pickText(list, this.recent(), 'added');
      if (!text) return;
      this.show({ kind: 'added', key: ++this.key, text, note, amount }, delayMs, SHOW_MS);
    });
  }

  /** A new cycle: a verse in a frame and a dua or dhikr, until "الحمد لله". */
  salary(amount: string | null): void {
    void this.load().then((list) => {
      const verse = pickText(list, this.recent(), 'verse');
      const dua = pickText(list, this.recent(), 'dua');
      this.show({ kind: 'salary', key: ++this.key, verse, dua, amount }, 300, null);
    });
  }

  close(): void {
    this.clear();
    this.card.set(null);
  }

  private show(card: GratitudeCard, delayMs: number, hideAfter: number | null): void {
    this.clear();
    this.timer = setTimeout(() => {
      this.card.set(card);
      this.remember(card.kind === 'added' ? [card.text] : [card.verse, card.dua]);
      this.timer = hideAfter ? setTimeout(() => this.card.update((c) => (c?.key === card.key ? null : c)), hideAfter) : null;
    }, delayMs);
  }

  private clear(): void {
    if (this.timer) clearTimeout(this.timer);
    this.timer = null;
  }

  private load(): Promise<GratitudeText[]> {
    this.texts ??= import('./gratitude-texts.json').then((m) => m.default as GratitudeText[])
      .catch(() => {
        this.texts = null;
        return [];
      });
    return this.texts;
  }

  /** The last texts shown, on this device only (msal2.* keys, CLAUDE.md rule 15) */
  private recent(): string[] {
    try {
      const list = JSON.parse(localStorage.getItem(RECENT_KEY) ?? '[]');
      return Array.isArray(list) ? list : [];
    } catch {
      return [];
    }
  }

  private remember(shown: (GratitudeText | null)[]): void {
    try {
      const ids = [...shown.filter((t): t is GratitudeText => !!t).map((t) => t.id), ...this.recent()];
      localStorage.setItem(RECENT_KEY, JSON.stringify([...new Set(ids)].slice(0, RECENT_MAX)));
    } catch {
      // Private mode: texts may repeat sooner
    }
  }
}
