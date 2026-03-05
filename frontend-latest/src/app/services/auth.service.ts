import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly AUTH_TOKEN_KEY = 'quickpoll-auth-token';
  private readonly apiUrl = '/api/auth';
  protected readonly http = inject(HttpClient);

  login(email: string, password: string): Observable<any> {
    return this.http.post(`${this.apiUrl}/login`, { email, password }).pipe(
      tap((res: any) => {
        localStorage.setItem(this.AUTH_TOKEN_KEY, res.token);
        localStorage.setItem(
          'user',
          JSON.stringify({ name: res.name, email: res.email, role: res.role }),
        );
      }),
    );
  }

  register(name: string, email: string, password: string): Observable<any> {
    return this.http.post(`${this.apiUrl}/register`, { name, email, password }).pipe(
      tap((res: any) => {
        localStorage.setItem(this.AUTH_TOKEN_KEY, res.token);
        localStorage.setItem(
          'user',
          JSON.stringify({ name: res.name, email: res.email, role: res.role }),
        );
      }),
    );
  }

  logout(): void {
    localStorage.removeItem(this.AUTH_TOKEN_KEY);
    localStorage.removeItem('user');
  }

  isLoggedIn(): boolean {
    return !!localStorage.getItem(this.AUTH_TOKEN_KEY);
  }

  getToken(): string | null {
    return localStorage.getItem(this.AUTH_TOKEN_KEY);
  }

  getUser(): any {
    // TODO: fix implementation, discuss with backend.
    const user = localStorage.getItem('user');
    return user ? JSON.parse(user) : null;
  }
}
