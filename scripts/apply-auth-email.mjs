// Sets Supabase Auth to send the sign-in code through your own Gmail (SMTP), with the Arabic
// code template docs/supabase/otp-email.html (API.md 2.2, phase 10). Run it yourself:
//   node scripts/apply-auth-email.mjs
// Reads from .env: SUPABASE_PROJECT_REF, SUPABASE_ACCESS_TOKEN (Auth Config + Project Settings,
// read and write), SMTP_USER (your Gmail address) and SMTP_PASSWORD (a Google "app password",
// not your Gmail password). SMTP_HOST and SMTP_PORT default to smtp.gmail.com and 465.
// Prints what was set, never the password or the token. Delete the token afterwards.
import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const env = Object.fromEntries(
  readFileSync(resolve(root, '.env'), 'utf8')
    .split(/\r?\n/)
    .filter((l) => /^[A-Z_]+=/.test(l))
    .map((l) => [l.slice(0, l.indexOf('=')), l.slice(l.indexOf('=') + 1).trim()]),
);
for (const name of ['SUPABASE_PROJECT_REF', 'SUPABASE_ACCESS_TOKEN', 'SMTP_USER', 'SMTP_PASSWORD']) {
  if (!env[name]) {
    console.error(`${name} is empty in .env`);
    process.exit(1);
  }
}

const template = readFileSync(resolve(root, 'docs/supabase/otp-email.html'), 'utf8').replace(/<!--[\s\S]*?-->\s*/, '');
const url = `https://api.supabase.com/v1/projects/${env.SUPABASE_PROJECT_REF}/config/auth`;
const headers = { Authorization: `Bearer ${env.SUPABASE_ACCESS_TOKEN}`, 'Content-Type': 'application/json' };

const response = await fetch(url, {
  method: 'PATCH',
  headers,
  body: JSON.stringify({
    smtp_host: env.SMTP_HOST || 'smtp.gmail.com',
    smtp_port: env.SMTP_PORT || '465',
    smtp_user: env.SMTP_USER,
    smtp_pass: env.SMTP_PASSWORD.replace(/\s+/g, ''), // Google shows app passwords in groups of 4
    smtp_admin_email: env.SMTP_USER, // Gmail sends only as the signed-in address
    smtp_sender_name: 'ميزانية الشهر',
    // Supabase's own email allows 2 an hour; with our SMTP the limit is ours (Gmail: ~500 a day)
    rate_limit_email_sent: 30,
    mailer_subjects_magic_link: 'رمز الدخول إلى ميزانية الشهر',
    mailer_templates_magic_link_content: template,
  }),
});
if (!response.ok) {
  console.error('Failed:', response.status, (await response.text()).slice(0, 300));
  process.exit(1);
}

const now = await (await fetch(url, { headers })).json();
console.log('Set:', {
  smtp_host: now.smtp_host,
  smtp_port: now.smtp_port,
  smtp_user: now.smtp_user,
  smtp_sender_name: now.smtp_sender_name,
  subject: now.mailer_subjects_magic_link,
  template_has_code: (now.mailer_templates_magic_link_content ?? '').includes('{{ .Token }}'),
  otp_length: now.mailer_otp_length,
  otp_seconds: now.mailer_otp_exp,
  emails_per_hour: now.rate_limit_email_sent,
});
console.log('\nNow try "أرسل الرمز" in the app. Then delete the Supabase token (and empty SUPABASE_ACCESS_TOKEN).');
