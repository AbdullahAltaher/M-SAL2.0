import { TestBed } from '@angular/core/testing';

import { CardPhotos, accountPhotoKey, budgetPhotoKey } from './card-photos';

describe('CardPhotos (DESIGN.md D-15 to D-17, D-26)', () => {
  it('keeps one photo per person and one per account', () => {
    expect(budgetPhotoKey('b1')).toBe('budget:b1');
    expect(accountPhotoKey('a1')).toBe('account:a1');
    expect(budgetPhotoKey('x')).not.toBe(accountPhotoKey('x'));
  });

  it('leaves the card blue when the device cannot keep photos', async () => {
    const saved = (globalThis as { indexedDB?: unknown }).indexedDB;
    (globalThis as { indexedDB?: unknown }).indexedDB = undefined;
    try {
      const photos = TestBed.inject(CardPhotos);
      expect(photos.get('budget:b1')).toBeNull();
      await new Promise((r) => setTimeout(r));
      expect(photos.get('budget:b1')).toBeNull();
      await expect(photos.remove('budget:b1')).resolves.toBeUndefined();
    } finally {
      (globalThis as { indexedDB?: unknown }).indexedDB = saved;
    }
  });
});
