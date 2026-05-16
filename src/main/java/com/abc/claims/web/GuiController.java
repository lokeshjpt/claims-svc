package com.abc.claims.web;

import com.abc.claims.batch.BatchProcessor;
import com.abc.claims.batch.CsvClaimParser;
import com.abc.claims.model.ClaimRequest;
import com.opencsv.CSVWriter;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Controller
public class GuiController {

    private static final String SESSION_KEY = "claimsResultRows";

    private final CsvClaimParser csvClaimParser;
    private final BatchProcessor batchProcessor;

    public GuiController(CsvClaimParser csvClaimParser, BatchProcessor batchProcessor) {
        this.csvClaimParser = csvClaimParser;
        this.batchProcessor = batchProcessor;
    }

    @GetMapping("/")
    public String index() {
        return "index";
    }

    @PostMapping("/process")
    public String processCsv(@RequestParam("file") MultipartFile file, Model model, HttpSession session) {
        if (file.isEmpty()) {
            model.addAttribute("error", "Please upload a CSV file.");
            return "index";
        }
        List<ClaimRequest> requests;
        try {
            requests = csvClaimParser.parseStream(file.getInputStream());
        } catch (Exception exception) {
            model.addAttribute("error", "Failed to parse CSV: " + exception.getMessage());
            return "index";
        }

        List<String[]> rows = batchProcessor.processRequests(requests, com.abc.claims.audit.ClaimAuditLogger.Channel.GUI);
        session.setAttribute(SESSION_KEY, rows);

        model.addAttribute("header", rows.isEmpty() ? new String[0] : rows.get(0));
        model.addAttribute("rows", rows.size() > 1 ? rows.subList(1, rows.size()) : List.of());
        model.addAttribute("fileName", file.getOriginalFilename());
        model.addAttribute("totalRows", rows.size() - 1);
        return "results";
    }

    @GetMapping(value = "/process/export.csv", produces = "text/csv")
    public void exportCsv(HttpSession session, HttpServletResponse response) throws IOException {
        @SuppressWarnings("unchecked")
        List<String[]> rows = (List<String[]>) session.getAttribute(SESSION_KEY);
        if (rows == null || rows.isEmpty()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND,
                    "No results available. Please upload a CSV first.");
            return;
        }
        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"claims-results.csv\"");
        try (CSVWriter writer = new CSVWriter(new OutputStreamWriter(response.getOutputStream(), StandardCharsets.UTF_8))) {
            writer.writeAll(rows);
        }
    }
}
