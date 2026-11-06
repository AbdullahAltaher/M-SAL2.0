import { ViewTransitionInfo } from '@angular/router';

/** The movement between two addresses (DESIGN.md D-32, D-33). */
export type PageMotion = 'to-expenses' | 'to-budget' | 'fade' | null;

const section = (url: string) => (url.startsWith('/expenses') ? 'expenses' : url.startsWith('/budget') ? 'budget' : null);
const path = (url: string) => url.split(/[?#]/)[0];

/**
 * Budget ↔ Expenses slides sideways with the switch (D-32); the Expenses bottom bar fades
 * (D-33); everything else (sign-in, admin, a cycle in the address) changes at once.
 */
export function motionBetween(from: string, to: string): PageMotion {
  const a = section(from);
  const b = section(to);
  if (a && b && a !== b) return b === 'expenses' ? 'to-expenses' : 'to-budget';
  if (a === 'expenses' && b === 'expenses' && path(from) !== path(to)) return 'fade';
  return null;
}

/**
 * The router's view transition hook: picks the movement and names it on <html>, where glass.css
 * reads it. Browsers without view transitions simply change the page.
 * (DESIGN.md D-27: with "reduce motion" nothing moves; glass.css also stops the animations.)
 */
export function pageMotion({ transition, from, to }: ViewTransitionInfo): void {
  const reduce = typeof matchMedia === 'function' && matchMedia('(prefers-reduced-motion: reduce)').matches;
  const motion = reduce ? null : motionBetween(urlOf(from), urlOf(to));
  // No movement: the transition still runs, but with nothing named and the page-wide fade
  // turned off (glass.css) it changes at once. (Skipping it instead makes Angular log an error.)
  if (!motion) return;
  const root = document.documentElement;
  root.dataset['motion'] = motion;
  void transition.finished.finally(() => {
    if (root.dataset['motion'] === motion) delete root.dataset['motion'];
  });
}

type Snapshot = ViewTransitionInfo['from'];

/** The address a route snapshot stands for, from the root down. */
function urlOf(snapshot: Snapshot): string {
  let node: Snapshot | null = snapshot.root;
  const parts: string[] = [];
  while (node) {
    parts.push(...node.url.map((s) => s.path));
    node = node.firstChild;
  }
  return '/' + parts.filter(Boolean).join('/');
}
