import { Component, inject } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '@/services/auth.service';
import { ButtonComponent } from '@/components/ui/button.component';

@Component({
  selector: 'app-profile',
  standalone: true,
  imports: [ButtonComponent],
  template: `
    <div class="flex flex-col gap-3">
      <p><strong>Name:</strong> {{ user.name }}</p>
      <p><strong>Email:</strong> {{ user.email }}</p>
      <p><strong>Role:</strong> {{ user.role }}</p>
      <div class="flex gap-2">
        <button app-button variant="primary">Edit Profile</button>
        <button app-button variant="destructive" (click)="logout()">Logout</button>
      </div>
    </div>
  `,
})
export class ProfileComponent {
  private authService = inject(AuthService);
  private router = inject(Router);
  protected user = this.authService.getUser();

  logout() {
    this.authService.logout();
    this.router.navigate(['/auth/login']);
  }
}
