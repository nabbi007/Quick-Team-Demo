import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';

const BASE_URL = 'http://localhost:8080/api';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly AUTH_TOKEN_KEY = 'quickpoll-auth-token';
  private readonly authApiUrl = `${BASE_URL}/auth`;
  private readonly usersApiUrl = `${BASE_URL}/users`;
  private readonly http = inject(HttpClient);

  login(email: string, password: string): Observable<any> {
    return this.http.post(`${this.authApiUrl}/login`, { email, password }).pipe(
      tap((res: any) => {
        localStorage.setItem(this.AUTH_TOKEN_KEY, res.token);
      }),
    );
  }

  register(name: string, email: string, password: string): Observable<any> {
    return this.http.post(`${this.authApiUrl}/register`, { name, email, password }).pipe(
      tap((res: any) => {
        localStorage.setItem(this.AUTH_TOKEN_KEY, res.token);
      }),
    );
  }

  logout(): void {
    localStorage.removeItem(this.AUTH_TOKEN_KEY);
  }

  isLoggedIn(): boolean {
    return !!this.getToken();
  }

  getToken(): string | null {
    return localStorage.getItem(this.AUTH_TOKEN_KEY);
  }

  getProfile(): Observable<any> {
    return this.http.get(`${this.usersApiUrl}/me`);
  }

  updateUser(name: string): Observable<any> {
    return this.http.put(this.usersApiUrl, { name });
  }
}
