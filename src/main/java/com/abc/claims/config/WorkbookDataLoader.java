package com.abc.claims.config;

import com.abc.claims.dao.CoverageDao;
import com.abc.claims.dao.PlanDao;
import com.abc.claims.dao.PolicyDao;
import com.abc.claims.engine.deductible.DeductibleService;
import com.abc.claims.model.Plan;
import com.abc.claims.model.PolicyHolder;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.FileInputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Bootstraps in-memory DAOs from the Excel reference workbook.
 *
 * <p>Active only under the {@code default} or {@code inmemory} profile. When the
 * Oracle / Mongo profiles are active, the DAOs are backed by a real database and
 * this loader does not run — data is expected to already exist in those stores.</p>
 */
@Component
@Profile({"default", "inmemory"})
public class WorkbookDataLoader {

    private static final LocalDate EXCEL_EPOCH = LocalDate.of(1899, 12, 30);

    private final DataFormatter dataFormatter = new DataFormatter(Locale.US);
    private final PlanDao planDao;
    private final PolicyDao policyDao;
    private final CoverageDao coverageDao;
    private final DeductibleService deductibleService;
    private final String workbookPath;

    public WorkbookDataLoader(
            PlanDao planDao,
            PolicyDao policyDao,
            CoverageDao coverageDao,
            DeductibleService deductibleService,
            @Value("${claims.data.xlsx-path:./SamplePlanAndTransactionData_1.xlsx}") String workbookPath
    ) {
        this.planDao = planDao;
        this.policyDao = policyDao;
        this.coverageDao = coverageDao;
        this.deductibleService = deductibleService;
        this.workbookPath = workbookPath;
    }

    @PostConstruct
    public void load() {
        Path path = Path.of(workbookPath);
        if (!Files.exists(path)) {
            throw new IllegalStateException("Workbook not found: " + path.toAbsolutePath());
        }
        try (FileInputStream in = new FileInputStream(path.toFile()); Workbook workbook = new XSSFWorkbook(in)) {
            loadPlans(workbook.getSheet("PlanDescriptions"));
            loadCoverage(workbook.getSheet("PlanCoverage"));
            List<PolicyHolder> holders = loadPolicyHolders(workbook.getSheet("PolicyData"));
            holders.forEach(policyDao::save);
            deductibleService.initialize(policyDao.findAll());
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to load workbook data from " + workbookPath, exception);
        }
    }

    private void loadPlans(Sheet sheet) {
        if (sheet == null) return;
        for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null || text(row, 0).isBlank()) continue;
            planDao.save(new Plan(text(row, 0), text(row, 1), decimal(row, 4), decimal(row, 5)));
        }
    }

    private void loadCoverage(Sheet sheet) {
        if (sheet == null) return;
        int headerRowIndex = findHeaderRow(sheet);
        Row header = sheet.getRow(headerRowIndex);
        String p001 = extractPlanId(text(header, 2));
        String p002 = extractPlanId(text(header, 3));
        String p003 = extractPlanId(text(header, 4));
        String currentMainCategory = "";
        for (int rowIndex = headerRowIndex + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) continue;
            String maybeMain = text(row, 0);
            if (!maybeMain.isBlank()) currentMainCategory = maybeMain;
            String subCategory = text(row, 1);
            if (subCategory.isBlank()) continue;
            putRule(p001, currentMainCategory, subCategory, text(row, 2));
            putRule(p002, currentMainCategory, subCategory, text(row, 3));
            putRule(p003, currentMainCategory, subCategory, text(row, 4));
        }
    }

    private int findHeaderRow(Sheet sheet) {
        for (int rowIndex = 0; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) continue;
            String c0 = text(row, 0);
            if (c0.equalsIgnoreCase("Main Category")) {
                return rowIndex;
            }
        }
        return 0;
    }

    private void putRule(String planId, String main, String sub, String raw) {
        if (planId.isBlank() || raw.isBlank()) return;
        coverageDao.save(planId, main, sub, raw);
        if (sub.contains(",")) {
            for (String token : sub.split(",")) {
                coverageDao.save(planId, main, token.trim(), raw);
            }
        }
    }

    private List<PolicyHolder> loadPolicyHolders(Sheet sheet) {
        List<PolicyHolder> holders = new ArrayList<>();
        if (sheet == null) return holders;
        for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) continue;
            String policyId = text(row, 0);
            String holderId = text(row, 1);
            if (policyId.isBlank() || holderId.isBlank() || !policyId.matches("\\d+") || !holderId.matches("\\d+")) {
                continue;
            }
            holders.add(new PolicyHolder(
                    policyId, holderId, text(row, 2), text(row, 3), text(row, 4),
                    excelDate(row, 5), excelDate(row, 6),
                    decimal(row, 7), decimal(row, 8)
            ));
        }
        return holders;
    }

    private String text(Row row, int index) {
        Cell cell = row == null ? null : row.getCell(index);
        return cell == null ? "" : dataFormatter.formatCellValue(cell).trim();
    }

    private BigDecimal decimal(Row row, int index) {
        String value = text(row, index).replace(",", "").replace("$", "").trim();
        return value.isBlank() ? BigDecimal.ZERO : new BigDecimal(value);
    }

    private LocalDate excelDate(Row row, int index) {
        Cell cell = row == null ? null : row.getCell(index);
        if (cell == null) {
            return null;
        }
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue().toLocalDate();
        }
        String value = dataFormatter.formatCellValue(cell).trim();
        if (value.isBlank()) {
            return null;
        }
        if (value.matches("^\\d+(?:\\.\\d+)?$")) {
            return EXCEL_EPOCH.plusDays((long) Double.parseDouble(value));
        }
        for (String pattern : new String[]{"M/d/yyyy", "MM/dd/yyyy", "yyyy-MM-dd"}) {
            try {
                return LocalDate.parse(value, java.time.format.DateTimeFormatter.ofPattern(pattern));
            } catch (java.time.format.DateTimeParseException ignored) {
                // try next
            }
        }
        return null;
    }

    private String extractPlanId(String header) {
        if (header == null || header.isBlank()) return "";
        String[] tokens = header.trim().split("\\s+");
        return tokens[tokens.length - 1];
    }
}
