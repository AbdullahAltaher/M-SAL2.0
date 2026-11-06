import { ChangeDetectionStrategy, Component } from '@angular/core';

/**
 * The floating wallet behind the home screen (DESIGN.md D-09): the app icon's glass wallet,
 * drifting slowly, with a coin dropping into it now and then. Drawn in SVG with the theme's
 * colours (tokens.scss --w-*), so it follows light and dark. It never takes a tap, and with
 * "reduce motion" it stands still and the coin stays hidden (D-27, base.css).
 */
@Component({
  selector: 'app-floating-wallet',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <svg class="fw" viewBox="0 0 220 200" aria-hidden="true" focusable="false">
      <defs>
        <linearGradient id="fwBody" x1="0" y1="0" x2=".35" y2="1">
          <stop offset="0" style="stop-color: var(--w-body-a)" />
          <stop offset="1" style="stop-color: var(--w-body-b)" />
        </linearGradient>
        <linearGradient id="fwCard" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0" style="stop-color: var(--w-card)" />
          <stop offset="1" style="stop-color: var(--w-card)" stop-opacity=".75" />
        </linearGradient>
        <linearGradient id="fwCoin" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0" stop-color="#FFE7A3" />
          <stop offset=".55" stop-color="#F4BF45" />
          <stop offset="1" stop-color="#D4931F" />
        </linearGradient>
        <radialGradient id="fwShadow">
          <stop offset="0" style="stop-color: var(--w-card)" stop-opacity=".35" />
          <stop offset="1" style="stop-color: var(--w-card)" stop-opacity="0" />
        </radialGradient>
      </defs>

      <ellipse class="fw-shadow" cx="108" cy="188" rx="80" ry="9" fill="url(#fwShadow)" />

      <g class="fw-float">
        <!-- the cards inside: the blue one with the chart bars, the green one behind it -->
        <g transform="rotate(9 150 70)">
          <rect x="112" y="42" width="72" height="54" rx="10" style="fill: var(--w-card2)" opacity=".9" />
          <rect x="124" y="56" width="34" height="5" rx="2.5" fill="#fff" opacity=".55" />
          <rect x="124" y="66" width="22" height="5" rx="2.5" fill="#fff" opacity=".4" />
        </g>
        <g transform="rotate(-11 86 64)">
          <rect x="38" y="26" width="96" height="70" rx="12" fill="url(#fwCard)" />
          <rect x="56" y="58" width="9" height="20" rx="3" fill="#fff" opacity=".75" />
          <rect x="71" y="48" width="9" height="30" rx="3" fill="#fff" opacity=".75" />
          <rect x="86" y="38" width="9" height="40" rx="3" fill="#fff" opacity=".75" />
        </g>

        <!-- the coin falls between the cards and the wallet's front, so it drops inside -->
        <g class="fw-coin">
          <circle cx="104" cy="70" r="15" fill="url(#fwCoin)" />
          <circle cx="104" cy="70" r="10.5" fill="none" stroke="#B87A12" stroke-width="1.6" opacity=".7" />
          <path d="M97 64a10 10 0 0 1 8-4" fill="none" stroke="#fff" stroke-width="2" stroke-linecap="round" opacity=".7" />
        </g>

        <!-- the wallet's glass front, its strap and its button -->
        <g class="fw-body">
          <rect x="14" y="80" width="182" height="102" rx="30" fill="url(#fwBody)" style="stroke: var(--w-edge)" stroke-width="1.6" />
          <path d="M42 84h126" style="stroke: var(--w-shine)" stroke-width="2" stroke-linecap="round" opacity=".7" />
          <rect x="132" y="112" width="80" height="42" rx="21" fill="url(#fwBody)" style="stroke: var(--w-edge)" stroke-width="1.6" />
          <circle cx="154" cy="133" r="7.5" style="fill: var(--w-shine); stroke: var(--w-edge)" stroke-width="1.2" />
        </g>
      </g>
    </svg>
  `,
  styles: `
    :host { display: block; pointer-events: none; opacity: var(--w-opacity); }
    .fw { display: block; width: 100%; height: auto; overflow: visible; }
    .fw-float { animation: fwBob 8s ease-in-out infinite; transform-box: view-box; transform-origin: 50% 60%; }
    .fw-body { animation: fwCatch 9s ease-in-out infinite; transform-box: fill-box; transform-origin: 50% 100%; }
    .fw-coin { opacity: 0; animation: fwDrop 9s cubic-bezier(.45,0,.75,.4) infinite; transform-box: fill-box; transform-origin: center; }
    .fw-shadow { animation: fwShadow 8s ease-in-out infinite; transform-box: fill-box; transform-origin: center; }
    @keyframes fwBob {
      0%, 100% { transform: translateY(0) rotate(-3deg); }
      50% { transform: translateY(-10px) rotate(2deg); }
    }
    @keyframes fwShadow {
      0%, 100% { transform: scaleX(1); opacity: 1; }
      50% { transform: scaleX(.88); opacity: .7; }
    }
    /* once every 9 seconds: the coin spins down from above, and the wallet gives a small nod */
    @keyframes fwDrop {
      0% { opacity: 0; transform: translateY(-120px) scaleX(1); }
      4% { opacity: 1; }
      10% { transform: translateY(-80px) scaleX(-1); }
      16% { transform: translateY(-36px) scaleX(1); }
      22% { opacity: 1; transform: translateY(14px) scaleX(-1); }
      25%, 100% { opacity: 0; transform: translateY(24px) scaleX(1); }
    }
    @keyframes fwCatch {
      0%, 21%, 30%, 100% { transform: scale(1); }
      24% { transform: scale(1.03, .97); }
    }
  `,
})
export class FloatingWallet {}
