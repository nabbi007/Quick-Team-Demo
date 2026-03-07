import { Injectable } from "@angular/core";
import { HttpClient } from "@angular/common/http";
import { Observable, tap } from "rxjs";

@Injectable({ providedIn: "root" })
export class AuthService {
  private apiUrl = "/api/auth";

  constructor(private http: HttpClient) {}

  login(email: string, password: string): Observable<any> {
    return this.http.post(`${this.apiUrl}/login`, { email, password })
      .pipe(tap((res: any) => {
        localStorage.setItem("token", res.token);
        localStorage.setItem("user", JSON.stringify({ name: res.name, email: res.email, role: res.role }));
      }));
  }

  register(name: string, email: string, password: string): Observable<any> {
    return this.http.post(`${this.apiUrl}/register`, { name, email, password })
      .pipe(tap((res: any) => {
        localStorage.setItem("token", res.token);
        localStorage.setItem("user", JSON.stringify({ name: res.name, email: res.email, role: res.role }));
      }));
  }

  logout(): void {
    localStorage.removeItem("token");
    localStorage.removeItem("user");
  }

  isLoggedIn(): boolean {
    return !!localStorage.getItem("token");
  }

  getToken(): string | null {
    return localStorage.getItem("token");
  }

  getUser(): any {
    const u = localStorage.getItem("user");
    return u ? JSON.parse(u) : null;
  }
}
