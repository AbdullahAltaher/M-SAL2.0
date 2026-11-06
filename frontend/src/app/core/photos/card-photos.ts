import { Injectable, signal } from '@angular/core';

/** How the photo sits behind the card (DESIGN.md D-16) */
export type PhotoStyle = 'dim' | 'glass' | 'tint';

export interface CardPhoto {
  url: string;
  style: PhotoStyle;
}

interface Stored {
  key: string;
  blob: Blob;
  style: PhotoStyle;
}

/** IndexedDB on this device only, never a "mizaniya" name (CLAUDE.md rule 15) */
const DB = 'msal2.photos';
const STORE = 'photos';
/** The longest side after shrinking: enough for a sharp card on any iPhone */
const MAX_SIDE = 1200;

/** The key of a person's budget card, or of an account's card (D-26). */
export const budgetPhotoKey = (budgetId: string) => 'budget:' + budgetId;
export const accountPhotoKey = (accountId: string) => 'account:' + accountId;

/**
 * Photos behind the big cards (DESIGN.md D-15 to D-17, D-26): one per person's budget card and
 * one per account's card, chosen from the album, shrunk, and kept on this device only. Nothing
 * goes to the server, so a photo does not show on another device and goes away with the app.
 */
@Injectable({ providedIn: 'root' })
export class CardPhotos {
  private readonly cache = signal<ReadonlyMap<string, CardPhoto>>(new Map());
  private readonly asked = new Set<string>();
  private db: Promise<IDBDatabase | null> | null = null;

  /** The photo for a card, loaded on first ask; null when there is none. */
  get(key: string): CardPhoto | null {
    if (!this.asked.has(key)) {
      this.asked.add(key);
      void this.load(key);
    }
    return this.cache().get(key) ?? null;
  }

  async set(key: string, file: Blob, style: PhotoStyle): Promise<void> {
    const blob = await shrink(file);
    await this.write({ key, blob, style });
    this.put(key, { url: URL.createObjectURL(blob), style });
  }

  async setStyle(key: string, style: PhotoStyle): Promise<void> {
    const stored = await this.read(key);
    if (!stored) return;
    await this.write({ ...stored, style });
    const current = this.cache().get(key);
    if (current) this.put(key, { ...current, style }, false);
  }

  async remove(key: string): Promise<void> {
    const db = await this.open();
    if (db) await done(db.transaction(STORE, 'readwrite').objectStore(STORE).delete(key));
    this.put(key, null);
  }

  private async load(key: string): Promise<void> {
    const stored = await this.read(key);
    if (stored) this.put(key, { url: URL.createObjectURL(stored.blob), style: stored.style });
  }

  private put(key: string, photo: CardPhoto | null, revoke = true): void {
    const next = new Map(this.cache());
    const old = next.get(key);
    if (old && revoke && old.url !== photo?.url) URL.revokeObjectURL(old.url);
    if (photo) next.set(key, photo);
    else next.delete(key);
    this.cache.set(next);
  }

  private async read(key: string): Promise<Stored | null> {
    const db = await this.open();
    if (!db) return null;
    return (await done<Stored | undefined>(db.transaction(STORE).objectStore(STORE).get(key))) ?? null;
  }

  private async write(value: Stored): Promise<void> {
    const db = await this.open();
    if (!db) throw new Error('تعذّر حفظ الصورة على هذا الجهاز.');
    await done(db.transaction(STORE, 'readwrite').objectStore(STORE).put(value));
  }

  /** Null where there is no IndexedDB (some private windows): cards just stay blue. */
  private open(): Promise<IDBDatabase | null> {
    this.db ??= new Promise((resolve) => {
      try {
        const request = indexedDB.open(DB, 1);
        request.onupgradeneeded = () => request.result.createObjectStore(STORE, { keyPath: 'key' });
        request.onsuccess = () => resolve(request.result);
        request.onerror = () => resolve(null);
      } catch {
        resolve(null);
      }
    });
    return this.db;
  }
}

function done<T = unknown>(request: IDBRequest<T>): Promise<T> {
  return new Promise((resolve, reject) => {
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error);
  });
}

/** Down to {@link MAX_SIDE} pixels as JPEG, the right way up. */
async function shrink(file: Blob): Promise<Blob> {
  const bitmap = await createImageBitmap(file, { imageOrientation: 'from-image' });
  const scale = Math.min(1, MAX_SIDE / Math.max(bitmap.width, bitmap.height));
  const canvas = document.createElement('canvas');
  canvas.width = Math.round(bitmap.width * scale);
  canvas.height = Math.round(bitmap.height * scale);
  canvas.getContext('2d')!.drawImage(bitmap, 0, 0, canvas.width, canvas.height);
  bitmap.close();
  return new Promise((resolve, reject) =>
    canvas.toBlob((b) => (b ? resolve(b) : reject(new Error('تعذّر قراءة الصورة.'))), 'image/jpeg', 0.82));
}
