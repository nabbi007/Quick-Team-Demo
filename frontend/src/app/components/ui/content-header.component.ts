import { Component, input } from '@angular/core';

@Component({
  selector: 'app-content-header',
  standalone: true,
  imports: [],
  template: `
    <div class="border-b">
      <div class="p-5 py-7.5 maxview-container">
        <h1 class="text-2xl font-medium">{{ title() }}</h1>
        <ng-content />
      </div>
    </div>
  `,
})
export class ContentHeaderComponent {
  title = input.required<string>();
}
