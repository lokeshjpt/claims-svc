package com.abc.claims.web;

import com.abc.claims.batch.BatchProcessor;
import com.abc.claims.batch.CsvClaimParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.view.InternalResourceViewResolver;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

class GuiControllerTest {

    private BatchProcessor batchProcessor;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        batchProcessor = mock(BatchProcessor.class);
        CsvClaimParser realParser = new CsvClaimParser();
        InternalResourceViewResolver viewResolver = new InternalResourceViewResolver();
        viewResolver.setPrefix("/templates/");
        viewResolver.setSuffix(".html");
        mockMvc = MockMvcBuilders
                .standaloneSetup(new GuiController(realParser, batchProcessor))
                .setViewResolvers(viewResolver)
                .build();
    }

    @Test
    @DisplayName("GET / renders the upload form")
    void index_renders_upload_form() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"));
    }

    @Test
    @DisplayName("POST /process with empty file shows an inline error")
    void empty_upload_shows_error() throws Exception {
        MockMultipartFile empty = new MockMultipartFile("file", "empty.csv", "text/csv", new byte[0]);

        mockMvc.perform(multipart("/process").file(empty))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(model().attributeExists("error"));
    }

    @Test
    @DisplayName("POST /process with a CSV invokes the batch processor and renders results")
    void uploads_csv_and_renders_results() throws Exception {
        String csv = """
                PolicyId,Policy holder Id,Date of service,Coverage Main Category,Coverage Sub Category,Billed Amount
                100001,1000011,2016-05-10,Inpatient Hospital Care,ROOM AND BOARD,1000
                """;
        MockMultipartFile upload = new MockMultipartFile("file", "claims.csv", "text/csv", csv.getBytes());

        String[] row = {"100001", "1000011", "2016-05-10", "Inpatient Hospital Care",
                "ROOM AND BOARD", "1000", "600", "400", "40% AFTER DEDUCTIBLE",
                "1000", "1500", "", "", "OK"};
        when(batchProcessor.processRequests(anyList(), any(com.abc.claims.audit.ClaimAuditLogger.Channel.class))).thenReturn(List.<String[]>of(row));

        mockMvc.perform(multipart("/process").file(upload))
                .andExpect(status().isOk())
                .andExpect(view().name("results"))
                .andExpect(model().attributeExists("rows"));

        verify(batchProcessor).processRequests(anyList(), any(com.abc.claims.audit.ClaimAuditLogger.Channel.class));
    }
}
