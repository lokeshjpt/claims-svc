export interface ClaimRequest {
  policyId: string;
  policyHolderId: string;
  dateOfService: string | null;
  coverageMainCategory: string;
  coverageSubCategory: string;
  billedAmount: number | null;
  individualAccumulatedDeductible?: number | null;
  familyAccumulatedDeductible?: number | null;
}

export interface ClaimResponse {
  policyId: string;
  policyHolderId: string;
  dateOfService: string;
  coverageMainCategory: string;
  coverageSubCategory: string;
  billedAmount: number;
  policyHolderPays: number | null;
  planPays: number | null;
  ruleUsed: string | null;
  individualAccumulatedDeductible: number | null;
  familyAccumulatedDeductible: number | null;
  errorCode: string | null;
  errorMessage: string | null;
  processingMessage: string | null;
}
