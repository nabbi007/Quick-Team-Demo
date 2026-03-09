import {
  afterNextRender,
  Component,
  DestroyRef,
  ElementRef,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';
import { ButtonComponent } from '../ui/button.component';
import { UserMenuComponent } from '../ui/user-menu.component';

@Component({
  selector: 'app-layout',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, ButtonComponent, UserMenuComponent],
  template: `
    <div class="flex flex-col min-h-screen">
      <div class="border-b">
        <header class="p-3 pb-0 w-full flex flex-col maxview-container">
          <div class="flex items-center justify-between p-1">
            Quickpoll
            <app-user-menu />
          </div>
          <nav #navElement class="relative flex gap-1 mt-2 items-center pb-2">
            @for (link of navLinks; track link.path) {
              <button
                data-nav-link
                [routerLink]="link.path"
                routerLinkActive="active"
                app-button
                variant="ghost"
                size="sm"
                class="rounded-md!"
              >
                {{ link.label }}
              </button>
            }

            <div
              aria-hidden="true"
              class="nav-indicator"
              [style.width.px]="indicatorWidth()"
              [style.transform]="'translateX(' + indicatorLeft() + 'px)'"
            ></div>
          </nav>
        </header>
      </div>
      <main class="flex-1">
        <router-outlet />
      </main>
    </div>
  `,
  styles: `
    :host {
      display: block;
      height: 100%;
    }

    .nav-indicator {
      position: absolute;
      left: 0;
      bottom: 0;
      height: 2px;
      background: #000;
      border-radius: 9999px 9999px 0 0;
      pointer-events: none;
      transition:
        transform 200ms ease,
        width 200ms ease;
      will-change: transform, width;
    }
  `,
})
export class DashboardLayoutComponent {
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly navElement = viewChild.required<ElementRef<HTMLElement>>('navElement');

  protected readonly indicatorLeft = signal(0);
  protected readonly indicatorWidth = signal(0);

  protected navLinks = [
    {
      label: 'Polls',
      path: '/~/polls',
    },
    {
      label: 'Account',
      path: '/~/account',
    },
  ];

  constructor() {
    this.router.events
      .pipe(
        filter((event) => event instanceof NavigationEnd),
        takeUntilDestroyed(),
      )
      .subscribe(() => {
        requestAnimationFrame(() => this.updateIndicator());
      });

    afterNextRender(() => {
      this.updateIndicator();

      const resizeObserver = new ResizeObserver(() => this.updateIndicator());
      resizeObserver.observe(this.navElement().nativeElement);

      this.destroyRef.onDestroy(() => resizeObserver.disconnect());
    });
  }

  private updateIndicator(): void {
    const activeLink =
      this.navElement().nativeElement.querySelector<HTMLElement>('[data-nav-link].active');

    if (!activeLink) {
      this.indicatorLeft.set(0);
      this.indicatorWidth.set(0);
      return;
    }

    this.indicatorLeft.set(activeLink.offsetLeft);
    this.indicatorWidth.set(activeLink.offsetWidth);
  }
}
