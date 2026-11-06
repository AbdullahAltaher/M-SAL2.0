/** The coin's drawing: the same gold coin as the floating wallet (shared/components/floating-wallet.ts). */
const COIN = `<svg viewBox="0 0 30 30" width="100%" height="100%" aria-hidden="true">
  <defs><linearGradient id="cfGold" x1="0" y1="0" x2="1" y2="1">
    <stop offset="0" stop-color="#FFE7A3"/><stop offset=".55" stop-color="#F4BF45"/><stop offset="1" stop-color="#D4931F"/>
  </linearGradient></defs>
  <circle cx="15" cy="15" r="14" fill="url(#cfGold)"/>
  <circle cx="15" cy="15" r="9.8" fill="none" stroke="#B87A12" stroke-width="1.5" opacity=".7"/>
  <path d="M8.5 9.5a9 9 0 0 1 7-3.5" fill="none" stroke="#fff" stroke-width="2" stroke-linecap="round" opacity=".7"/>
</svg>`;

const SIZE = 30;

/**
 * After saving an expense (DESIGN.md D-10, D-24): a coin flies from the + button to the account
 * card in an arc, spinning, in 0.9s. Nothing flies with "reduce motion" (D-27), or when either
 * end is not on screen.
 */
export function flyCoin(from: Element | null, to: Element | null): void {
  if (!from || !to || typeof document === 'undefined') return;
  if (typeof matchMedia === 'function' && matchMedia('(prefers-reduced-motion: reduce)').matches) return;
  const a = from.getBoundingClientRect();
  const b = to.getBoundingClientRect();
  if (b.bottom < 0 || b.top > window.innerHeight) return;

  const coin = document.createElement('div');
  coin.className = 'coin-flight';
  coin.innerHTML = COIN;
  coin.style.cssText = `position:fixed;left:0;top:0;width:${SIZE}px;height:${SIZE}px;z-index:90;pointer-events:none`;
  document.body.appendChild(coin);

  const x0 = a.left + a.width / 2 - SIZE / 2;
  const y0 = a.top + a.height / 2 - SIZE / 2;
  // lands on the card's big number, which sits on its left in a right-to-left page
  const x1 = b.left + b.width * 0.25 - SIZE / 2;
  const y1 = b.top + b.height * 0.5 - SIZE / 2;
  // the arc's top: above both ends, leaning toward the start
  const xm = (x0 + x1) / 2 + 60;
  const ym = Math.min(y0, y1) - 70;

  const at = (x: number, y: number, s: number, r: number) => `translate(${x}px, ${y}px) scale(${s}) rotate(${r}deg)`;
  coin.animate(
    [
      { transform: at(x0, y0, 0.5, 0), opacity: 0 },
      { transform: at(x0, y0 - 24, 1, 60), opacity: 1, offset: 0.15 },
      { transform: at(xm, ym, 1.15, 220), opacity: 1, offset: 0.55 },
      { transform: at(x1, y1, 0.7, 380), opacity: 1, offset: 0.92 },
      { transform: at(x1, y1, 0.4, 400), opacity: 0 },
    ],
    { duration: 900, easing: 'cubic-bezier(.4, 0, .2, 1)' },
  ).finished.finally(() => coin.remove());
}
