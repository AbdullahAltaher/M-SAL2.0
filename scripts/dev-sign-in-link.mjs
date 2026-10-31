// Development only: prints a one-time sign-in link for MSAL_BOOTSTRAP_ADMIN_EMAIL, without
// sending an email (Supabase's default email allows only 2 per hour until SMTP, phase 10).
// Run it yourself:  node scripts/dev-sign-in-link.mjs
// Open the printed link in the browser where the app runs (http://localhost:4200).
// The link signs in as you: never share it. Secrets are read from .env and never printed.
import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const env = Object.fromEntries(
  readFileSync(resolve(dirname(fileURLToPath(import.meta.url)), '../.env'), 'utf8')
    .split(/\r?\n/)
    .filter((l) => /^[A-Z_]+=/.test(l))
    .map((l) => [l.slice(0, l.indexOf('=')), l.slice(l.indexOf('=') + 1)]),
);

const response = await fetch(`${env.SUPABASE_URL}/auth/v1/admin/generate_link`, {
  method: 'POST',
  headers: {
    apikey: env.SUPABASE_SECRET_KEY,
    Authorization: `Bearer ${env.SUPABASE_SECRET_KEY}`,
    'Content-Type': 'application/json',
  },
  body: JSON.stringify({
    type: 'magiclink',
    email: env.MSAL_BOOTSTRAP_ADMIN_EMAIL,
    redirect_to: 'http://localhost:4200/',
  }),
});
const body = await response.json();
if (!response.ok) {
  console.error('Failed:', response.status, body.msg ?? body.error_description ?? body.error ?? '');
  process.exit(1);
}
console.log('\nOpen this link in the app\'s browser (valid once, for a short time):\n');
console.log(body.action_link ?? body.properties?.action_link);
