// Opens a daily backup and loads it into a throwaway local Postgres 17, to check it or to read
// from it (docs/BACKUP.md). It never touches Supabase or the app's local database.
//   node scripts/restore-backup.mjs <msal-YYYY-MM-DD.sql.gz.age> [key file]
// The key file defaults to ~/M-SAL2.0-backup-key.txt (made by scripts/setup-backups.mjs).
// Prints the row count of each table, and nothing from the data itself.
import { execFileSync } from 'node:child_process';
import { existsSync } from 'node:fs';
import { homedir } from 'node:os';
import { basename, dirname, join, resolve } from 'node:path';

const file = process.argv[2] && resolve(process.argv[2]);
const key = resolve(process.argv[3] ?? join(homedir(), 'M-SAL2.0-backup-key.txt'));
if (!file || !existsSync(file)) throw new Error('Usage: node scripts/restore-backup.mjs <backup.sql.gz.age> [key file]');
if (!existsSync(key)) throw new Error(`No key file at ${key}`);

const name = 'msal2-restore-check';
const docker = (args, opts = {}) => execFileSync('docker', args, { stdio: ['ignore', 'pipe', 'inherit'], ...opts })?.toString() ?? '';
try {
  docker(['rm', '-f', name], { stdio: 'ignore' });
} catch {
  // not running
}
docker(['run', '-d', '--name', name, '-e', 'POSTGRES_PASSWORD=restore', '-v', `${dirname(file)}:/in:ro`,
  '-v', `${key}:/key.txt:ro`, 'postgres:17-alpine']);
try {
  docker(['exec', name, 'sh', '-c', 'until pg_isready -q -U postgres; do sleep 1; done; apk add -q --no-cache age >/dev/null']);
  docker(['exec', name, 'sh', '-c',
    `age -d -i /key.txt "/in/${basename(file)}" | gunzip | psql -q -v ON_ERROR_STOP=1 -U postgres -d postgres >/dev/null`]);
  const rows = docker(['exec', name, 'psql', '-U', 'postgres', '-d', 'postgres', '-tA', '-F', ' ', '-c',
    `select 'app_users', count(*) from msal.app_users union all select 'budgets', count(*) from msal.budgets
     union all select 'budget_items', count(*) from msal.budget_items union all select 'entries', count(*) from msal.entries`]);
  console.log(`Restored ${basename(file)}. Rows:\n${rows.trim()}`);
} finally {
  docker(['rm', '-f', name], { stdio: 'ignore' });
}
