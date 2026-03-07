import { Component } from '@angular/core';
import { RouterModule } from '@angular/router';

@Component({
  selector: 'app-not-found',
  standalone: true,
  imports: [RouterModule],
  template: `
    <div class="h-screen grid place-items-center">
      <div class="flex flex-col items-center gap-3">
        <h1 class="text-4xl sm:text-7xl mb-4 font-semibold">404</h1>
        <h2 class="text-sm text-neutral-600">Page Not Found. You probably have the wrong link.</h2>
        <a
          routerLink="/~"
          class="text-sm text-neutral-600 px-5 py-2 bg-neutral-100/70 hover:bg-neutral-100 border border-neutral-500/7 shadow-2xs w-fit rounded-full"
        >
          Go Back Home
        </a>
      </div>
    </div>
  `,
})
export class NotFoundComponent {}
