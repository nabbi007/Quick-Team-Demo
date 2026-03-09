import { Observable } from 'rxjs';
import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Department } from '@/models';
import { API_BASE_URL } from '@/constants';

@Injectable({ providedIn: 'root' })
export class DepartmentService {
  private readonly departmentsApiUrl = `${API_BASE_URL}/departments`;
  private readonly http = inject(HttpClient);

  getAll(): Observable<Department[]> {
    return this.http.get<Department[]>(this.departmentsApiUrl);
  }

  getById(id: number): Observable<Department> {
    return this.http.get<Department>(`${this.departmentsApiUrl}/${id}`);
  }
}
