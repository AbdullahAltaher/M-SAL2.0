import { DOCUMENT } from '@angular/common';
import { Component, DestroyRef, inject } from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';

import { SupabaseAuth } from '../../core/auth/supabase-auth';
import { IfService } from '../../core/permissions/if-service';
import { SessionStore } from '../../core/session/session-store';
import { LastTab } from './last-tab';

/**
 * The signed-in frame: the shared header with the two tabs (SCREENS.md "الرأس المشترك").
 * The Expenses section switches the page to the glass colors (body.x-on), as in M-SAL.
 */
@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, IfService],
  templateUrl: './shell.html',
  styleUrl: './shell.scss',
})
export class Shell {
  private readonly auth = inject(SupabaseAuth);
  private readonly session = inject(SessionStore);
  private readonly router = inject(Router);
  private readonly document = inject(DOCUMENT);

  protected readonly user = this.session.user;

  constructor() {
    this.applySection(this.router.url);
    const sub = this.router.events
      .pipe(filter((e): e is NavigationEnd => e instanceof NavigationEnd))
      .subscribe((e) => this.applySection(e.urlAfterRedirects));
    inject(DestroyRef).onDestroy(() => {
      sub.unsubscribe();
      this.document.body.classList.remove('x-on');
    });
  }

  protected async signOut(): Promise<void> {
    this.session.clear();
    await this.auth.signOut();
    await this.router.navigateByUrl('/login');
  }

  private applySection(url: string): void {
    const expenses = url.startsWith('/expenses');
    this.document.body.classList.toggle('x-on', expenses);
    if (url.startsWith('/budget') || expenses) {
      LastTab.remember(expenses ? 'expenses' : 'budget');
    }
  }
}
