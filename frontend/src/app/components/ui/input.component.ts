import { Component, ChangeDetectionStrategy } from '@angular/core';
import { NgpInput } from 'ng-primitives/input';

@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  selector: 'input[app-input]',
  hostDirectives: [{ directive: NgpInput, inputs: ['disabled'] }],
  template: '',
  styles: `
    :host {
      height: 2.5rem;
      width: 100%;
      border-radius: var(--radius);
      padding: 0 16px;
      background-color: var(--input);
      color: var(--foreground);
      outline: none;
      box-sizing: border-box;
      transition: all 200ms ease-out;
      font-size: 13px;
    }

    :host:focus {
      outline: 2px solid var(--ring);
      outline-offset: 2px;
      border-color: var(--ring);
    }

    :host::placeholder {
      color: var(--muted-foreground);
    }
  `,
})
export class InputComponent {}
