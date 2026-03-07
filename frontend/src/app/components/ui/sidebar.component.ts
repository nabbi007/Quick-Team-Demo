import { Component, input } from '@angular/core';
import { SidebarButtonComponent } from './sidebar-button.component';

export interface SidebarLink {
  label: string;
  path: string;
}

@Component({
  selector: 'app-sidebar',
  standalone: true,
  imports: [SidebarButtonComponent],
  template: `
    <nav class="min-w-56 flex flex-col gap-px">
      @for (link of navLinks(); track link.path) {
        <app-sidebar-button [label]="link.label" [path]="link.path" />
      }
    </nav>
  `,
})
export class SidebarComponent {
  navLinks = input.required<SidebarLink[]>();
}
