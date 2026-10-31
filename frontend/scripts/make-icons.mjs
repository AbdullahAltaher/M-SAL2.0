// Makes the app icons and the iPhone splash screens (SCREENS.md section 7) from M-SAL's own
// icon (the apple-touch-icon SVG in M-SAL's index.html): paper, a ledger page, "د.إ" in brass.
// Run when the design changes:  node scripts/make-icons.mjs   (needs Docker)
// SVG to PNG runs in a throwaway Alpine container (rsvg-convert, Noto Sans Arabic), so nothing
// is installed on this machine. Output: public/icons/, and the <link> tags in src/index.html.
import { execFileSync } from 'node:child_process';
import { mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const out = join(root, 'public', 'icons');

const THEMES = {
  light: { bg: '#F5F2EA', surface: '#FFFCF5', border: '#DED9CD', green: '#38594A', brass: '#B99A5B' },
  dark: { bg: '#1B1A16', surface: '#242320', border: '#38362F', green: '#8FB4A0', brass: '#C2A66B' },
};

/** M-SAL's icon on a 180 grid, without its background. */
const page = (t) => `
  <rect x="24" y="24" width="132" height="132" rx="14" fill="${t.surface}" stroke="${t.border}" stroke-width="2"/>
  <line x1="44" y1="64" x2="136" y2="64" stroke="${t.green}" stroke-width="3"/>
  <line x1="44" y1="88" x2="136" y2="88" stroke="${t.border}" stroke-width="2" stroke-dasharray="2 5"/>
  <line x1="44" y1="108" x2="136" y2="108" stroke="${t.border}" stroke-width="2" stroke-dasharray="2 5"/>
  <text x="90" y="138" font-family="Noto Sans Arabic" font-size="28" font-weight="700" fill="${t.brass}" text-anchor="middle">د.إ</text>`;

const icon = (t, scale = 1) => `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 180 180">
  <rect width="180" height="180" fill="${t.bg}"/>
  <g transform="translate(90 90) scale(${scale}) translate(-90 -90)">${page(t)}</g>
</svg>`;

/** The page in the middle, and the app's name under it, as the sign-in screen. */
const splash = (t, w, h) => {
  const size = w * 0.34;
  const top = h * 0.42 - size / 2;
  return `<svg xmlns="http://www.w3.org/2000/svg" width="${w}" height="${h}" viewBox="0 0 ${w} ${h}">
  <rect width="${w}" height="${h}" fill="${t.bg}"/>
  <g transform="translate(${(w - size) / 2} ${top}) scale(${size / 180})">${page(t)}</g>
  <text x="${w / 2}" y="${top + size + w * 0.11}" font-family="Noto Sans Arabic" font-size="${w * 0.06}"
        font-weight="600" fill="${t.green}" text-anchor="middle">ميزانية الشهر</text>
</svg>`;
};

/** iPhones in portrait: CSS width and height, and pixel ratio. */
const PHONES = [
  [440, 956, 3], [402, 874, 3], [430, 932, 3], [393, 852, 3], [428, 926, 3], [390, 844, 3],
  [375, 812, 3], [414, 896, 3], [414, 896, 2], [414, 736, 3], [375, 667, 2],
];

const files = {
  'icon-180.png': [icon(THEMES.light), 180],
  'icon-192.png': [icon(THEMES.light), 192],
  'icon-512.png': [icon(THEMES.light), 512],
  'maskable-512.png': [icon(THEMES.light, 0.72), 512], // inside the 80% circle Android cuts
  'favicon-32.png': [icon(THEMES.light), 32],
};
const links = [];
for (const [cw, ch, dpr] of PHONES) {
  for (const [name, t] of Object.entries(THEMES)) {
    const file = `splash-${cw * dpr}x${ch * dpr}-${name}.png`;
    files[file] = [splash(t, cw * dpr, ch * dpr), cw * dpr];
    links.push(`  <link rel="apple-touch-startup-image" href="icons/${file}" media="(device-width: ${cw}px) and `
      + `(device-height: ${ch}px) and (-webkit-device-pixel-ratio: ${dpr}) and (orientation: portrait) and `
      + `(prefers-color-scheme: ${name})">`);
  }
}

const work = mkdtempSync(join(tmpdir(), 'msal2-icons-'));
try {
  mkdirSync(out, { recursive: true });
  const commands = [];
  for (const [file, [svg, width]] of Object.entries(files)) {
    const src = file.replace(/\.png$/, '.svg');
    writeFileSync(join(work, src), svg);
    commands.push(`rsvg-convert -w ${width} -o /out/${file} /work/${src}`);
  }
  execFileSync('docker', ['run', '--rm', '-v', `${work}:/work:ro`, '-v', `${out}:/out`, 'alpine:3.22', 'sh', '-c',
    `apk add --no-cache -q rsvg-convert font-noto-arabic >/dev/null && ${commands.join(' && ')}`],
    { stdio: 'inherit' });
} finally {
  rmSync(work, { recursive: true, force: true });
}

const index = join(root, 'src', 'index.html');
const html = readFileSync(index, 'utf8').replace(
  /  <!-- splash:start -->[\s\S]*?<!-- splash:end -->\n/,
  `  <!-- splash:start -->\n${links.join('\n')}\n  <!-- splash:end -->\n`);
writeFileSync(index, html);
console.log(`${Object.keys(files).length} images in public/icons`);
