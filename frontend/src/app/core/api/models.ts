/**
 * The API's JSON shapes (API.md section 4 and 9). Amounts are strings ("46.25"), dates are
 * "YYYY-MM-DD", ids are UUID strings.
 */

export type Money = string;

// ── SRV-002 ───────────────────────────────────────────────────

export interface BudgetListItem {
  id: string;
  name: string;
  monthLabel: string;
  sortOrder: number;
  version: number;
}

export interface BudgetItem {
  id: string;
  name: string;
  amount: Money | null;
  isApprox: boolean;
  isDone: boolean;
  sortOrder: number;
  version: number;
}

export interface GroupTotals {
  subtotal: Money;
  subtotalApprox: boolean;
  balanceAfter: Money;
  balanceApprox: boolean;
}

export interface BudgetGroup {
  id: string;
  title: string;
  sortOrder: number;
  items: BudgetItem[];
  totals: GroupTotals;
  version: number;
}

export interface Budget {
  id: string;
  name: string;
  monthLabel: string;
  salaryLabel: string;
  salary: { amount: Money | null; isApprox: boolean };
  groups: BudgetGroup[];
  totals: { final: Money; finalApprox: boolean };
  lastAccountId: string | null;
  version: number;
}

// ── SRV-004, SRV-005 ──────────────────────────────────────────

export interface Account {
  id: string;
  name: string;
  isArchived: boolean;
  sortOrder: number;
  version: number;
}

export interface Category {
  id: string;
  code: string | null;
  name: string;
  emoji: string;
  isHidden: boolean;
  sortOrder: number;
  version: number;
}

// ── SRV-003 ───────────────────────────────────────────────────

export interface Cycle {
  id: string;
  startDate: string;
  allowance: Money;
  expectedNextSalaryDate: string | null;
  lastDay: string | null;
  isOpen: boolean;
  version: number;
}

export interface CycleInput {
  startDate: string;
  allowance: string;
  expectedNextSalaryDate: string | null;
}

// ── SRV-001 ───────────────────────────────────────────────────

export type EntryType = 'EXPENSE' | 'INCOME';

export interface Entry {
  id: string;
  type: EntryType;
  amount: Money;
  name: string;
  categoryId: string;
  entryDate: string;
  accountId: string;
  createdAt: string;
  updatedAt: string;
  deletedAt?: string | null;
  version: number;
  /** After create, update, restore and duplicate: the cycle the entry is in, or "none" */
  cycleKey?: string;
}

export interface EntryInput {
  type: EntryType;
  amount: string;
  name: string;
  categoryId: string;
  entryDate: string;
  accountId: string;
}

export interface CycleSummary {
  start: string;
  lastDay: string | null;
  open: boolean;
  allowance: Money;
  extra: Money;
  budget: Money;
  spent: Money;
  remaining: Money;
  spentToday: Money;
  progress: number;
  daysElapsed: number;
  totalDays: number | null;
  daysLeft: number | null;
  startOfToday: Money | null;
  todayBudget: Money | null;
  canSpendToday: Money | null;
  overToday: Money;
  tomorrow: Money | null;
  dailyAverage: Money;
  over: boolean;
  entryCount: number;
}

export interface Home {
  today: string;
  account: { id: string; name: string };
  activeAccounts: { id: string; name: string }[];
  cycleKeys: string[];
  selected: {
    key: string;
    cycle: { id: string; startDate: string; expectedNextSalaryDate: string | null; lastDay: string | null; isOpen: boolean } | null;
    summary: CycleSummary | null;
    noCycleTotal: Money | null;
  };
  recent: Entry[];
}

/** R-ENT-14, R-ENT-15 */
export interface QuickPicks {
  shortcuts: { name: string; categoryId: string; amount: Money }[];
  categoryOrder: string[];
}

export interface Day {
  date: string;
  spent: Money;
  extra: Money;
  entries: Entry[];
}

// ── SRV-006, SRV-008 ──────────────────────────────────────────

export interface Stats {
  total: Money;
  dailyAverage: Money;
  count: number;
  extra: Money;
  categories: { categoryId: string; total: Money; pct: number }[];
  lastDays: { date: string; total: Money }[];
  trend: { cycleId: string; start: string; total: Money }[];
  changes: { categoryId: string; current: Money; previous: Money; delta: Money; kind: 'NEW' | 'UP' | 'DOWN' }[];
  hasPreviousCycle: boolean;
}

export interface Receipt {
  accountName: string;
  start: string | null;
  end: string | null;
  allowance: Money;
  extra: Money;
  spent: Money;
  remaining: Money;
  categories: { name: string; total: Money; pct: number }[];
  days: { date: string; spent: Money; extra: Money; entries: { name: string; type: EntryType; amount: Money }[] }[];
}

// ── SRV-009 ───────────────────────────────────────────────────

export interface AdminUser {
  id: string;
  email: string;
  displayName: string | null;
  status: 'ACTIVE' | 'DISABLED';
  roles: string[];
  createdAt: string;
  version: number;
}

export interface Invitation {
  id: string;
  email: string;
  roleCode: string;
  status: 'PENDING' | 'ACCEPTED' | 'REVOKED' | 'EXPIRED';
  expiresAt: string;
  acceptedAt: string | null;
  createdAt: string;
}

export interface ServiceInfo {
  code: string;
  nameAr: string;
  nameEn: string;
  enabled: boolean;
}

export interface AuditPage {
  items: {
    id: number;
    occurredAt: string;
    userId: string | null;
    userEmail: string | null;
    serviceCode: string;
    action: string;
    entityType: string | null;
    entityId: string | null;
    requestId: string | null;
  }[];
  page: number;
  size: number;
  total: number;
}

/** M-SAL's countState, for a file and for what the user has now (API.md SRV-007). */
export interface ImportCounts {
  profiles: number;
  blocks: number;
  rows: number;
  accounts: number;
  cycles: number;
  entries: number;
  deleted: number;
}

export interface ImportWarning {
  code: string;
  count: number;
  message: string;
}

export type ImportMode = 'MERGE' | 'REPLACE';

export interface ImportPreview {
  /** STATE: a full export. SINGLE: an old one-person file (budget only) */
  kind: 'STATE' | 'SINGLE';
  newer: boolean;
  counts: ImportCounts;
  current: ImportCounts;
  warnings: ImportWarning[];
  /** The budget a SINGLE file goes into */
  target: { id: string; name: string } | null;
}

export interface ImportRecord {
  id: string;
  fileName: string | null;
  mode: ImportMode;
  counts: ImportCounts;
  warnings: ImportWarning[];
  createdAt: string;
}
