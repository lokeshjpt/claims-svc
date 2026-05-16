import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { ClaimsService } from './claims.service';
import { ClaimRequest, ClaimResponse } from '../models/claim';

describe('ClaimsService', () => {
  let service: ClaimsService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(ClaimsService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('POSTs a claim request to /api/v1/claims', () => {
    const req: ClaimRequest = {
      policyId: '100001', policyHolderId: '1000011', dateOfService: '2016-05-08',
      coverageMainCategory: 'Inpatient Hospital Care', coverageSubCategory: 'ROOM AND BOARD',
      billedAmount: 1000
    };
    const expected: Partial<ClaimResponse> = {
      policyId: '100001', planPays: 400, policyHolderPays: 600, ruleUsed: '40% AFTER DEDUCTIBLE'
    };

    service.process(req).subscribe(r => {
      expect(r.planPays).toBe(400);
      expect(r.ruleUsed).toBe('40% AFTER DEDUCTIBLE');
    });

    const r = http.expectOne('/api/v1/claims');
    expect(r.request.method).toBe('POST');
    expect(r.request.body).toEqual(req);
    r.flush(expected);
  });

  it('uploads a CSV via multipart form data', () => {
    const file = new File(['hello'], 'sample.csv', { type: 'text/csv' });
    service.uploadCsv(file).subscribe(html => {
      expect(html).toContain('<table');
    });
    const r = http.expectOne('/process');
    expect(r.request.method).toBe('POST');
    expect(r.request.body instanceof FormData).toBeTrue();
    r.flush('<table>ok</table>');
  });
});
