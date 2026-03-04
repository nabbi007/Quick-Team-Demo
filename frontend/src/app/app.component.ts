import { Component } from "@angular/core";
import { RouterOutlet, RouterLink } from "@angular/router";
import { CommonModule } from "@angular/common";
import { AuthService } from "./services/auth.service";

@Component({
  selector: "app-root",
  standalone: true,
  imports: [RouterOutlet, RouterLink, CommonModule],
  template: `
    <nav class="navbar">
      <a routerLink="/" style="font-size:20px;font-weight:bold">QuickPoll</a>
      <div>
        <a routerLink="/">Polls</a>
        @if (authService.isLoggedIn()) {
          <a routerLink="/create">Create Poll</a>
          <span style="margin-left:20px">{{ authService.getUser()?.name }}</span>
          <a href="#" (click)="authService.logout(); $event.preventDefault()" style="margin-left:20px">Logout</a>
        } @else {
          <a routerLink="/login">Login</a>
          <a routerLink="/register">Register</a>
        }
      </div>
    </nav>
    <div class="container">
      <router-outlet></router-outlet>
    </div>
  `
})
export class AppComponent {
  constructor(public authService: AuthService) {}
}
