import { Router, RouterLink } from '@angular/router';
import { Component, inject } from '@angular/core';
import { FormBuilder, Validators, ReactiveFormsModule } from '@angular/forms';

import { AuthService } from '../services/auth.service';
import { ButtonComponent } from '@/components/ui/button.component';
import { InputComponent } from '@/components/ui/input.component';

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [ButtonComponent, InputComponent, ReactiveFormsModule, RouterLink],
  template: `
    <div class="max-w-100 m-15 mx-auto flex flex-col">
      <h1 class="mb-8 text-xl md:text-3xl font-semibold text-center">Welcome to Quickpoll</h1>
      @if (error) {
        <p class="text-destructive text-xs mb-8">{{ error }}</p>
      }
      <form class="flex flex-col gap-5" [formGroup]="registerForm" (ngSubmit)="onSubmit()">
        <div>
          <input
            app-input
            type="text"
            name="name"
            formControlName="name"
            placeholder="Full name"
            required
          />
          @if (registerForm.get('name')?.touched && registerForm.get('name')?.invalid) {
            <div id="name-error" class="form-field-error" role="alert" aria-live="assertive">
              @if (registerForm.get('name')?.errors?.['required']) {
                <span>Full name is required.</span>
              }
            </div>
          }
        </div>

        <div>
          <input
            app-input
            type="email"
            name="email"
            formControlName="email"
            placeholder="Email address"
            required
          />
          @if (registerForm.get('email')?.touched && registerForm.get('email')?.invalid) {
            <div id="email-error" class="form-field-error" role="alert" aria-live="assertive">
              @if (registerForm.get('email')?.errors?.['required']) {
                <span>Email address is required.</span>
              }
              @if (registerForm.get('email')?.errors?.['email']) {
                <span>Please enter a valid email address.</span>
              }
            </div>
          }
        </div>

        <div>
          <input
            app-input
            type="password"
            name="password"
            formControlName="password"
            placeholder="Password"
            required
          />
          @if (registerForm.get('password')?.touched && registerForm.get('password')?.invalid) {
            <div id="password-error" class="form-field-error" role="alert" aria-live="assertive">
              @if (registerForm.get('password')?.errors?.['required']) {
                <span>Password is required.</span>
              }
              @if (registerForm.get('password')?.errors?.['minlength']) {
                <span>Password must be at least 6 characters.</span>
              }
            </div>
          }
        </div>

        <button
          app-button
          type="submit"
          (click)="registerForm.markAllAsTouched()"
        >
          Register
        </button>
      </form>
      <div class="mt-6 text-center text-xs inline-flex items-center justify-center gap-1">
        <p>Already have an account?</p>
        <a routerLink="/auth/login" class="font-medium">Login</a>
      </div>
    </div>
  `,
})
export class RegisterComponent {
  private formBuilder = inject(FormBuilder);
  protected registerForm = this.formBuilder.group({
    name: ['', [Validators.required]],
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(6)]],
  });

  private authService = inject(AuthService);
  private router = inject(Router);
  protected error: string | null = null;

  onSubmit() {
    if (this.registerForm.invalid) return;

    const { name, email, password } = this.registerForm.value;
    this.authService.register(name!, email!, password!).subscribe({
      next: () => this.router.navigate(['/']),
      error: () => (this.error = 'Registration failed. Please try again.'),
    });
  }
}
