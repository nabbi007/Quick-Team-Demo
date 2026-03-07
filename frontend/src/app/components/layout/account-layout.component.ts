import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { ContentHeaderComponent } from '@/components/ui/content-header.component';
import { SidebarComponent } from '@/components/ui/sidebar.component';

@Component({
  selector: 'app-account',
  standalone: true,
  imports: [ContentHeaderComponent, SidebarComponent, RouterOutlet],
  template: `
    <app-content-header title="Account" />
    <div class="p-5 flex gap-5">
      <app-sidebar [navLinks]="navLinks" />
      <router-outlet />
    </div>
  `,
})
export class AccountLayoutComponent {
  protected navLinks = [
    { label: 'Profile', path: '/~/account/profile' },
    { label: 'Teams', path: '/~/account/teams' },
    { label: 'Settings', path: '/~/account/settings' },
  ];
}
