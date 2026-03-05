import { Component, input, ChangeDetectionStrategy } from '@angular/core';
import { NgpButton } from 'ng-primitives/button';

/**
 * The size of the button.
 */
export type ButtonSize = 'sm' | 'md' | 'lg' | 'xl';

/**
 * The variant of the button.
 */
export type ButtonVariant = 'primary' | 'secondary' | 'destructive' | 'outline' | 'ghost' | 'link';

@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  selector: 'button[app-button]',
  hostDirectives: [{ directive: NgpButton, inputs: ['disabled'] }],
  template: `
    <ng-content select="[slot=leading]" />
    <ng-content />
    <ng-content select="[slot=trailing]" />
  `,
  host: {
    '[attr.data-size]': 'size()',
    '[attr.data-variant]': 'variant()',
  },
  styles: `
    :host {
      padding-left: 1rem;
      padding-right: 1rem;
      border-radius: 9999px;
      color: var(--primary-foreground);
      border: none;
      height: 2.5rem;
      font-weight: 500;
      box-sizing: border-box;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      cursor: pointer;
      gap: 0.5rem;
    }

    :host[data-hover] {
      background-color: var(--primary-hover);
      transition: background-color 300ms ease-out;
    }

    :host[data-focus-visible] {
      outline: 2px solid var(--ring);
      outline-offset: 2px;
    }

    :host[data-press] {
      scale: 0.99;
      background-color: var(--ngp-background-active);
      transition: transform 300ms ease-in;
    }

    /* Size variants */
    :host[data-size='sm'] {
      height: 2rem;
      padding-left: 0.75rem;
      padding-right: 0.75rem;
      font-size: 0.875rem;
      --ng-icon__size: 0.875rem;
    }

    :host[data-size='md'],
    :host:not([data-size]) {
      height: 2.5rem;
      padding-left: 1rem;
      padding-right: 1rem;
      font-size: 0.875rem;
      --ng-icon__size: 0.875rem;
    }

    :host[data-size='lg'] {
      height: 3rem;
      padding-left: 1.25rem;
      padding-right: 1.25rem;
      font-size: 1rem;
      --ng-icon__size: 1rem;
    }

    :host[data-size='xl'] {
      height: 3.5rem;
      padding-left: 1.5rem;
      padding-right: 1.5rem;
      font-size: 1.125rem;
      --ng-icon__size: 1.125rem;
    }

    /* Variant styles */
    :host[data-variant='primary'],
    :host:not([data-variant]) {
      background-color: var(--primary);
      color: var(--primary-foreground);
      border: none;
    }

    :host[data-variant='primary'][data-hover],
    :host:not([data-variant])[data-hover] {
      background-color: var(--primary-hover);
    }

    :host[data-variant='primary'][data-press],
    :host:not([data-variant])[data-press] {
      background-color: var(--primary-active);
    }

    :host[data-variant='secondary'] {
      background-color: var(--secondary, #f1f5f9);
      color: var(--secondary-foreground, #0f172a);
      border: none;
    }

    :host[data-variant='secondary'][data-hover] {
      background-color: var(--ngp-secondary-background-hover, #e2e8f0);
    }

    :host[data-variant='secondary'][data-press] {
      background-color: var(--ngp-secondary-background-active, #cbd5e1);
    }

    :host[data-variant='destructive'] {
      background-color: var(--destructive, #ef4444);
      color: var(--destructive-foreground, #ffffff);
      border: none;
    }

    :host[data-variant='destructive'][data-hover] {
      background-color: var(--ngp-destructive-background-hover, #dc2626);
    }

    :host[data-variant='destructive'][data-press] {
      background-color: var(--ngp-destructive-background-active, #b91c1c);
    }

    :host[data-variant='outline'] {
      background-color: var(--surface);
      color: var(--primary);
      border: 1px solid var(--border, #e2e8f0);
      box-shadow: none;
    }

    :host[data-variant='outline'][data-hover] {
      background-color: var(--ngp-background-hover);
      border-color: var(--ngp-outline-border-hover, #cbd5e1);
    }

    :host[data-variant='outline'][data-press] {
      background-color: var(--ngp-outline-background-active, rgba(15, 23, 42, 0.1));
    }

    :host[data-variant='ghost'] {
      background-color: transparent;
      color: var(--primary);
      border: none;
      box-shadow: none;
    }

    :host[data-variant='ghost'][data-hover] {
      background-color: var(--ngp-background-hover);
    }

    :host[data-variant='ghost'][data-press] {
      background-color: var(--ngp-background-active);
    }

    :host[data-variant='link'] {
      background-color: transparent;
      color: var(--ngp-link-color, #3b82f6);
      border: none;
      box-shadow: none;
      text-decoration: underline;
      text-underline-offset: 4px;
    }

    :host[data-variant='link'][data-hover] {
      text-decoration-thickness: 2px;
    }

    :host[disabled] {
      opacity: 0.6;
      cursor: not-allowed;
    }
  `,
})
export class ButtonComponent {
  /**
   * The size of the button.
   */
  readonly size = input<ButtonSize>('md');

  /**
   * The variant of the button.
   */
  readonly variant = input<ButtonVariant>('primary');
}
