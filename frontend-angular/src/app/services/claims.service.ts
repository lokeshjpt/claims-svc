import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ClaimRequest, ClaimResponse } from '../models/claim';

export interface UserInfo {
  authenticated: boolean;
  username: string;
  roles: string[];
}

@Injectable({ providedIn: 'root' })
export class ClaimsService {
  private http = inject(HttpClient);
  private contextRoot = '/claims-svc';
  private apiBase = `${this.contextRoot}/api/v1/claims`;
  private uploadUrl = `${this.contextRoot}/process`;
  private meUrl = `${this.contextRoot}/api/v1/me`;

  process(request: ClaimRequest): Observable<ClaimResponse> {
    return this.http.post<ClaimResponse>(this.apiBase, request);
  }

  /** Server-rendered Thymeleaf HTML (used by the iframe fallback). */
  uploadCsv(file: File): Observable<string> {
    const form = new FormData();
    form.append('file', file);
    return this.http.post(this.uploadUrl, form, { responseType: 'text' });
  }

  /** JSON batch endpoint — returns one ClaimResponse per CSV row. */
  uploadCsvAsJson(file: File): Observable<ClaimResponse[]> {
    const form = new FormData();
    form.append('file', file);
    return this.http.post<ClaimResponse[]>(`${this.apiBase}/batch`, form);
  }

  /** Returns identity + roles of the currently-authenticated principal. */
  me(): Observable<UserInfo> {
    return this.http.get<UserInfo>(this.meUrl);
  }
}
