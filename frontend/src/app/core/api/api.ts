import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, firstValueFrom } from 'rxjs';

import { environment } from '../../../environments/environment';
import type { Me } from '../session/session-store';
import { toApiError } from './api-error';
import {
  Account, AdminUser, AuditPage, Budget, BudgetGroup, BudgetItem, BudgetListItem, Category, Cycle, CycleInput, Day, Entry,
  EntryInput, Home, ImportMode, ImportPreview, ImportRecord, Invitation, MerchantKeyword, PendingEntry, QuickPicks, Receipt,
  ServiceInfo, SmsSettings, Stats,
} from './models';

/**
 * Every call of API.md section 4, as promises. Failures reject with an ApiError whose Arabic
 * message can be shown as is.
 */
@Injectable({ providedIn: 'root' })
export class Api {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiUrl;

  private run<T>(call: Observable<T>): Promise<T> {
    return firstValueFrom(call).catch((error: unknown) => {
      throw toApiError(error);
    });
  }

  private get<T>(path: string, params?: Record<string, string | boolean | undefined | null>): Promise<T> {
    let p = new HttpParams();
    for (const [k, v] of Object.entries(params ?? {})) if (v !== undefined && v !== null && v !== '') p = p.set(k, String(v));
    return this.run(this.http.get<T>(this.base + path, { params: p }));
  }
  private post<T>(path: string, body: unknown = {}): Promise<T> {
    return this.run(this.http.post<T>(this.base + path, body));
  }
  private patch<T>(path: string, body: unknown): Promise<T> {
    return this.run(this.http.patch<T>(this.base + path, body));
  }
  private put<T>(path: string, body: unknown): Promise<T> {
    return this.run(this.http.put<T>(this.base + path, body));
  }
  private delete<T = void>(path: string): Promise<T> {
    return this.run(this.http.delete<T>(this.base + path));
  }

  // ── SRV-000 ─────────────────────────────────────────────────
  patchMe(body: { displayName?: string; timeZone?: string; lastBudgetId?: string; dailyReminderTime?: string | null; version: number }) {
    return this.patch<Me>('/me', body);
  }

  // ── SRV-002 ─────────────────────────────────────────────────
  budgets() { return this.get<BudgetListItem[]>('/budgets'); }
  budget(id: string) { return this.get<Budget>(`/budgets/${id}`); }
  createBudget(name: string) { return this.post<Budget>('/budgets', { name }); }
  patchBudget(id: string, body: Record<string, unknown> & { version: number }) { return this.patch<Budget>(`/budgets/${id}`, body); }
  deleteBudget(id: string) { return this.delete(`/budgets/${id}`); }
  newMonth(id: string) { return this.post<Budget>(`/budgets/${id}/new-month`); }
  addGroup(budgetId: string, title = '') { return this.post<BudgetGroup>(`/budgets/${budgetId}/groups`, { title }); }
  patchGroup(id: string, body: { title?: string; version: number }) { return this.patch<BudgetGroup>(`/budget-groups/${id}`, body); }
  deleteGroup(id: string) { return this.delete(`/budget-groups/${id}`); }
  addItem(groupId: string) { return this.post<BudgetItem>(`/budget-groups/${groupId}/items`); }
  patchItem(id: string, body: Record<string, unknown> & { version: number }) { return this.patch<BudgetItem>(`/budget-items/${id}`, body); }
  deleteItem(id: string) { return this.delete(`/budget-items/${id}`); }

  // ── SRV-004, SRV-005 ────────────────────────────────────────
  accounts(budgetId: string, includeArchived = false) {
    return this.get<Account[]>(`/budgets/${budgetId}/accounts`, { includeArchived });
  }
  createAccount(budgetId: string, name: string) { return this.post<Account>(`/budgets/${budgetId}/accounts`, { name }); }
  patchAccount(id: string, body: { name?: string; isArchived?: boolean; version: number }) {
    return this.patch<Account>(`/accounts/${id}`, body);
  }
  categories(budgetId: string) { return this.get<Category[]>(`/budgets/${budgetId}/categories`); }
  createCategory(budgetId: string, name: string, emoji: string) {
    return this.post<Category>(`/budgets/${budgetId}/categories`, { name, emoji });
  }
  patchCategory(id: string, body: { name?: string; emoji?: string; isHidden?: boolean; version: number }) {
    return this.patch<Category>(`/categories/${id}`, body);
  }
  reorderCategories(budgetId: string, categoryIds: string[]) {
    return this.put<Category[]>(`/budgets/${budgetId}/categories/order`, { categoryIds });
  }

  // ── SRV-003 ─────────────────────────────────────────────────
  cycles(accountId: string) { return this.get<{ cycles: Cycle[]; hasNoCycleEntries: boolean }>(`/accounts/${accountId}/cycles`); }
  prefill(accountId: string) { return this.get<{ startDate: string; allowance: string }>(`/accounts/${accountId}/cycles/prefill`); }
  createCycle(accountId: string, body: CycleInput) { return this.post<Cycle>(`/accounts/${accountId}/cycles`, body); }
  patchCycle(id: string, body: Partial<CycleInput> & { version: number }) { return this.patch<Cycle>(`/salary-cycles/${id}`, body); }
  moveImpact(id: string, startDate: string) {
    return this.get<{ movedEntries: number }>(`/salary-cycles/${id}/move-impact`, { startDate });
  }
  deleteCycle(id: string) { return this.delete(`/salary-cycles/${id}`); }

  // ── SRV-001 ─────────────────────────────────────────────────
  home(accountId: string, cycle: string) { return this.get<Home>(`/accounts/${accountId}/home`, { cycle }); }
  entries(accountId: string, cycle: string, q?: string, categoryId?: string) {
    return this.get<{ days: Day[] }>(`/accounts/${accountId}/entries`, { cycle, q, categoryId });
  }
  async lastExpense(accountId: string): Promise<Entry | null> {
    return (await this.get<Entry | null>(`/accounts/${accountId}/entries/last-expense`)) ?? null;
  }
  createEntry(budgetId: string, body: EntryInput) { return this.post<Entry>(`/budgets/${budgetId}/entries`, body); }
  patchEntry(id: string, body: Partial<EntryInput> & { version: number }) { return this.patch<Entry>(`/entries/${id}`, body); }
  deleteEntry(id: string) { return this.delete(`/entries/${id}`); }
  restoreEntry(id: string) { return this.post<Entry>(`/entries/${id}/restore`); }
  duplicateEntry(id: string) { return this.post<Entry>(`/entries/${id}/duplicate`); }
  purgeEntry(id: string) { return this.delete(`/entries/${id}/permanent`); }
  deletedEntries(budgetId: string) { return this.get<Entry[]>(`/budgets/${budgetId}/entries/deleted`); }
  purgeDeleted(budgetId: string) { return this.delete<{ purged: number }>(`/budgets/${budgetId}/entries/deleted`); }
  suggestions(budgetId: string, categoryId: string) {
    return this.get<{ suggestions: string[] }>(`/budgets/${budgetId}/entries/suggestions`, { categoryId });
  }
  /** The entry sheet's one-tap shortcuts and category order (R-ENT-14, R-ENT-15) */
  quick(budgetId: string) { return this.get<QuickPicks>(`/budgets/${budgetId}/entries/quick`); }

  // ── SRV-006, SRV-008 ────────────────────────────────────────
  stats(accountId: string, cycle: string) { return this.get<Stats>(`/accounts/${accountId}/stats`, { cycle }); }
  receipt(accountId: string, cycle: string) { return this.get<Receipt>(`/accounts/${accountId}/receipt`, { cycle }); }

  // ── SRV-000: this device's notifications (the daily reminder) ─
  subscribePush(subscription: PushSubscriptionJSON) { return this.post<void>('/me/push-subscriptions', subscription); }
  unsubscribePush(endpoint: string) { return this.delete(`/me/push-subscriptions?endpoint=${encodeURIComponent(endpoint)}`); }

  // ── SRV-007 ─────────────────────────────────────────────────
  /** The export file (R-IO-01); the server also notes the export for its reminder. */
  exportFile() { return this.run(this.http.get(this.base + '/export', { responseType: 'blob' })); }
  previewImport(file: File) { return this.post<ImportPreview>('/imports/preview', form(file)); }
  importFile(file: File, mode: ImportMode) { return this.post<ImportRecord>(`/imports?mode=${mode}`, form(file)); }
  imports() { return this.get<ImportRecord[]>('/imports'); }

  // ── SRV-011: bank messages (BANK-SMS.md 4.5) ───────────────
  pendingEntries(budgetId: string) { return this.get<PendingEntry[]>(`/budgets/${budgetId}/pending-entries`); }
  /** Adds the entry exactly as createEntry does, and deletes the pending one with its message (B-07) */
  approvePending(id: string, body: { categoryId: string; name: string; remember: boolean; entryDate?: string }) {
    return this.post<Entry>(`/pending-entries/${id}/approve`, body);
  }
  ignorePending(id: string) { return this.delete(`/pending-entries/${id}`); }
  smsSettings() { return this.get<SmsSettings>('/sms/settings'); }
  linkSms(suffix: string, accountId: string) { return this.put<void>(`/sms/links/${encodeURIComponent(suffix)}`, { accountId }); }
  unlinkSms(suffix: string) { return this.delete(`/sms/links/${encodeURIComponent(suffix)}`); }
  /** The token is returned this once only (B-19) */
  newSmsToken() { return this.post<{ token: string; hint: string; createdAt: string }>('/sms/token'); }
  revokeSmsToken() { return this.delete('/sms/token'); }
  addKeyword(budgetId: string, keyword: string, categoryId: string | null) {
    return this.post<MerchantKeyword>(`/budgets/${budgetId}/merchant-keywords`, { keyword, categoryId });
  }
  deleteKeyword(id: string) { return this.delete(`/merchant-keywords/${id}`); }

  // ── SRV-009 ─────────────────────────────────────────────────
  adminUsers() { return this.get<AdminUser[]>('/admin/users'); }
  patchAdminUser(id: string, body: { status?: string; roles?: string[]; version: number }) {
    return this.patch<AdminUser>(`/admin/users/${id}`, body);
  }
  invitations() { return this.get<Invitation[]>('/admin/invitations'); }
  invite(email: string, roleCode = 'USER') { return this.post<Invitation>('/admin/invitations', { email, roleCode }); }
  revokeInvitation(id: string) { return this.delete(`/admin/invitations/${id}`); }
  services() { return this.get<ServiceInfo[]>('/admin/services'); }
  patchService(code: string, enabled: boolean) { return this.patch<ServiceInfo>(`/admin/services/${code}`, { enabled }); }
  auditLog(params: { userId?: string; service?: string; page?: number; size?: number }) {
    return this.get<AuditPage>('/admin/audit-log', {
      userId: params.userId, service: params.service, page: String(params.page ?? 0), size: String(params.size ?? 50),
    });
  }
}

/** The import file as multipart {@code file} (API.md SRV-007). */
function form(file: File): FormData {
  const data = new FormData();
  data.append('file', file, file.name);
  return data;
}
