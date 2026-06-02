import { Component, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ClaimsService } from './services/claims.service';
import { ClaimRequest, ClaimResponse } from './models/claim';

type View = 'dashboard' | 'rest' | 'upload' | 'about';

interface Sample {
  name: string;
  payload: ClaimRequest;
}

interface HistoryEntry {
  at: Date;
  source: 'REST' | 'CSV';
  request: ClaimRequest;
  response: ClaimResponse;
}

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="app-shell">

      <!-- ============================== SIDEBAR ============================== -->
      <aside class="sidebar">
        <div class="brand">
          <h5><i class="bi bi-shield-plus me-2"></i>Claims Processor</h5>
          <small>ABC Health · v1.0</small>
        </div>
        <nav>
          <button [class.active]="view() === 'dashboard'" (click)="view.set('dashboard')">
            <i class="bi bi-speedometer2"></i><span>Dashboard</span>
          </button>
          <button *ngIf="isAdmin()" [class.active]="view() === 'rest'" (click)="view.set('rest')">
            <i class="bi bi-code-slash"></i><span>REST API</span>
          </button>
          <button [class.active]="view() === 'upload'" (click)="view.set('upload')">
            <i class="bi bi-cloud-arrow-up"></i><span>Upload CSV</span>
          </button>
          <button [class.active]="view() === 'about'" (click)="view.set('about')">
            <i class="bi bi-info-circle"></i><span>About</span>
          </button>
        </nav>
        <div class="side-foot">
          Angular 18 · Bootstrap 5<br>
          Proxied to Spring Boot :8080
        </div>
      </aside>

      <!-- ============================== MAIN ============================== -->
      <section class="main">
        <header class="topbar">
          <h4>{{ titleFor(view()) }}</h4>
          <div class="d-flex gap-2 align-items-center">
            <span class="badge text-bg-light"><i class="bi bi-database"></i> {{ history().length }} processed</span>
            <span class="badge text-bg-success" *ngIf="okCount() > 0">{{ okCount() }} OK</span>
            <span class="badge text-bg-danger" *ngIf="errorCount() > 0">{{ errorCount() }} errors</span>
            <span class="vr mx-1"></span>
            <span *ngIf="currentUser()" class="badge text-bg-primary">
              <i class="bi bi-person-circle"></i> {{ currentUser() }}
            </span>
            <span *ngIf="isAdmin()" class="badge text-bg-warning text-dark" title="ADMIN role">
              <i class="bi bi-shield-lock"></i> ADMIN
            </span>
            <span *ngIf="currentUser() && !isAdmin()" class="badge text-bg-secondary" title="PROCESSOR role">
              <i class="bi bi-person-badge"></i> PROCESSOR
            </span>
            <button *ngIf="currentUser()" class="btn btn-sm btn-outline-secondary" (click)="signOut()">
              <i class="bi bi-box-arrow-right"></i> Sign out
            </button>
            <button class="btn btn-sm btn-outline-secondary"
                    (click)="clearHistory()" [disabled]="history().length === 0">
              <i class="bi bi-trash"></i> Clear
            </button>
          </div>
        </header>

        <div class="content">

          <!-- ====================== SIGN-IN ====================== -->
          <div *ngIf="!currentUser()" class="card shadow-sm mb-4 border-warning">
            <div class="card-body">
              <h5 class="card-title text-warning">
                <i class="bi bi-lock"></i> Sign in to call protected APIs
              </h5>
              <p class="text-muted small mb-3">
                The Spring Boot backend requires HTTP Basic authentication.
                Default users:
                <code>admin/admin123</code> (sees all menus including REST API) or
                <code>processor/claims123</code> (Dashboard + Upload CSV only).
              </p>
              <div class="row g-2">
                <div class="col-md-4">
                  <input class="form-control" placeholder="Username"
                         [(ngModel)]="loginUser" (keyup.enter)="signIn()">
                </div>
                <div class="col-md-4">
                  <input class="form-control" type="password" placeholder="Password"
                         [(ngModel)]="loginPass" (keyup.enter)="signIn()">
                </div>
                <div class="col-md-4">
                  <button class="btn btn-primary w-100" (click)="signIn()"
                          [disabled]="!loginUser || !loginPass">
                    <i class="bi bi-box-arrow-in-right"></i> Sign in
                  </button>
                </div>
              </div>
            </div>
          </div>

          <!-- ====================== DASHBOARD ====================== -->
          <ng-container *ngIf="view() === 'dashboard'">
            <div class="row g-3 mb-4">
              <div class="col-md-3">
                <div class="kpi-card card p-3 accent-primary">
                  <i class="bi bi-receipt kpi-icon"></i>
                  <div class="kpi-label">Total Claims</div>
                  <div class="kpi-value">{{ history().length }}</div>
                </div>
              </div>
              <div class="col-md-3">
                <div class="kpi-card card p-3 accent-success">
                  <i class="bi bi-cash-coin kpi-icon"></i>
                  <div class="kpi-label">Plan Paid</div>
                  <div class="kpi-value">{{ totalPlanPays() | currency }}</div>
                </div>
              </div>
              <div class="col-md-3">
                <div class="kpi-card card p-3 accent-warning">
                  <i class="bi bi-wallet2 kpi-icon"></i>
                  <div class="kpi-label">Holder Paid</div>
                  <div class="kpi-value">{{ totalHolderPays() | currency }}</div>
                </div>
              </div>
              <div class="col-md-3">
                <div class="kpi-card card p-3 accent-danger">
                  <i class="bi bi-exclamation-triangle kpi-icon"></i>
                  <div class="kpi-label">Errors</div>
                  <div class="kpi-value">{{ errorCount() }}</div>
                </div>
              </div>
            </div>

            <div class="row g-3">
              <!-- Rule breakdown -->
              <div class="col-lg-5">
                <div class="card shadow-sm h-100">
                  <div class="card-header bg-white">
                    <h6 class="mb-0"><i class="bi bi-pie-chart"></i> Rule Breakdown</h6>
                  </div>
                  <div class="card-body">
                    <div *ngIf="ruleBreakdown().length === 0" class="text-muted text-center py-4">
                      <i class="bi bi-inbox display-6 d-block"></i>
                      No claims yet. Submit one from REST API or Upload CSV.
                    </div>
                    <div *ngFor="let r of ruleBreakdown()" class="mb-3">
                      <div class="d-flex justify-content-between small mb-1">
                        <span class="fw-semibold">{{ r.rule }}</span>
                        <span class="text-muted">{{ r.count }} ({{ r.pct }}%)</span>
                      </div>
                      <div class="progress" style="height: 8px;">
                        <div class="progress-bar" [style.width.%]="r.pct" [class]="r.css"></div>
                      </div>
                    </div>
                  </div>
                </div>
              </div>

              <!-- Recent claims table -->
              <div class="col-lg-7">
                <div class="card shadow-sm h-100">
                  <div class="card-header bg-white d-flex justify-content-between align-items-center">
                    <h6 class="mb-0"><i class="bi bi-clock-history"></i> Recent Claims</h6>
                    <div class="d-flex gap-2 align-items-center">
                      <span class="chip chip-ok" *ngIf="okCount() > 0">
                        <i class="bi bi-check-circle-fill"></i>{{ okCount() }} OK
                      </span>
                      <span class="chip chip-error" *ngIf="errorCount() > 0">
                        <i class="bi bi-exclamation-triangle-fill"></i>{{ errorCount() }} errors
                      </span>
                    </div>
                  </div>
                  <div class="card-body p-0">
                    <div *ngIf="recent().length === 0" class="empty">
                      <i class="bi bi-hourglass"></i>
                      <div>No claims yet. Submit one from REST API or Upload CSV.</div>
                    </div>
                    <div *ngIf="recent().length > 0" class="table-responsive">
                      <table class="claims-table">
                        <thead>
                          <tr>
                            <th>When</th>
                            <th>Source</th>
                            <th>Policy / Holder</th>
                            <th>Service</th>
                            <th>Rule</th>
                            <th class="money">Billed</th>
                            <th class="money">Plan</th>
                            <th class="money">Holder</th>
                            <th>Status</th>
                            <th>Processing message</th>
                          </tr>
                        </thead>
                        <tbody>
                          <tr *ngFor="let h of recent()"
                              [class.row-error]="!!h.response.errorCode"
                              [class.row-nocharge]="h.response.ruleUsed === 'No Charge'">
                            <td class="when-cell">{{ h.at | date:'shortTime' }}</td>
                            <td>
                              <span class="chip" [class.chip-rest]="h.source === 'REST'" [class.chip-csv]="h.source === 'CSV'">
                                <i class="bi" [class.bi-code-slash]="h.source === 'REST'" [class.bi-filetype-csv]="h.source === 'CSV'"></i>
                                {{ h.source }}
                              </span>
                            </td>
                            <td>
                              <div class="holder-cell">{{ h.response.policyHolderId }}</div>
                              <div class="when-cell">{{ h.response.policyId }}</div>
                            </td>
                            <td class="small">
                              <div>{{ h.request.coverageSubCategory }}</div>
                              <div class="when-cell">{{ h.request.dateOfService }}</div>
                            </td>
                            <td>
                              <span class="chip" [ngClass]="ruleChip(h.response.ruleUsed)">
                                {{ h.response.ruleUsed || '—' }}
                              </span>
                            </td>
                            <td class="money">{{ h.request.billedAmount | currency }}</td>
                            <td class="money money-success">{{ h.response.planPays | currency }}</td>
                            <td class="money money-warning">{{ h.response.policyHolderPays | currency }}</td>
                            <td>
                              <span *ngIf="!h.response.errorCode" class="chip chip-ok">
                                <i class="bi bi-check-circle-fill"></i>OK
                              </span>
                              <span *ngIf="h.response.errorCode" class="chip" [ngClass]="errorChip(h.response.errorCode)">
                                <i class="bi bi-exclamation-triangle-fill"></i>
                                {{ h.response.errorCode }}
                              </span>
                            </td>
                            <td class="msg-cell" [title]="h.response.processingMessage || h.response.errorMessage || ''">
                              {{ h.response.processingMessage || h.response.errorMessage || '—' }}
                            </td>
                          </tr>
                        </tbody>
                      </table>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </ng-container>

          <!-- ====================== REST API ====================== -->
          <ng-container *ngIf="view() === 'rest' && isAdmin()">
            <div class="card shadow-sm">
              <div class="card-body">
                <div class="row mb-3">
                  <div class="col-md-6">
                    <label class="form-label fw-semibold">Pick a sample</label>
                    <select class="form-select" [(ngModel)]="selectedSampleName" (ngModelChange)="loadSample($event)">
                      <option *ngFor="let s of samples" [value]="s.name">{{ s.name }}</option>
                    </select>
                  </div>
                </div>

                <div class="row g-3">
                  <div class="col-md-3"><label class="form-label">Policy Id</label>
                    <input class="form-control" [(ngModel)]="form.policyId"></div>
                  <div class="col-md-3"><label class="form-label">Policy Holder Id</label>
                    <input class="form-control" [(ngModel)]="form.policyHolderId"></div>
                  <div class="col-md-3"><label class="form-label">Date of service</label>
                    <input class="form-control" type="date" [(ngModel)]="form.dateOfService"></div>
                  <div class="col-md-3"><label class="form-label">Billed Amount</label>
                    <input class="form-control" type="number" step="0.01" [(ngModel)]="form.billedAmount"></div>
                  <div class="col-md-6"><label class="form-label">Main Category</label>
                    <input class="form-control" [(ngModel)]="form.coverageMainCategory"></div>
                  <div class="col-md-6"><label class="form-label">Sub Category</label>
                    <input class="form-control" [(ngModel)]="form.coverageSubCategory"></div>
                  <div class="col-md-6"><label class="form-label">Individual deductible YTD</label>
                    <input class="form-control" type="number" step="0.01" [(ngModel)]="form.individualAccumulatedDeductible"></div>
                  <div class="col-md-6"><label class="form-label">Family deductible YTD</label>
                    <input class="form-control" type="number" step="0.01" [(ngModel)]="form.familyAccumulatedDeductible"></div>
                </div>

                <div class="d-grid mt-4">
                  <button class="btn btn-primary btn-lg" type="button" (click)="submit()" [disabled]="loading()">
                    <i class="bi bi-play-fill"></i>
                    {{ loading() ? 'Processing…' : 'POST /claims-svc/api/v1/claims' }}
                  </button>
                </div>

                <div *ngIf="error()" class="alert alert-danger mt-3">
                  <i class="bi bi-exclamation-triangle-fill me-1"></i>{{ error() }}
                </div>

                <div *ngIf="response() as r" class="mt-4">
                  <h5 class="mb-2"><i class="bi bi-receipt"></i> Response</h5>
                  <div class="row g-3">
                    <div class="col-md-3"><div class="border rounded p-2 bg-light">
                      <div class="text-muted small">Plan pays</div>
                      <div class="fs-4 text-success">{{ r.planPays | currency }}</div>
                    </div></div>
                    <div class="col-md-3"><div class="border rounded p-2 bg-light">
                      <div class="text-muted small">Holder pays</div>
                      <div class="fs-4 text-primary">{{ r.policyHolderPays | currency }}</div>
                    </div></div>
                    <div class="col-md-3"><div class="border rounded p-2 bg-light">
                      <div class="text-muted small">Rule used</div>
                      <div class="fs-6 pill">{{ r.ruleUsed || '—' }}</div>
                    </div></div>
                    <div class="col-md-3"><div class="border rounded p-2 bg-light">
                      <div class="text-muted small">Error</div>
                      <div>
                        <span *ngIf="r.errorCode" class="chip" [ngClass]="errorChip(r.errorCode)">
                          <i class="bi bi-exclamation-triangle-fill"></i>{{ r.errorCode }}
                        </span>
                        <span *ngIf="!r.errorCode" class="chip chip-ok">
                          <i class="bi bi-check-circle-fill"></i>OK
                        </span>
                      </div>
                    </div></div>
                  </div>
                  <pre class="mt-3 bg-dark text-light p-3 rounded" style="font-size: .8rem">{{ r | json }}</pre>
                </div>
              </div>
            </div>
          </ng-container>

          <!-- ====================== UPLOAD ====================== -->
          <ng-container *ngIf="view() === 'upload'">
            <div class="card shadow-sm">
              <div class="card-body">
                <h5 class="card-title"><i class="bi bi-cloud-arrow-up"></i> Upload claims CSV</h5>
                <p class="text-muted mb-3">
                  Sends the file to <code>POST /claims-svc/api/v1/claims/batch</code>.
                  Results render below in the same chip-styled table used on the dashboard.
                </p>

                <label class="drop-zone d-block" for="file-input"
                       (dragenter)="$event.preventDefault()"
                       (dragover)="$event.preventDefault()"
                       (drop)="onDrop($event)">
                  <i class="bi bi-filetype-csv display-4 text-primary"></i>
                  <div class="mt-2 fw-semibold">Click to choose a CSV file</div>
                  <div class="text-muted small">or drag &amp; drop it here</div>
                  <div *ngIf="selectedFile" class="mt-2 small text-success">{{ selectedFile.name }}</div>
                </label>
                <input class="d-none" type="file" id="file-input" accept=".csv" (change)="onFile($event)">

                <div class="d-grid mt-3">
                  <button class="btn btn-success btn-lg" type="button"
                          (click)="upload()" [disabled]="!selectedFile || loading()">
                    <i class="bi bi-upload"></i>
                    {{ loading() ? 'Uploading…' : 'Upload & process' }}
                  </button>
                </div>

                <div *ngIf="error()" class="alert alert-danger mt-3">
                  <i class="bi bi-exclamation-triangle-fill me-1"></i>{{ error() }}
                </div>

                <ng-container *ngIf="uploadResults() as rows">
                  <div class="mt-4 d-flex justify-content-between align-items-center">
                    <h5 class="mb-0"><i class="bi bi-table"></i> Processed claims ({{ rows.length }})</h5>
                    <div class="d-flex gap-2">
                      <span class="chip chip-ok">
                        <i class="bi bi-check-circle-fill"></i>{{ uploadOkCount() }} OK
                      </span>
                      <span class="chip chip-error" *ngIf="uploadErrorCount() > 0">
                        <i class="bi bi-exclamation-triangle-fill"></i>{{ uploadErrorCount() }} errors
                      </span>
                      <button class="btn btn-sm btn-outline-primary" (click)="view.set('dashboard')">
                        <i class="bi bi-speedometer2"></i> View on Dashboard
                      </button>
                      <button class="btn btn-sm btn-outline-success" (click)="downloadResultsCsv()">
                        <i class="bi bi-download"></i> Download CSV
                      </button>
                    </div>
                  </div>

                  <div class="card shadow-sm mt-2">
                    <div class="card-body p-0">
                      <div *ngIf="rows.length === 0" class="empty">
                        <i class="bi bi-inbox"></i><div>No claims found in the uploaded file.</div>
                      </div>
                      <div *ngIf="rows.length > 0" class="table-responsive" style="max-height: 70vh;">
                        <table class="claims-table">
                          <thead>
                            <tr>
                              <th>Policy / Holder</th>
                              <th>Date of service</th>
                              <th>Service</th>
                              <th>Rule</th>
                              <th class="money">Billed</th>
                              <th class="money">Plan pays</th>
                              <th class="money">Holder pays</th>
                              <th class="money">Indiv. YTD</th>
                              <th class="money">Family YTD</th>
                              <th>Status</th>
                              <th>Processing message</th>
                            </tr>
                          </thead>
                          <tbody>
                            <tr *ngFor="let r of rows"
                                [class.row-error]="!!r.errorCode"
                                [class.row-nocharge]="r.ruleUsed === 'No Charge'">
                              <td>
                                <div class="holder-cell">{{ r.policyHolderId }}</div>
                                <div class="when-cell">{{ r.policyId }}</div>
                              </td>
                              <td class="when-cell">{{ r.dateOfService }}</td>
                              <td class="small">
                                <div>{{ r.coverageSubCategory }}</div>
                                <div class="when-cell">{{ r.coverageMainCategory }}</div>
                              </td>
                              <td>
                                <span class="chip" [ngClass]="ruleChip(r.ruleUsed)">
                                  {{ r.ruleUsed || '—' }}
                                </span>
                              </td>
                              <td class="money">{{ r.billedAmount | currency }}</td>
                              <td class="money money-success">{{ (r.planPays ?? 0) | currency }}</td>
                              <td class="money money-warning">{{ (r.policyHolderPays ?? 0) | currency }}</td>
                              <td class="money when-cell">{{ (r.individualAccumulatedDeductible ?? 0) | currency }}</td>
                              <td class="money when-cell">{{ (r.familyAccumulatedDeductible ?? 0) | currency }}</td>
                              <td>
                                <span *ngIf="!r.errorCode" class="chip chip-ok">
                                  <i class="bi bi-check-circle-fill"></i>OK
                                </span>
                                <span *ngIf="r.errorCode" class="chip" [ngClass]="errorChip(r.errorCode)"
                                      [title]="r.errorMessage || ''">
                                  <i class="bi bi-exclamation-triangle-fill"></i>{{ r.errorCode }}
                                </span>
                              </td>
                              <td class="msg-cell" [title]="r.processingMessage || r.errorMessage || ''">
                                {{ r.processingMessage || r.errorMessage || '—' }}
                              </td>
                            </tr>
                          </tbody>
                        </table>
                      </div>
                    </div>
                  </div>
                </ng-container>
              </div>
            </div>
          </ng-container>

          <!-- ====================== ABOUT ====================== -->
          <ng-container *ngIf="view() === 'about'">
            <div class="card shadow-sm">
              <div class="card-body">
                <h5><i class="bi bi-info-circle"></i> About</h5>
                <p class="text-muted">
                  Claims Processor — assessment build. Java 25 + Spring Boot 3.4.5 backend with
                  strategy-pattern coverage rules, DAO-abstracted reference data
                  (Oracle / Redis / NoSQL ready) and a reusable deductible service.
                </p>
                <ul class="text-muted small">
                  <li>Backend: <code>http://localhost:8080/claims-svc</code></li>
                  <li>REST endpoint: <code>POST /claims-svc/api/v1/claims</code></li>
                  <li>CSV upload: <code>POST /process</code> (Thymeleaf renders results)</li>
                  <li>Batch CLI: <code>java -jar claims-processor.jar batch in.csv out.csv</code></li>
                </ul>
              </div>
            </div>
          </ng-container>

          <p class="footer">Angular 18 · Bootstrap 5 · proxied to Spring Boot on :8080</p>
        </div>
      </section>
    </div>
  `
})
export class AppComponent {
  private claims = inject(ClaimsService);

  readonly view = signal<View>('dashboard');
  readonly loading = signal(false);
  readonly response = signal<ClaimResponse | null>(null);
  readonly error = signal<string | null>(null);
  readonly uploadResults = signal<ClaimResponse[] | null>(null);
  readonly history = signal<HistoryEntry[]>([]);
  readonly currentUser = signal<string | null>(sessionStorage.getItem('claims.user'));
  readonly roles = signal<string[]>(this.loadStoredRoles());
  readonly isAdmin = computed(() => this.roles().includes('ADMIN'));

  constructor() {
    if (this.currentUser()) {
      this.refreshIdentity();
    }
  }

  private loadStoredRoles(): string[] {
    const raw = sessionStorage.getItem('claims.roles');
    if (!raw) { return []; }
    try { return JSON.parse(raw) as string[]; } catch { return []; }
  }

  private refreshIdentity(): void {
    this.claims.me().subscribe({
      next: info => {
        this.roles.set(info.roles ?? []);
        sessionStorage.setItem('claims.roles', JSON.stringify(info.roles ?? []));
        // If processor was viewing REST when role gating kicks in, bounce them home.
        if (!this.isAdmin() && this.view() === 'rest') {
          this.view.set('dashboard');
        }
      },
      error: () => {
        this.roles.set([]);
        sessionStorage.removeItem('claims.roles');
      }
    });
  }

  readonly uploadOkCount = computed(() => (this.uploadResults() ?? []).filter(r => !r.errorCode).length);
  readonly uploadErrorCount = computed(() => (this.uploadResults() ?? []).filter(r => !!r.errorCode).length);

  loginUser = '';
  loginPass = '';

  signIn(): void {
    if (!this.loginUser || !this.loginPass) { return; }
    const token = btoa(`${this.loginUser}:${this.loginPass}`);
    sessionStorage.setItem('claims.basicAuth', token);
    sessionStorage.setItem('claims.user', this.loginUser);
    this.currentUser.set(this.loginUser);
    this.loginUser = '';
    this.loginPass = '';
    this.refreshIdentity();
  }

  signOut(): void {
    sessionStorage.removeItem('claims.basicAuth');
    sessionStorage.removeItem('claims.user');
    sessionStorage.removeItem('claims.roles');
    this.currentUser.set(null);
    this.roles.set([]);
    if (this.view() === 'rest') { this.view.set('dashboard'); }
  }

  readonly okCount = computed(() => this.history().filter(h => !h.response.errorCode).length);
  readonly errorCount = computed(() => this.history().filter(h => !!h.response.errorCode).length);
  readonly totalPlanPays = computed(() =>
    this.history().reduce((s, h) => s + (h.response.planPays ?? 0), 0));
  readonly totalHolderPays = computed(() =>
    this.history().reduce((s, h) => s + (h.response.policyHolderPays ?? 0), 0));
  readonly recent = computed(() => this.history().slice(0, 10));
  readonly ruleBreakdown = computed(() => {
    const counts = new Map<string, number>();
    for (const h of this.history()) {
      const key = h.response.ruleUsed || (h.response.errorCode ? 'ERROR' : 'UNKNOWN');
      counts.set(key, (counts.get(key) ?? 0) + 1);
    }
    const total = this.history().length || 1;
    const palette = ['bg-primary', 'bg-success', 'bg-warning', 'bg-danger', 'bg-info', 'bg-secondary'];
    return Array.from(counts.entries())
      .sort((a, b) => b[1] - a[1])
      .map(([rule, count], i) => ({
        rule, count,
        pct: Math.round((count / total) * 100),
        css: palette[i % palette.length]!
      }));
  });

  selectedFile: File | null = null;
  selectedSampleName = 'Percentage after deductible (happy path)';

  samples: Sample[] = [
    {
      name: 'Percentage after deductible (happy path)',
      payload: {
        policyId: '100001', policyHolderId: '1000011', dateOfService: '2016-05-08',
        coverageMainCategory: 'Inpatient Hospital Care', coverageSubCategory: 'ROOM AND BOARD',
        billedAmount: 1000, individualAccumulatedDeductible: 6000, familyAccumulatedDeductible: 6000
      }
    },
    {
      name: 'No Charge (preventive care)',
      payload: {
        policyId: '100002', policyHolderId: '1000021', dateOfService: '2016-06-15',
        coverageMainCategory: 'Preventive Care', coverageSubCategory: 'ROUTINE PHYSICAL EXAM',
        billedAmount: 350, individualAccumulatedDeductible: 4000, familyAccumulatedDeductible: 4000
      }
    },
    {
      name: 'Flat dollar ($120)',
      payload: {
        policyId: '100007', policyHolderId: '1000071', dateOfService: '2016-07-10',
        coverageMainCategory: 'Emergency And Urgent Care', coverageSubCategory: 'URGENT CARE VISIT',
        billedAmount: 250, individualAccumulatedDeductible: 4460.82, familyAccumulatedDeductible: 4460.82
      }
    },
    {
      name: 'E0001 — unknown holder',
      payload: {
        policyId: '100001', policyHolderId: '9999999', dateOfService: '2016-10-12',
        coverageMainCategory: 'Prescription Drugs', coverageSubCategory: 'GENERIC',
        billedAmount: 61.4, individualAccumulatedDeductible: null, familyAccumulatedDeductible: null
      }
    },
    {
      name: 'E0004 — future-dated',
      payload: {
        policyId: '100002', policyHolderId: '1000021', dateOfService: '2017-01-15',
        coverageMainCategory: 'Outpatient Services', coverageSubCategory: 'LAB TESTS',
        billedAmount: 1200, individualAccumulatedDeductible: null, familyAccumulatedDeductible: null
      }
    },
    {
      name: 'E0005 — malformed (missing date)',
      payload: {
        policyId: '100001', policyHolderId: '1000011', dateOfService: null,
        coverageMainCategory: 'Inpatient Hospital Care', coverageSubCategory: 'ROOM AND BOARD',
        billedAmount: 1000, individualAccumulatedDeductible: null, familyAccumulatedDeductible: null
      }
    }
  ];

  form: ClaimRequest = structuredClone(this.samples[0]!.payload);

  titleFor(view: View): string {
    switch (view) {
      case 'dashboard': return 'Dashboard';
      case 'rest':      return 'REST API';
      case 'upload':    return 'Upload CSV';
      case 'about':     return 'About';
    }
  }

  loadSample(name: string): void {
    const sample = this.samples.find(s => s.name === name);
    if (sample) {
      this.form = structuredClone(sample.payload);
      this.response.set(null);
      this.error.set(null);
    }
  }

  submit(): void {
    this.loading.set(true);
    this.error.set(null);
    this.response.set(null);
    const req = structuredClone(this.form);
    this.claims.process(req).subscribe({
      next: r => {
        this.response.set(r);
        this.loading.set(false);
        this.pushHistory({ at: new Date(), source: 'REST', request: req, response: r });
      },
      error: e => { this.error.set(e?.error?.message || e?.message || 'Request failed'); this.loading.set(false); }
    });
  }

  onFile(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.selectedFile = input.files && input.files.length > 0 ? input.files[0]! : null;
  }

  onDrop(event: DragEvent): void {
    event.preventDefault();
    const files = event.dataTransfer?.files;
    if (files && files.length > 0) {
      this.selectedFile = files[0]!;
    }
  }

  upload(): void {
    if (!this.selectedFile) { return; }
    this.loading.set(true);
    this.uploadResults.set(null);
    this.error.set(null);
    const fileName = this.selectedFile.name;
    this.claims.uploadCsvAsJson(this.selectedFile).subscribe({
      next: rows => {
        this.uploadResults.set(rows);
        this.loading.set(false);
        const now = new Date();
        const entries: HistoryEntry[] = rows.map(r => ({
          at: now,
          source: 'CSV',
          request: {
            policyId: r.policyId,
            policyHolderId: r.policyHolderId,
            dateOfService: r.dateOfService,
            coverageMainCategory: r.coverageMainCategory,
            coverageSubCategory: r.coverageSubCategory,
            billedAmount: r.billedAmount
          },
          response: r
        }));
        // Single atomic update so dashboard signals (KPI cards, rule breakdown,
        // recent claims) recompute exactly once for the whole upload batch.
        this.history.update(list => [...entries.reverse(), ...list].slice(0, 200));
        console.info(`Processed ${rows.length} claims from ${fileName}; history now ${this.history().length}`);
      },
      error: e => {
        this.error.set(e?.error?.message || e?.message || 'Upload failed');
        this.loading.set(false);
      }
    });
  }

  downloadResultsCsv(): void {
    const rows = this.uploadResults();
    if (!rows || rows.length === 0) { return; }
    const headers = [
      'PolicyId','Policy holder Id','Date of service','Coverage Main Category','Coverage Sub Category',
      'Billed Amount','Policy Holder pays','Plan Pays','Rule used',
      'Individual accumulated deductible','Family accumulated deductible',
      'Error Code','Error Message','Processing message'
    ];
    const escape = (v: unknown) => {
      const s = v == null ? '' : String(v);
      return /[",\n]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s;
    };
    const lines = [headers.join(',')];
    for (const r of rows) {
      lines.push([
        r.policyId, r.policyHolderId, r.dateOfService,
        r.coverageMainCategory, r.coverageSubCategory, r.billedAmount,
        r.policyHolderPays ?? '', r.planPays ?? '', r.ruleUsed ?? '',
        r.individualAccumulatedDeductible ?? '', r.familyAccumulatedDeductible ?? '',
        r.errorCode ?? '', r.errorMessage ?? '', r.processingMessage ?? ''
      ].map(escape).join(','));
    }
    const blob = new Blob([lines.join('\n')], { type: 'text/csv;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'claims-results.csv';
    a.click();
    URL.revokeObjectURL(url);
  }

  clearHistory(): void {
    this.history.set([]);
  }

  ruleChip(rule?: string | null): string {
    if (!rule) { return 'chip-rule-na'; }
    const r = rule.toLowerCase();
    if (r.includes('no charge'))                 { return 'chip-rule-nc'; }
    if (r.includes('%') || r.includes('deduct')) { return 'chip-rule-pct'; }
    if (r.includes('$') || r.includes('flat'))   { return 'chip-rule-flat'; }
    return 'chip-rule-na';
  }

  errorChip(code?: string | null): string {
    if (!code) { return 'chip-error'; }
    return 'chip-' + code.toLowerCase();
  }

  private pushHistory(entry: HistoryEntry): void {
    this.history.update(list => [entry, ...list].slice(0, 50));
  }
}
