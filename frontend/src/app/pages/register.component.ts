import { Router, RouterLink } from '@angular/router';
import { Component, inject } from '@angular/core';
import {
  AbstractControl,
  FormBuilder,
  ValidationErrors,
  ValidatorFn,
  Validators,
  ReactiveFormsModule,
} from '@angular/forms';

import { AuthService } from '../services/auth.service';
import { ButtonComponent } from '@/components/ui/button.component';
import { InputComponent } from '@/components/ui/input.component';
import { ComboboxComponent } from '@/components/ui/combobox.component';

const passwordMatchValidator: ValidatorFn = (group: AbstractControl): ValidationErrors | null => {
  const password = group.get('password')?.value;
  const confirmPassword = group.get('confirmPassword')?.value;
  return password === confirmPassword ? null : { passwordMismatch: true };
};

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [ButtonComponent, InputComponent, ReactiveFormsModule, RouterLink, ComboboxComponent],
  template: `
    <div class="max-w-100 m-15 mx-auto flex flex-col">
      <h1 class="mb-8 text-xl md:text-3xl font-semibold text-center">Welcome to Quickpoll</h1>
      @if (error) {
        <p class="text-destructive text-xs mb-8">{{ error }}</p>
      }
      <form
        id="register-form"
        class="flex flex-col gap-5"
        [formGroup]="registerForm"
        (ngSubmit)="onSubmit()"
      >
        <div class="flex gap-3">
          <div class="flex-1">
            <input
              app-input
              type="text"
              name="firstName"
              formControlName="firstName"
              placeholder="First name"
              required
            />
            @if (registerForm.get('firstName')?.touched && registerForm.get('firstName')?.invalid) {
              <div
                id="first-name-error"
                class="form-field-error"
                role="alert"
                aria-live="assertive"
              >
                @if (registerForm.get('firstName')?.errors?.['required']) {
                  <span>First name is required.</span>
                }
              </div>
            }
          </div>
          <div class="flex-1">
            <input
              app-input
              type="text"
              name="lastName"
              formControlName="lastName"
              placeholder="Last name"
              required
            />
            @if (registerForm.get('lastName')?.touched && registerForm.get('lastName')?.invalid) {
              <div id="last-name-error" class="form-field-error" role="alert" aria-live="assertive">
                @if (registerForm.get('lastName')?.errors?.['required']) {
                  <span>Last name is required.</span>
                }
              </div>
            }
          </div>
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

        <app-combobox placeholder="Select a department"/>

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

        <div>
          <input
            app-input
            type="password"
            name="confirmPassword"
            formControlName="confirmPassword"
            placeholder="Confirm password"
            required
          />
          @if (
            registerForm.get('confirmPassword')?.touched &&
            (registerForm.get('confirmPassword')?.invalid ||
              registerForm.hasError('passwordMismatch'))
          ) {
            <div
              id="confirm-password-error"
              class="form-field-error"
              role="alert"
              aria-live="assertive"
            >
              @if (registerForm.get('confirmPassword')?.errors?.['required']) {
                <span>Please confirm your password.</span>
              }
              @if (
                !registerForm.get('confirmPassword')?.errors?.['required'] &&
                registerForm.hasError('passwordMismatch')
              ) {
                <span>Passwords do not match.</span>
              }
            </div>
          }
        </div>

        <button
          app-button
          type="submit"
          (click)="registerForm.markAllAsTouched()"
          class="rounded-full!"
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
  protected registerForm = this.formBuilder.group(
    {
      firstName: ['', [Validators.required]],
      lastName: ['', [Validators.required]],
      email: ['', [Validators.required, Validators.email]],
      password: ['', [Validators.required, Validators.minLength(6)]],
      confirmPassword: ['', [Validators.required]],
    },
    { validators: passwordMatchValidator },
  );

  private authService = inject(AuthService);
  private router = inject(Router);
  protected error: string | null = null;

  onSubmit() {
    if (this.registerForm.invalid) return;

    const { firstName, lastName, email, password } = this.registerForm.value;
    const name = `${firstName!.trim()} ${lastName!.trim()}`;
    this.authService.register(name, email!, password!).subscribe({
      next: () => this.router.navigate(['/polls']),
      error: () => (this.error = 'Registration failed. Please try again.'),
    });
  }
}
