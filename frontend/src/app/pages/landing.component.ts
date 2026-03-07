import { ButtonComponent } from '@/components/ui/button.component';
import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-landing',
  standalone: true,
  imports: [RouterLink, ButtonComponent],
  template: `
    <div class="grid min-h-screen place-items-center">
      <div class="">
        <button routerLink="~" app-button>Go to Dashboard</button>
      </div>
    </div>
  `,
})
export class LandingComponent {}
