import { Component, inject } from '@angular/core';
import { AsyncPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { AuthService } from '@/services/auth.service';
import { ButtonComponent } from './button.component';

@Component({
  selector: 'app-user-menu',
  standalone: true,
  imports: [ButtonComponent, RouterLink, AsyncPipe],
  template: `
    <button
      app-button
      variant="ghost"
      routerLink="/~/account"
      class="flex items-center gap-1 px-0.5! md:pl-6! rounded-full!"
    >
      <div class="hidden md:flex flex-col items-end">
        <p class="text-xs font-medium">{{ (user | async)?.fullName }}</p>
        <p class="text-xs text-muted-foreground">{{ (user | async)?.email }}</p>
      </div>
      <div class="inline-flex size-9 rounded-full bg-neutral-300"></div>
    </button>
  `,
})
export class UserMenuComponent {
  private auth = inject(AuthService);
  protected user = this.auth.getProfile();
}
