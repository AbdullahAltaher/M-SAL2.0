import { Injectable, Type, signal } from '@angular/core';

export interface OpenSheet {
  component: Type<unknown>;
  inputs: Record<string, unknown>;
  title: ReturnType<typeof signal<string>>;
  resolve: (result: unknown) => void;
}

/**
 * M-SAL's openSheet: one sliding sheet at a time, from the bottom (SCREENS.md section 4).
 * Sheets are not routes, because the installed iPhone app has no back button.
 * <p>
 * {@code open()} resolves with what the sheet passes to {@code close()}, or undefined when it is
 * dismissed (grabber, backdrop, Escape or the close button).
 */
@Injectable({ providedIn: 'root' })
export class Sheets {
  readonly current = signal<OpenSheet | null>(null);
  /** Set by the host while the sheet slides away */
  readonly closing = signal(false);

  open<R = unknown>(component: Type<unknown>, title: string, inputs: Record<string, unknown> = {}): Promise<R | undefined> {
    this.current()?.resolve(undefined);
    return new Promise<R | undefined>((resolve) => {
      this.closing.set(false);
      this.current.set({ component, inputs, title: signal(title), resolve: resolve as (r: unknown) => void });
    });
  }

  /** Closes the open sheet with an optional result. The host plays the slide-down. */
  close(result?: unknown): void {
    const sheet = this.current();
    if (!sheet || this.closing()) return;
    sheet.resolve(result);
    this.closing.set(true);
  }

  setTitle(title: string): void {
    this.current()?.title.set(title);
  }

  /** Called by the host when the slide-down has finished. */
  finishClose(): void {
    this.current.set(null);
    this.closing.set(false);
  }
}
