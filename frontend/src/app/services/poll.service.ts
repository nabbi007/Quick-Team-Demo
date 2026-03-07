import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class PollService {
  private apiUrl = 'http://localhost:8080/api/polls';

  constructor(private http: HttpClient) {}

  getAll(page = 0, size = 10): Observable<any> {
    return this.http.get(`${this.apiUrl}?page=${page}&size=${size}`);
  }

  getById(id: number): Observable<any> {
    return this.http.get(`${this.apiUrl}/${id}`);
  }

  create(poll: any): Observable<any> {
    return this.http.post(this.apiUrl, poll);
  }

  // TODO: Implement vote method
  // vote(pollId: number, optionIds: number[]): Observable<any> { ... }

  // TODO: Implement close poll method
}
