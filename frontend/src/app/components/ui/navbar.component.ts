import { Component, input } from '@angular/core';
import { SidebarButtonComponent } from './sidebar-button.component';

export interface SidebarLink {
  label: string;
  path: string;
}

@Component({
  selector: 'app-secondary-navbar',
  standalone: true,
  imports: [SidebarButtonComponent],
  template: `
    <nav class="flex gap-1 pb-5">
      @for (link of navLinks(); track link.path) {
        <app-sidebar-button [label]="link.label" [path]="link.path" />
      }
    </nav>
  `,
})
export class SecondaryNavbarComponent {
  navLinks = input.required<SidebarLink[]>();
}
