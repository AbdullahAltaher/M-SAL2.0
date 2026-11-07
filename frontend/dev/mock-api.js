// Fake API and session for trying screens in the browser without signing in (development only).
// Served by `ng serve` at /dev/mock-api.js (angular.json, development config); never in the build.
// All data is made up: the repository is public.
//
// Use, on http://localhost:4200/login, with the browser tool's javascript_exec:
//   window.__mockGo = '/expenses/pending'; await import('/dev/mock-api.js?' + Date.now())
// It swaps window.fetch for http://localhost:8080/api/v1 (Angular uses fetch, not XHR), fakes the
// session and opens __mockGo (default /expenses/home). A reload loses it: run it again.
// State to read or change in tests: window.__mock (pending, settings, entries, last = last body sent).
// Unknown paths answer 404 and log "mock: unmocked" in the console: add them here when needed.

const API = 'http://localhost:8080/api/v1';
const B = 'b1', A = 'a1', TODAY = '2026-11-08';
const cat = (id, code, name, emoji, i) => ({ id, code, name, emoji, isHidden: false, sortOrder: i, version: 0 });
const categories = [
  ['c-food', 'cat_food', 'أكل ومطاعم', '🍔'], ['c-groc', 'cat_grocery', 'بقالة', '🛒'], ['c-fuel', 'cat_fuel', 'بنزين ومواصلات', '⛽'],
  ['c-coffee', 'cat_coffee', 'قهوة وكافيهات', '☕'], ['c-shop', 'cat_shopping', 'ملابس وتسوق', '🛍️'], ['c-health', 'cat_health', 'صحة', '💊'],
  ['c-charity', 'cat_charity', 'تبرعات وصدقة', '🤝'], ['c-other', 'cat_other', 'أخرى', '📦'],
].map(([id, code, name, emoji], i) => cat(id, code, name, emoji, i));
const sms = (amount, merchant, city = 'Du bai') => 'Transaction of AED ' + amount + ' debited from your a/c *987 at '
  + (merchant + ' '.repeat(22)).slice(0, 22) + ' ' + city + ' AE. Avl Bal is AED 4321.00';
const entry = (id, amount, name, categoryId, entryDate, type = 'EXPENSE') => ({
  id, type, amount, name, categoryId, entryDate, accountId: A, createdAt: entryDate + 'T08:00:00Z', updatedAt: entryDate + 'T08:00:00Z', version: 0,
});

const mock = window.__mock = {
  entries: [entry('e1', '18.00', 'قهوة', 'c-coffee', TODAY), entry('e2', '64.25', 'بقالة الأسبوع', 'c-groc', '2026-11-07'),
    entry('e3', '120.00', 'بنزين', 'c-fuel', '2026-11-05')],
  pending: [
    { id: 'p1', accountId: A, amount: '12.00', merchant: 'BLUE FALCON CAFETERIA', categoryId: 'c-food', askEachTime: false,
      receivedAt: TODAY + 'T05:42:00Z', entryDate: TODAY, smsText: sms('12.00', 'BLUE FALCON CAFETERIA') },
    { id: 'p2', accountId: A, amount: '45.50', merchant: 'FASTBITE DELIVERY', categoryId: null, askEachTime: true,
      receivedAt: TODAY + 'T03:15:00Z', entryDate: TODAY, smsText: sms('45.50', 'FASTBITE DELIVERY', '+10000000000') },
    { id: 'p3', accountId: A, amount: '1250.00', merchant: 'DESERT FURNITURE', categoryId: null, askEachTime: false,
      receivedAt: '2026-11-07T17:03:00Z', entryDate: '2026-11-07', smsText: sms('1,250.00', 'DESERT FURNITURE', 'Sh arjah') },
  ],
  settings: {
    links: [{ suffix: '987', accountId: A, accountName: 'مصاريفي', archived: false, budgetId: B, budgetName: 'سارة' }],
    token: { hint: 'x7Qa', createdAt: '2026-11-07T04:00:00Z', lastUsedAt: TODAY + 'T05:42:00Z' },
    keywords: [{ id: 'k1', budgetId: B, keyword: 'CAFETERIA', categoryId: 'c-food' }, { id: 'k2', budgetId: B, keyword: 'CAFE', categoryId: 'c-coffee' },
      { id: 'k3', budgetId: B, keyword: 'CHARITY', categoryId: 'c-charity' }, { id: 'k4', budgetId: B, keyword: 'FASTBITE', categoryId: null }],
  },
  last: null,
};

const summary = {
  start: '2026-11-01', lastDay: null, open: true, allowance: '3000.00', extra: '0.00', budget: '3000.00', spent: '1240.50',
  remaining: '1759.50', spentToday: '18.00', progress: 0.41, daysElapsed: 8, totalDays: 30, daysLeft: 23, startOfToday: '1777.50',
  todayBudget: '77.28', canSpendToday: '59.28', overToday: '0.00', tomorrow: '76.50', dailyAverage: '155.06', over: false, entryCount: 23,
};
const cycle = { id: 'cy1', startDate: '2026-11-01', expectedNextSalaryDate: '2026-12-01', lastDay: null, isOpen: true };
const me = {
  id: 'u1', email: 'sara@example.test', displayName: 'سارة', timeZone: 'Asia/Dubai', today: TODAY, roles: ['USER', 'ADMIN'],
  services: Object.fromEntries(['000', '001', '002', '003', '004', '005', '006', '007', '008', '009', '011']
    .map((n) => ['SRV-' + n, { read: true, write: true }])),
  lastBudgetId: B, exportReminder: { due: false, days: 1, never: false }, dailyReminderTime: null, pushPublicKey: null, version: 1,
};
const days = () => [...new Set(mock.entries.map((e) => e.entryDate))].sort().reverse().map((date) => {
  const list = mock.entries.filter((e) => e.entryDate === date);
  const sum = (t) => list.filter((e) => e.type === t).reduce((s, e) => s + Number(e.amount), 0).toFixed(2);
  return { date, spent: sum('EXPENSE'), extra: sum('INCOME'), entries: list };
});

const json = (body, status = 200) => new Response(body === undefined ? null : JSON.stringify(body),
  { status, headers: { 'Content-Type': 'application/json' } });
const routes = [
  ['GET', /^\/me$/, () => me],
  ['GET', /^\/budgets$/, () => [{ id: B, name: 'سارة', monthLabel: 'نوفمبر', sortOrder: 0, version: 0 }]],
  ['GET', /^\/budgets\/b1$/, () => ({ id: B, name: 'سارة', monthLabel: 'نوفمبر', salaryLabel: 'الراتب', salary: { amount: '9000.00', isApprox: false },
    groups: [], totals: { final: '9000.00', finalApprox: false }, lastAccountId: A, version: 0 })],
  ['GET', /\/accounts$/, () => [{ id: A, name: 'مصاريفي', isArchived: false, sortOrder: 0, version: 0 }]],
  ['GET', /\/categories$/, () => categories],
  ['GET', /\/home$/, () => ({ today: TODAY, account: { id: A, name: 'مصاريفي' }, activeAccounts: [{ id: A, name: 'مصاريفي' }], cycleKeys: ['cy1'],
    selected: { key: 'cy1', cycle, summary, noCycleTotal: null }, recent: mock.entries.slice(0, 5), pendingCount: mock.pending.length })],
  ['GET', /\/entries\/quick$/, () => ({ shortcuts: [], categoryOrder: ['c-food', 'c-coffee', 'c-groc'] })],
  ['GET', /\/entries\/suggestions$/, (url) => ({ suggestions: url.includes('c-food') ? ['غداء', 'عشاء', 'ريوق'] : url.includes('c-coffee') ? ['قهوة'] : [] })],
  ['GET', /\/entries\/deleted$/, () => []],
  ['GET', /\/entries\/last-expense$/, () => undefined],
  ['GET', /\/accounts\/a1\/entries$/, () => ({ days: days() })],
  ['GET', /\/cycles$/, () => ({ cycles: [{ ...cycle, allowance: '3000.00', version: 0 }] })],
  ['GET', /\/stats$/, () => ({ total: '1240.50', dailyAverage: '155.06', count: 23, extra: '0.00',
    categories: [{ categoryId: 'c-groc', total: '640.25', pct: 51.6 }, { categoryId: 'c-fuel', total: '360.00', pct: 29 }, { categoryId: 'c-coffee', total: '240.25', pct: 19.4 }],
    lastDays: [{ date: TODAY, total: '18.00' }], trend: [{ cycleId: 'cy1', start: '2026-11-01', total: '1240.50' }], changes: [], hasPreviousCycle: false })],
  ['GET', /\/pending-entries$/, () => mock.pending],
  ['POST', /^\/pending-entries\/(\w+)\/approve$/, (url, data, m) => {
    const p = mock.pending.find((x) => x.id === m[1]);
    mock.pending = mock.pending.filter((x) => x.id !== m[1]);
    const e = { ...entry('e' + Date.now(), p.amount, data.name, data.categoryId, data.entryDate ?? p.entryDate), cycleKey: 'cy1' };
    mock.entries.unshift(e);
    return [e, 201];
  }],
  ['DELETE', /^\/pending-entries\/(\w+)$/, (url, data, m) => { mock.pending = mock.pending.filter((x) => x.id !== m[1]); return [undefined, 204]; }],
  ['GET', /^\/sms\/settings$/, () => mock.settings],
  ['POST', /^\/sms\/token$/, () => {
    const token = 'msal_FAKEfakeFAKEfakeFAKEfakeFAKE1234';
    mock.settings.token = { hint: token.slice(-4), createdAt: new Date().toISOString(), lastUsedAt: null };
    return [{ token, hint: token.slice(-4), createdAt: mock.settings.token.createdAt }, 201];
  }],
  ['DELETE', /^\/sms\/token$/, () => { mock.settings.token = null; return [undefined, 204]; }],
  ['PUT', /^\/sms\/links\/(\d{3,6})$/, (url, data, m) => {
    mock.settings.links = mock.settings.links.filter((l) => l.suffix !== m[1])
      .concat([{ suffix: m[1], accountId: data.accountId, accountName: 'مصاريفي', archived: false, budgetId: B, budgetName: 'سارة' }]);
    return [undefined, 204];
  }],
  ['DELETE', /^\/sms\/links\/(\d+)$/, (url, data, m) => { mock.settings.links = mock.settings.links.filter((l) => l.suffix !== m[1]); return [undefined, 204]; }],
  ['POST', /\/merchant-keywords$/, (url, data) => {
    const keyword = String(data.keyword ?? '').toUpperCase().replace(/[^\p{L}\p{N}]+/gu, ' ').trim();
    if (!keyword) return [{ code: 'SRV-011-E002', message: 'اكتب كلمة.', details: [] }, 400];
    const row = { id: 'k' + Date.now(), budgetId: B, keyword, categoryId: data.categoryId };
    mock.settings.keywords = mock.settings.keywords.filter((k) => k.keyword !== keyword).concat([row]);
    return [row, 201];
  }],
  ['DELETE', /^\/merchant-keywords\/(\w+)$/, (url, data, m) => { mock.settings.keywords = mock.settings.keywords.filter((k) => k.id !== m[1]); return [undefined, 204]; }],
  ['GET', /^\/imports$/, () => []],
];

const realFetch = window.__realFetch || window.fetch;
window.__realFetch = realFetch;
window.fetch = async (input, init) => {
  const url = typeof input === 'string' ? input : input.url;
  if (!url.startsWith(API)) return realFetch(input, init);
  const method = (init?.method || (typeof input === 'string' ? 'GET' : input.method) || 'GET').toUpperCase();
  const path = url.slice(API.length).split('?')[0];
  let body = init?.body;
  if (!body && typeof input !== 'string') body = await input.clone().text().catch(() => '');
  const data = body ? JSON.parse(body) : null;
  if (method !== 'GET') mock.last = { method, path, data };
  await new Promise((r) => setTimeout(r, 80));
  for (const [m, pattern, handle] of routes) {
    const match = path.match(pattern);
    if (m === method && match) {
      const result = handle(url, data, match);
      return Array.isArray(result) && typeof result[1] === 'number' ? json(result[0], result[1]) : json(result);
    }
  }
  console.warn('mock: unmocked', method, path);
  return json({ code: 'SYS-E004', message: 'العنصر غير موجود.', details: [] }, 404);
};

const page = ng.getComponent(document.querySelector('app-login-page'));
page.auth.accessToken = async () => 'x';
page.auth.whenReady = async () => {};
page.auth.signOut = async () => {};
page.auth.session.set({ access_token: 'x', user: { email: me.email } });
await page.router.navigateByUrl(window.__mockGo || '/expenses/home');
