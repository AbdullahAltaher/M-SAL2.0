// Makes the app icons and the iPhone splash screens (SCREENS.md section 7).
// The icon is the user's own artwork, icons-src/app-icon.webp (a glass wallet in the Budget's green
// and the Expenses' blue): the glass square is cropped out of its dark background, because iOS
// rounds the corners itself. The splash screens are plain colour (see `splash` below).
// Run when the design changes:  node scripts/make-icons.mjs   (needs Docker)
// The work runs in a throwaway Alpine container (ImageMagick, rsvg-convert), so nothing is
// installed on this machine. Output: public/icons/, and the <link> tags in src/index.html.
import { execFileSync } from 'node:child_process';
import { mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const out = join(root, 'public', 'icons');
const art = join(root, 'icons-src');

/** The glass square inside icons-src/app-icon.webp (1254x1254), found by trying crops under iOS's mask */
const SQUARE = '940x940+162+148';

/** Each icon: its size, and whether it keeps the dark background (Android's maskable circle cuts the edge) */
const ICONS = {
  'icon-180.png': [180, false],
  'icon-192.png': [192, false],
  'icon-512.png': [512, false],
  'maskable-512.png': [512, true],
  'favicon-32.png': [32, false],
};

// The start screen's navy, in both themes like the icon, so the animation follows without a jump
const THEMES = { light: { bg: '#040A1C' }, dark: { bg: '#040A1C' } };

/**
 * The navy only: iOS shows a still image at launch, so the logo is left to the animated
 * start screen in index.html, which follows it without a jump.
 */
const splash = (t, w, h) => `<svg xmlns="http://www.w3.org/2000/svg" width="${w}" height="${h}" viewBox="0 0 ${w} ${h}">
  <rect width="${w}" height="${h}" fill="${t.bg}"/>
</svg>`;

/** iPhones in portrait: CSS width and height, and pixel ratio. */
const PHONES = [
  [440, 956, 3], [402, 874, 3], [430, 932, 3], [393, 852, 3], [428, 926, 3], [390, 844, 3],
  [375, 812, 3], [414, 896, 3], [414, 896, 2], [414, 736, 3], [375, 667, 2],
];

const commands = Object.entries(ICONS).map(([file, [size, whole]]) =>
  `magick /art/app-icon.webp ${whole ? '' : `-crop ${SQUARE} +repage `}-resize ${size}x${size} -strip /out/${file}`);
const links = [];
const work = mkdtempSync(join(tmpdir(), 'msal2-icons-'));
try {
  for (const [cw, ch, dpr] of PHONES) {
    for (const [name, t] of Object.entries(THEMES)) {
      const file = `splash-${cw * dpr}x${ch * dpr}-${name}.png`;
      writeFileSync(join(work, file.replace(/\.png$/, '.svg')), splash(t, cw * dpr, ch * dpr));
      commands.push(`rsvg-convert -o /out/${file} /work/${file.replace(/\.png$/, '.svg')}`);
      links.push(`  <link rel="apple-touch-startup-image" href="icons/${file}" media="(device-width: ${cw}px) and `
        + `(device-height: ${ch}px) and (-webkit-device-pixel-ratio: ${dpr}) and (orientation: portrait) and `
        + `(prefers-color-scheme: ${name})">`);
    }
  }
  mkdirSync(out, { recursive: true });
  execFileSync('docker', ['run', '--rm', '-v', `${work}:/work:ro`, '-v', `${art}:/art:ro`, '-v', `${out}:/out`,
    'alpine:3.22', 'sh', '-c',
    `apk add --no-cache -q imagemagick imagemagick-webp rsvg-convert >/dev/null && ${commands.join(' && ')}`],
    { stdio: 'inherit' });
} finally {
  rmSync(work, { recursive: true, force: true });
}

const index = join(root, 'src', 'index.html');
const html = readFileSync(index, 'utf8').replace(
  /  <!-- splash:start -->[\s\S]*?<!-- splash:end -->\n/,
  `  <!-- splash:start -->\n${links.join('\n')}\n  <!-- splash:end -->\n`);
writeFileSync(index, html);
console.log(`${commands.length} images in public/icons`);
