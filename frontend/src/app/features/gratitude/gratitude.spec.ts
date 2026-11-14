import { TestBed } from '@angular/core/testing';

import { AFTER_COIN_MS, Gratitude, GratitudeText, SHOW_MS, pickText } from './gratitude';
import TEXTS from './gratitude-texts.json';

const t = (id: string, type: GratitudeText['type'], short = true, salaryOnly = false): GratitudeText =>
  ({ id, type, text: id, source: '', grade: '', short, ...(salaryOnly ? { salaryOnly } : {}) });

describe('Thanks after adding (DESIGN.md D-42)', () => {
  const list = [t('q1', 'آية'), t('q2', 'آية', false), t('q3', 'آية', true, true), t('d1', 'دعاء'), t('z1', 'ذكر', false)];

  it('after adding: a short text without a warning tone', () => {
    const picked = new Set([0, 0.3, 0.6, 0.99].map((r) => pickText(list, [], 'added', () => r)?.id));
    expect([...picked].sort()).toEqual(['d1', 'q1']);
  });

  it('the salary card: a verse in the frame, a dua or dhikr under it, long ones too', () => {
    expect(new Set([0, 0.4, 0.99].map((r) => pickText(list, [], 'verse', () => r)?.id))).toEqual(new Set(['q1', 'q2', 'q3']));
    expect(new Set([0, 0.99].map((r) => pickText(list, [], 'dua', () => r)?.id))).toEqual(new Set(['d1', 'z1']));
  });

  it('skips the texts shown lately, unless all were', () => {
    expect(pickText(list, ['q1'], 'added', () => 0)?.id).toBe('d1');
    expect(pickText(list, ['q1', 'd1'], 'added', () => 0)?.id).toBe('q1');
  });

  it('the list: 80 texts, each with a source, the duas and adhkar from Bukhari or Muslim', () => {
    const texts = TEXTS as GratitudeText[];
    expect(texts.length).toBe(80);
    expect(new Set(texts.map((x) => x.id)).size).toBe(texts.length);
    for (const x of texts) {
      expect(x.text.trim()).not.toBe('');
      expect(x.source.trim()).not.toBe('');
      if (x.type !== 'آية') expect(x.source).toMatch(/البخاري|مسلم/);
    }
  });

  describe('the card', () => {
    beforeEach(() => vi.useFakeTimers());
    afterEach(() => vi.useRealTimers());

    async function settle(ms: number) {
      await vi.advanceTimersByTimeAsync(ms);
    }

    it('comes after the coin, and goes by itself', async () => {
      const g = TestBed.inject(Gratitude);
      // the texts' own file, loaded on first use
      await g['load']();
      g.added('أُضيف', '18.00');
      await settle(AFTER_COIN_MS - 50);
      expect(g.card()).toBeNull();
      await settle(100);
      const card = g.card();
      expect(card?.kind).toBe('added');
      if (card?.kind === 'added') {
        expect(card.amount).toBe('18.00');
        expect(card.text.short && !card.text.salaryOnly).toBe(true);
      }
      await settle(SHOW_MS);
      expect(g.card()).toBeNull();
    });

    it('a new one replaces the one showing', async () => {
      const g = TestBed.inject(Gratitude);
      g.added('أُضيف', '18.00', 0);
      await settle(10);
      g.added('أُضيف', '5.00', 0);
      await settle(10);
      const card = g.card();
      expect(card?.kind === 'added' && card.amount).toBe('5.00');
    });

    it('the salary card stays until "الحمد لله"', async () => {
      const g = TestBed.inject(Gratitude);
      g.salary('3000.00');
      await settle(SHOW_MS * 3);
      const card = g.card();
      expect(card?.kind).toBe('salary');
      if (card?.kind === 'salary') expect(card.verse?.type).toBe('آية');
      g.close();
      expect(g.card()).toBeNull();
    });
  });
});
