import { Component } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { ButtonComponent } from '../ui/button.component';
import { UserMenuComponent } from '../ui/user-menu.component';

@Component({
  selector: 'app-layout',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, ButtonComponent, UserMenuComponent],
  template: `
    <div class="flex flex-col min-h-screen">
      <div class="border-b">
        <header class="p-3 pb-2 w-full flex flex-col maxview-container">
          <div class="flex items-center justify-between p-1">
            Quickpoll
            <app-user-menu />
          </div>
          <nav class="flex gap-1 mt-2 items-center">
            @for (link of navLinks; track link.path) {
              <button
                [routerLink]="link.path"
                routerLinkActive
                #rla="routerLinkActive"
                app-button
                [variant]="rla.isActive ? 'secondary' : 'ghost'"
                size="sm"
                class="rounded-md!"
              >
                {{ link.label }}
              </button>
            }
          </nav>
        </header>
      </div>
      <main class="flex-1 view-container">
        <router-outlet />
      </main>
    </div>
  `,
  styles: `
    :host {
      display: block;
      height: 100%;
    }
  `,
})
export class DashboardLayoutComponent {
  protected navLinks = [
    {
      label: 'Polls',
      path: '/~/polls',
    },
    {
      label: 'Account',
      path: '/~/account',
    },
  ];
}
