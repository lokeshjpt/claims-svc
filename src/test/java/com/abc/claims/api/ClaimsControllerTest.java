package com.abc.claims.api;

import com.abc.claims.engine.ClaimsEngine;
import com.abc.claims.model.ClaimRequest;
import com.abc.claims.model.ClaimResult;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ClaimsControllerTest {

    private ClaimsEngine claimsEngine;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        claimsEngine = mock(ClaimsEngine.class);
        MappingJackson2HttpMessageConverter json = new MappingJackson2HttpMessageConverter();
        json.getObjectMapper().registerModule(new JavaTimeModule());
        json.getObjectMapper().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mockMvc = MockMvcBuilders.standaloneSetup(new ClaimsController(claimsEngine,
                        new com.abc.claims.audit.ClaimAuditLogger(new io.micrometer.core.instrument.simple.SimpleMeterRegistry()),
                        new com.abc.claims.batch.CsvClaimParser()))
                .setMessageConverters(json)
                .setControllerAdvice(new ValidationAdvice(), new IllegalArgumentAdvice())
                .build();
    }

    @Test
    @DisplayName("POST /api/v1/claims returns the processed claim payload")
    void processes_valid_claim_request() throws Exception {
        when(claimsEngine.processForApi(any(ClaimRequest.class))).thenReturn(new ClaimResult(
                "100001", "1000011", LocalDate.of(2016, 5, 10),
                "Inpatient Hospital Care", "ROOM AND BOARD",
                new BigDecimal("1000"), new BigDecimal("600"), new BigDecimal("400"),
                "40% AFTER DEDUCTIBLE", new BigDecimal("1000"), new BigDecimal("1500"),
                null, null, "OK"
        ));

        mockMvc.perform(post("/api/v1/claims")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "policyId": "100001",
                                  "policyHolderId": "1000011",
                                  "dateOfService": "2016-05-10",
                                  "coverageMainCategory": "Inpatient Hospital Care",
                                  "coverageSubCategory": "ROOM AND BOARD",
                                  "billedAmount": 1000,
                                  "individualAccumulatedDeductible": 500,
                                  "familyAccumulatedDeductible": 750
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ruleUsed").value("40% AFTER DEDUCTIBLE"))
                .andExpect(jsonPath("$.policyHolderPays").value(600))
                .andExpect(jsonPath("$.planPays").value(400));
    }

    @Test
    @DisplayName("POST /api/v1/claims returns 200 with E0005 when payload is malformed")
    void malformed_request_returns_e0005() throws Exception {
        when(claimsEngine.processForApi(any(ClaimRequest.class))).thenReturn(new ClaimResult(
                "", "", null, "", "", null,
                null, null, null, null, null,
                "E0005", "Missing or malformed claim data", null
        ));

        mockMvc.perform(post("/api/v1/claims")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "policyId": "",
                                  "policyHolderId": "",
                                  "billedAmount": -10
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errorCode").value("E0005"))
                .andExpect(jsonPath("$.errorMessage").value("Missing or malformed claim data"));
    }

    @Test
    @DisplayName("POST /api/v1/claims/batch returns JSON results for every CSV row")
    void batch_endpoint_returns_json_per_row() throws Exception {
        when(claimsEngine.processForBatchOrGui(any(ClaimRequest.class))).thenReturn(new ClaimResult(
                "100001", "1000011", LocalDate.of(2016, 5, 8),
                "Inpatient Hospital Care", "ROOM AND BOARD",
                new BigDecimal("1000"), new BigDecimal("600"), new BigDecimal("400"),
                "40% AFTER DEDUCTIBLE", new BigDecimal("6000"), new BigDecimal("6000"),
                null, null, "OK"
        ));
        String csv = "PolicyId,Policy holder Id,Date of service,Coverage Main Category,Coverage Sub Category,Billed Amount\n" +
                     "100001,1000011,2016-05-08,Inpatient Hospital Care,ROOM AND BOARD,1000\n";
        MockMultipartFile file = new MockMultipartFile("file", "claims.csv", "text/csv", csv.getBytes());

        mockMvc.perform(multipart("/api/v1/claims/batch").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ruleUsed").value("40% AFTER DEDUCTIBLE"))
                .andExpect(jsonPath("$[0].planPays").value(400));
    }

    @Test
    @DisplayName("POST /api/v1/claims/batch rejects empty file with 400")
    void batch_endpoint_rejects_empty_file() throws Exception {
        MockMultipartFile empty = new MockMultipartFile("file", "empty.csv", "text/csv", new byte[0]);
        mockMvc.perform(multipart("/api/v1/claims/batch").file(empty))
                .andExpect(status().isBadRequest());
    }

    @ControllerAdvice
    static class IllegalArgumentAdvice {
        @ResponseStatus(HttpStatus.BAD_REQUEST)
        @ExceptionHandler(IllegalArgumentException.class)
        public void handle(IllegalArgumentException ignored) {
        }
    }

    @ControllerAdvice
    static class ValidationAdvice {
        @ResponseStatus(HttpStatus.BAD_REQUEST)
        @ExceptionHandler(MethodArgumentNotValidException.class)
        public void handle(MethodArgumentNotValidException ignored) {
        }
    }
}
