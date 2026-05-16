package com.abc.claims.web;

import com.abc.claims.batch.BatchProcessor;
import com.abc.claims.batch.CsvClaimParser;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GuiControllerExportTest {

    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new GuiController(new CsvClaimParser(), mock(BatchProcessor.class)))
            .build();

    @Test
    @DisplayName("GET /process/export.csv → 404 when no session results")
    void export_404_when_no_session() throws Exception {
        mockMvc.perform(get("/process/export.csv"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /process/export.csv → streams CSV with attachment header when session has rows")
    void export_streams_csv() throws Exception {
        HttpSession session = new MockHttpSession();
        session.setAttribute("claimsResultRows", List.<String[]>of(
                new String[]{"H1", "H2"},
                new String[]{"a", "b"}
        ));

        mockMvc.perform(get("/process/export.csv").session((MockHttpSession) session))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("claims-results.csv")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"H1\",\"H2\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"a\",\"b\"")));
    }
}
