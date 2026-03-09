import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { ContentHeaderComponent } from '@/components/ui/content-header.component';
import { SecondaryNavbarComponent } from '../ui/navbar.component';

@Component({
  selector: 'app-account',
  standalone: true,
  imports: [ContentHeaderComponent, SecondaryNavbarComponent, RouterOutlet],
  template: `
    <app-content-header title="Account" />
    <div class="p-5 flex flex-col maxview-container gap-5">
      <app-secondary-navbar [navLinks]="navLinks" />
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
