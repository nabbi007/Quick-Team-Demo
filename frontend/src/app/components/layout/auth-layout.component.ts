import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { NgOptimizedImage } from '@angular/common';

@Component({
  selector: 'app-layout',
  standalone: true,
  imports: [RouterOutlet, NgOptimizedImage],
  template: `
    <div class="flex min-h-screen">
      <main class="flex-1 grid place-items-center">
        <div class="w-full px-5">
          <router-outlet />
        </div>
      </main>
      <div class="relative w-1/2 max-md:hidden">
        <img ngSrc="/images/auth-layout-bg.jpg" fill style="object-fit:cover" alt="" priority />
      </div>
    </div>
  `,
  styles: `
    :host {
      display: block;
      height: 100%;
    }
  `,
})
export class AuthLayoutComponent {}
