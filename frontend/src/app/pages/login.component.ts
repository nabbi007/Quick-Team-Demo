import { Component } from "@angular/core";
import { CommonModule } from "@angular/common";
import { FormsModule } from "@angular/forms";
import { Router } from "@angular/router";
import { AuthService } from "../services/auth.service";

@Component({
  selector: "app-login",
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div style="max-width:400px;margin:60px auto">
      <h1>Login</h1>
      @if (error) { <p style="color:red">{{ error }}</p> }
      <form (ngSubmit)="onSubmit()">
        <input type="email" [(ngModel)]="email" name="email" placeholder="Email" required>
        <input type="password" [(ngModel)]="password" name="password" placeholder="Password" required>
        <button type="submit" class="btn btn-primary" style="width:100%">Login</button>
      </form>
    </div>
  `
})
export class LoginComponent {
  email = ""; password = ""; error = "";

  constructor(private authService: AuthService, private router: Router) {}

  onSubmit() {
    this.authService.login(this.email, this.password).subscribe({
      next: () => this.router.navigate(["/"]),
      error: () => this.error = "Invalid email or password"
    });
  }
}
