import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

@Component({
  selector: 'app-layout',
  standalone: true,
  imports: [RouterOutlet],
  template: `
    <div class="flex min-h-screen">
      <main class="flex-1 grid place-items-center">
        <div class="w-full px-5">
          <router-outlet />
        </div>
      </main>
      <div
        class="w-full max-w-1/2 max-md:hidden bg-cover bg-center"
        style="background-image: url('/images/samsung-uk-n2FLiPRGaxk-unsplash.jpg')"
      ></div>
    </div>
  `,
  styles: `
    :host {
      display: block;
      height: 100%;
    }
  `,
})
export class LayoutComponent {}
