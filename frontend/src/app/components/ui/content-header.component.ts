import { Component, input } from '@angular/core';

@Component({
  selector: 'app-content-header',
  standalone: true,
  imports: [],
  template: `
    <div class="relative p-5 py-7 mb-6 border-b">
      <h1 class="text-2xl font-medium">{{ title() }}</h1>
      <ng-content />
    </div>
  `,
})
export class ContentHeaderComponent {
  title = input.required<string>();
}
