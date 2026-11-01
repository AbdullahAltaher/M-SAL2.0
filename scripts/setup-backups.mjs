// Sets up the daily backup (PLAN.md section 12, docs/BACKUP.md). Run it yourself, once:
//   node scripts/setup-backups.mjs
// 1. Makes the age key pair in a throwaway container. The PRIVATE key is written to a file in
//    your home folder (never printed, never in a repository): keep a copy somewhere safe, as it
//    is the only way to open the backups.
// 2. In the private repository M-SAL2.0-backups: the secret DB_PASSWORD (from .env), and the
//    variables DB_HOST, DB_USER and AGE_RECIPIENT (the public key).
// It refuses to make a new key if the key file already exists: old backups need the old key.
import { execFileSync } from 'node:child_process';
import { existsSync, readFileSync, writeFileSync } from 'node:fs';
import { homedir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const REPO = 'AbdullahAltaher/M-SAL2.0-backups';
const KEY_FILE = join(homedir(), 'M-SAL2.0-backup-key.txt');

const env = Object.fromEntries(
  readFileSync(resolve(dirname(fileURLToPath(import.meta.url)), '../.env'), 'utf8')
    .split(/\r?\n/)
    .filter((l) => /^[A-Z_]+=/.test(l))
    .map((l) => [l.slice(0, l.indexOf('=')), l.slice(l.indexOf('=') + 1).trim()]),
);
for (const name of ['SUPABASE_DB_HOST', 'SUPABASE_DB_USER', 'SUPABASE_DB_PASSWORD']) {
  if (!env[name]) throw new Error(`${name} is empty in .env`);
}

const gh = (args, input) => execFileSync('gh', args, { input, stdio: [input ? 'pipe' : 'ignore', 'pipe', 'inherit'] }).toString();

let publicKey;
if (existsSync(KEY_FILE)) {
  publicKey = readFileSync(KEY_FILE, 'utf8').match(/public key: (age1\w+)/)?.[1];
  if (!publicKey) throw new Error(`${KEY_FILE} has no public key line`);
  console.log(`Using the existing key in ${KEY_FILE}`);
} else {
  const pair = execFileSync('docker', ['run', '--rm', 'alpine:3.22', 'sh', '-c', 'apk add -q --no-cache age >/dev/null && age-keygen 2>/dev/null'])
    .toString();
  publicKey = pair.match(/public key: (age1\w+)/)?.[1];
  if (!publicKey || !/AGE-SECRET-KEY-/.test(pair)) throw new Error('age-keygen gave no key');
  writeFileSync(KEY_FILE, pair, { mode: 0o600 });
  console.log(`New key pair. The private key is in ${KEY_FILE} (not printed).`);
}

gh(['variable', 'set', 'DB_HOST', '--repo', REPO, '--body', env.SUPABASE_DB_HOST]);
gh(['variable', 'set', 'DB_USER', '--repo', REPO, '--body', env.SUPABASE_DB_USER]);
gh(['variable', 'set', 'AGE_RECIPIENT', '--repo', REPO, '--body', publicKey]);
gh(['secret', 'set', 'DB_PASSWORD', '--repo', REPO], env.SUPABASE_DB_PASSWORD);

console.log(`Set in ${REPO}: secret DB_PASSWORD, variables DB_HOST, DB_USER, AGE_RECIPIENT.`);
console.log(`Public key (not secret): ${publicKey}`);
console.log('\nKeep a copy of the private key file somewhere safe (a password manager or a USB stick).');
console.log('Without it the backups cannot be opened.');
