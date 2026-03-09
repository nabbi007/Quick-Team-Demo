import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '@/services/auth.service';
import { ButtonComponent } from '@/components/ui/button.component';
import { InputComponent } from '@/components/ui/input.component';

@Component({
  selector: 'app-profile',
  standalone: true,
  imports: [ButtonComponent, InputComponent, FormsModule],
  template: `
    <div class="flex flex-col gap-3">

      <div class="border bg-surface flex flex-col gap-2 px-4 py-6 rounded-lg">
        <p class="text-sm text-muted-foreground font-medium">Name</p>
        @if (isEditing()) {
          <input app-input [(ngModel)]="editName" />
        } @else {
          <p class="text-sm">{{ user()?.name }}</p>
        }
      </div>

      <div class="border bg-surface flex flex-col gap-2 px-4 py-6 rounded-lg">
        <p class="text-sm text-muted-foreground font-medium">Email</p>
        <p class="text-sm">{{ user()?.email }}</p>
      </div>

      <div class="border bg-surface flex flex-col gap-2 px-4 py-6 rounded-lg">
        <p class="text-sm text-muted-foreground font-medium">Role</p>
        <p class="text-sm">{{ user()?.role }}</p>
      </div>

      <div class="flex gap-2">
        @if (isEditing()) {
          <button app-button variant="primary" (click)="saveEdit()">Save</button>
          <button app-button variant="outline" (click)="cancelEdit()">Cancel</button>
        } @else {
          <button app-button variant="primary" (click)="startEdit()">Edit Profile</button>
        }
        <button app-button variant="destructive" (click)="logout()">Logout</button>
      </div>

    </div>
  `,
})
export class ProfileComponent implements OnInit {
  private authService = inject(AuthService);
  private router = inject(Router);

  protected user = signal<any>(null);
  protected isEditing = signal(false);
  protected editName = '';

  ngOnInit() {
    this.authService.getProfile().subscribe((profile) => {
      this.user.set(profile);
    });
  }

  startEdit() {
    this.editName = this.user()?.name ?? '';
    this.isEditing.set(true);
  }

  cancelEdit() {
    this.isEditing.set(false);
  }

  saveEdit() {
    this.authService.updateUser(this.editName).subscribe((updated) => {
      this.user.set(updated);
      this.isEditing.set(false);
    });
  }

  logout() {
    this.authService.logout();
    this.router.navigate(['/auth/login']);
  }
}
