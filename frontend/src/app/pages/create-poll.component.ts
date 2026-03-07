import { Component } from '@angular/core';

import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { PollService } from '@/services/poll.service';

@Component({
  selector: 'app-create-poll',
  standalone: true,
  imports: [FormsModule],
  template: `
    <div style="max-width:600px;margin:60px auto">
      <h1>Create New Poll</h1>
      @if (error) {
        <p style="color:red">{{ error }}</p>
      }
      <form (ngSubmit)="onSubmit()">
        <input
          type="text"
          [(ngModel)]="question"
          name="question"
          placeholder="Your question"
          required
        />
        <textarea
          [(ngModel)]="description"
          name="description"
          rows="3"
          placeholder="Description (optional)"
        ></textarea>
        <h3>Options</h3>
        @for (opt of options; track $index; let i = $index) {
          <input
            type="text"
            [(ngModel)]="options[i]"
            [name]="'option' + i"
            [placeholder]="'Option ' + (i + 1)"
            required
          />
        }
        <button type="button" class="btn" style="margin-bottom:12px" (click)="addOption()">
          + Add Option
        </button>
        <label style="display:block;margin-bottom:12px">
          <input type="checkbox" [(ngModel)]="multipleChoice" name="multipleChoice" /> Allow
          multiple selections
        </label>
        <button type="submit" class="btn btn-primary" style="width:100%">Create Poll</button>
      </form>
    </div>
  `,
})
export class CreatePollComponent {
  question = '';
  description = '';
  multipleChoice = false;
  options = ['', ''];
  error = '';

  constructor(
    private pollService: PollService,
    private router: Router,
  ) {}

  addOption() {
    this.options.push('');
  }

  onSubmit() {
    const validOptions = this.options.filter((o) => o.trim());
    if (validOptions.length < 2) {
      this.error = 'At least 2 options required';
      return;
    }
    this.pollService
      .create({
        question: this.question,
        description: this.description,
        options: validOptions,
        multipleChoice: this.multipleChoice,
      })
      .subscribe({
        next: () => this.router.navigate(['/']),
        error: () => (this.error = 'Failed to create poll'),
      });
  }
}
