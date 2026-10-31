// Makes the VAPID keys for notifications (Web Push, phase 7) and writes them into .env.
// Run it yourself, once:  node scripts/generate-vapid-keys.mjs
// The private key is secret (CLAUDE.md rule 16): it goes only into .env and is never printed.
// It refuses to replace keys already in .env, because new keys unsubscribe every device.
import { generateKeyPairSync } from 'node:crypto';
import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const path = resolve(dirname(fileURLToPath(import.meta.url)), '../.env');
let text = readFileSync(path, 'utf8');
const value = (name) => (text.match(new RegExp(`^${name}=(.*)$`, 'm'))?.[1] ?? '').trim();

if (value('VAPID_PUBLIC_KEY') || value('VAPID_PRIVATE_KEY')) {
  console.log('VAPID keys are already in .env; nothing changed.');
  process.exit(0);
}

const { publicKey, privateKey } = generateKeyPairSync('ec', { namedCurve: 'prime256v1' });
const jwk = privateKey.export({ format: 'jwk' });
const pub = Buffer.from(publicKey.export({ format: 'der', type: 'spki' })).subarray(-65).toString('base64url');

const set = (name, v) => {
  text = new RegExp(`^${name}=.*$`, 'm').test(text)
    ? text.replace(new RegExp(`^${name}=.*$`, 'm'), `${name}=${v}`)
    : `${text.replace(/\s*$/, '\n')}${name}=${v}\n`;
};
set('VAPID_PUBLIC_KEY', pub);
set('VAPID_PRIVATE_KEY', jwk.d);
// Apple refuses a made-up mailto (403 BadJwtToken); the app's own address works
if (!value('VAPID_SUBJECT')) set('VAPID_SUBJECT', 'https://abdullahaltaher.github.io/M-SAL2.0/');
writeFileSync(path, text);
console.log('Written to .env. Public key (not secret):', pub);
