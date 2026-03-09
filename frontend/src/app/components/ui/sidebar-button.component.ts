import { Component, input } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { ButtonComponent } from './button.component';

@Component({
  selector: 'app-sidebar-button',
  standalone: true,
  imports: [ButtonComponent, RouterLink, RouterLinkActive],
  template: `
    <button
      app-button
      size="sm"
      routerLinkActive
      #rla="routerLinkActive"
      [routerLink]="path()"
      [variant]="rla.isActive ? 'secondary' : 'ghost'"
      class="w-full justify-start!"
    >
      {{ label() }}
    </button>
  `,
})
export class SidebarButtonComponent {
  label = input.required<string>();
  path = input.required<string>();
}
