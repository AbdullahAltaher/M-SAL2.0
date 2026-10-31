/**
 * Chart helpers for the hand-written SVG charts (SCREENS.md S-22), ported from M-SAL's
 * chartNum, smoothPath and chartX. No chart library: lighter, and the same look.
 */

/** Compact Latin labels: 46.25 → "46", 1234 → "1.2K". */
export function chartNum(v: unknown): string {
  const n = Math.abs(Number(v) || 0);
  const r = Math.round(n);
  const t = r >= 1000 ? (Math.round(n / 100) / 10).toString().replace(/\.0$/, '') + 'K' : String(r);
  return (Number(v) < 0 && r !== 0 ? '-' : '') + t;
}

/**
 * A smooth curve through the points (Catmull-Rom as cubic Béziers). Control points are clamped
 * to [yMin, yMax], so the curve never dips under the baseline or above the top.
 */
export function smoothPath(pts: [number, number][], yMin: number, yMax: number): string {
  if (!pts.length) return '';
  const f = (n: number) => Math.round(n * 100) / 100;
  const cl = (y: number) => Math.min(yMax, Math.max(yMin, y));
  let d = 'M' + f(pts[0][0]) + ' ' + f(pts[0][1]);
  for (let i = 0; i < pts.length - 1; i++) {
    const p0 = pts[i - 1] || pts[i], p1 = pts[i], p2 = pts[i + 1], p3 = pts[i + 2] || p2;
    d += ' C' + f(p1[0] + (p2[0] - p0[0]) / 6) + ' ' + f(cl(p1[1] + (p2[1] - p0[1]) / 6)) + ' ' +
      f(p2[0] - (p3[0] - p1[0]) / 6) + ' ' + f(cl(p2[1] - (p3[1] - p1[1]) / 6)) + ' ' + f(p2[0]) + ' ' + f(p2[1]);
  }
  return d;
}

/** Charts run right to left like the interface: the oldest point is on the right. */
export function chartX(i: number, n: number, left: number, right: number): number {
  return n > 1 ? right - (i * (right - left)) / (n - 1) : (left + right) / 2;
}

/** The colour of the i-th category slice (M-SAL's sliceVar). */
export function sliceVar(i: number): string {
  return 'var(--x-s' + ((i % 8) + 1) + ')';
}
