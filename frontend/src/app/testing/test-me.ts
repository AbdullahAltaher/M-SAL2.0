import { Me } from '../core/session/session-store';

/** A signed-in user for tests, with the given services allowed. Their open budget is "b2". */
export function testMe(services: string[]): Me {
  return {
    id: 'u1', email: 'sara@test.local', displayName: null, timeZone: 'Asia/Dubai', today: '2026-11-11',
    roles: ['USER'], lastBudgetId: 'b2', exportReminder: { due: false, days: 0, never: true }, dailyReminderTime: null, bankSmsPush: true, pushPublicKey: null, version: 1,
    services: Object.fromEntries(services.map((s) => [s, { read: true, write: true }])),
  };
}
