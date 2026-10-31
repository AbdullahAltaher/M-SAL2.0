import { DOCUMENT } from '@angular/common';
import { Injectable, inject, signal } from '@angular/core';

const KEY = 'msal2.lock';
/** Back after this long in the background locks again (SCREENS.md S-03) */
export const RELOCK_AFTER_MS = 5 * 60 * 1000;

interface Saved {
  credentialId: string;
}

/**
 * S-03: the Face ID lock. A passkey on this device (WebAuthn) that only opens the app here: a
 * privacy lock over the session, not a sign-in (the server still checks the JWT). Nothing
 * goes to the server; the passkey's id is kept in localStorage under "msal2.", never "mizaniya"
 * (CLAUDE.md rule 15).
 */
@Injectable({ providedIn: 'root' })
export class AppLock {
  private readonly document = inject(DOCUMENT);
  private hiddenAt: number | null = null;

  readonly enabled = signal(read() !== null);
  readonly locked = signal(read() !== null);

  constructor() {
    this.document.addEventListener('visibilitychange', () => {
      if (!this.enabled()) return;
      if (this.document.visibilityState === 'hidden') {
        this.hiddenAt = Date.now();
      } else if (this.hiddenAt !== null && Date.now() - this.hiddenAt >= RELOCK_AFTER_MS) {
        this.locked.set(true);
      }
    });
  }

  /** Creates the passkey; Face ID asks once. */
  async enable(email: string): Promise<void> {
    const credential = await navigator.credentials.create({
      publicKey: {
        challenge: random(32),
        rp: { name: 'ميزانية' },
        user: { id: random(16), name: email, displayName: email },
        pubKeyCredParams: [{ type: 'public-key', alg: -7 }, { type: 'public-key', alg: -257 }],
        authenticatorSelection: { authenticatorAttachment: 'platform', userVerification: 'required', residentKey: 'discouraged' },
        timeout: 60000,
      },
    }) as PublicKeyCredential | null;
    if (!credential) throw new Error('no credential');
    localStorage.setItem(KEY, JSON.stringify({ credentialId: toBase64(credential.rawId) } satisfies Saved));
    this.enabled.set(true);
  }

  disable(): void {
    localStorage.removeItem(KEY);
    this.enabled.set(false);
    this.locked.set(false);
  }

  /** "افتح بـ Face ID": true when the device confirmed it is the owner. */
  async unlock(): Promise<boolean> {
    const saved = read();
    if (!saved) {
      this.locked.set(false);
      return true;
    }
    try {
      const assertion = await navigator.credentials.get({
        publicKey: {
          challenge: random(32),
          allowCredentials: [{ type: 'public-key', id: fromBase64(saved.credentialId), transports: ['internal'] }],
          userVerification: 'required',
          timeout: 60000,
        },
      });
      if (!assertion) return false;
      this.locked.set(false);
      this.hiddenAt = null;
      return true;
    } catch {
      return false;
    }
  }
}

function read(): Saved | null {
  try {
    const saved = JSON.parse(localStorage.getItem(KEY) ?? 'null') as Saved | null;
    return saved?.credentialId ? saved : null;
  } catch {
    return null;
  }
}

function random(n: number): Uint8Array<ArrayBuffer> {
  return crypto.getRandomValues(new Uint8Array(n));
}

function toBase64(buffer: ArrayBuffer): string {
  return btoa(String.fromCharCode(...new Uint8Array(buffer)));
}

function fromBase64(text: string): Uint8Array<ArrayBuffer> {
  return Uint8Array.from(atob(text), (c) => c.charCodeAt(0));
}
