package com.abc.claims.batch;

import com.abc.claims.model.ClaimRequest;
import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;
import com.opencsv.exceptions.CsvValidationException;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class CsvClaimParser {
    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("M/d/yyyy"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),
            DateTimeFormatter.ofPattern("yyyy/M/d")
    );

    public List<ClaimRequest> parseFile(String inputPath) {
        try (CSVReader reader = new CSVReader(new BufferedReader(new FileReader(inputPath)))) {
            return parse(reader);
        } catch (IOException | CsvValidationException exception) {
            throw new IllegalStateException("Failed to parse input CSV: " + inputPath, exception);
        }
    }

    public List<ClaimRequest> parseStream(InputStream stream) {
        try (CSVReader reader = new CSVReader(new BufferedReader(new InputStreamReader(stream)))) {
            return parse(reader);
        } catch (IOException | CsvValidationException exception) {
            throw new IllegalStateException("Failed to parse uploaded CSV", exception);
        }
    }

    public void writeResults(String outputPath, List<String[]> rows) {
        try (CSVWriter writer = new CSVWriter(new FileWriter(outputPath))) {
            writer.writeAll(rows);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to write output CSV: " + outputPath, exception);
        }
    }

    private List<ClaimRequest> parse(CSVReader reader) throws IOException, CsvValidationException {
        String[] header = reader.readNext();
        if (header == null) {
            return List.of();
        }
        Map<String, Integer> index = buildIndex(header);
        List<ClaimRequest> requests = new ArrayList<>();
        String[] row;
        while ((row = reader.readNext()) != null) {
            requests.add(new ClaimRequest(
                    value(row, index, "POLICYID"),
                    value(row, index, "POLICYHOLDERID"),
                    parseDate(value(row, index, "DATEOFSERVICE")),
                    value(row, index, "COVERAGEMAINCATEGORY"),
                    value(row, index, "COVERAGESUBCATEGORY"),
                    parseMoney(value(row, index, "BILLEDAMOUNT")),
                    null,
                    null
            ));
        }
        return requests;
    }

    private Map<String, Integer> buildIndex(String[] header) {
        Map<String, Integer> map = new HashMap<>();
        for (int i = 0; i < header.length; i++) {
            map.put(normalizeHeader(header[i]), i);
        }
        return map;
    }

    private String value(String[] row, Map<String, Integer> index, String key) {
        Integer i = index.get(key);
        if (i == null || i >= row.length) {
            return "";
        }
        return row[i] == null ? "" : row[i].trim();
    }

    private String normalizeHeader(String value) {
        return value == null ? "" : value.replace(" ", "").toUpperCase();
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        for (DateTimeFormatter format : DATE_FORMATS) {
            try {
                return LocalDate.parse(trimmed, format);
            } catch (DateTimeParseException ignored) {
                // try next format
            }
        }
        // Lenient: return null so the engine emits E0005 (malformed) for this row
        // rather than aborting the whole batch.
        return null;
    }

    private BigDecimal parseMoney(String value) {
        if (value == null || value.isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException ignored) {
            // Lenient: return null so the engine flags this row as E0005.
            return null;
        }
    }
}
