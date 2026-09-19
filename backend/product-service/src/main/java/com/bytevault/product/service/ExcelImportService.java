package com.bytevault.product.service;

import com.bytevault.common.exception.BadRequestException;
import com.bytevault.product.dto.ExcelImportConfirmRequest;
import com.bytevault.product.dto.ExcelImportPreviewResponse;
import com.bytevault.product.dto.ExcelImportResult;
import com.bytevault.product.dto.ExcelImportRow;
import com.bytevault.product.entity.Category;
import com.bytevault.product.entity.Product;
import com.bytevault.product.entity.ProductStatus;
import com.bytevault.product.entity.ProductType;
import com.bytevault.product.repository.CategoryRepository;
import com.bytevault.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExcelImportService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    public ExcelImportPreviewResponse previewExcel(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Please upload a valid Excel file.");
        }

        String filename = file.getOriginalFilename();
        if (filename == null || (!filename.endsWith(".xlsx") && !filename.endsWith(".xls"))) {
            throw new BadRequestException("Invalid file format. Only .xlsx and .xls Excel files are supported.");
        }

        List<ExcelImportRow> previewRows = new ArrayList<>();
        Set<String> seenSkusInFile = new HashSet<>();

        try (InputStream is = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(is)) {

            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null || sheet.getLastRowNum() < 1) {
                throw new BadRequestException("Excel sheet is empty or contains no data rows.");
            }

            // Map headers
            Row headerRow = sheet.getRow(0);
            Map<String, Integer> colMap = mapHeaders(headerRow);

            int totalRows = 0;
            int validRows = 0;
            int invalidRows = 0;

            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null || isRowEmpty(row)) {
                    continue;
                }

                totalRows++;
                ExcelImportRow importRow = parseRow(row, r, colMap, seenSkusInFile);
                if (importRow.isValid()) {
                    validRows++;
                } else {
                    invalidRows++;
                }
                previewRows.add(importRow);
            }

            return ExcelImportPreviewResponse.builder()
                    .totalRows(totalRows)
                    .validRows(validRows)
                    .invalidRows(invalidRows)
                    .previewRows(previewRows)
                    .build();

        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.error("[ExcelImportService] Failed to parse Excel file", e);
            throw new BadRequestException("Failed to parse Excel file: " + e.getMessage());
        }
    }

    @Transactional
    public ExcelImportResult confirmImport(ExcelImportConfirmRequest request) {
        if (request == null || request.getRows() == null || request.getRows().isEmpty()) {
            throw new BadRequestException("No rows provided for confirmation import.");
        }

        int importedCount = 0;
        int failedCount = 0;
        List<String> errors = new ArrayList<>();
        List<Product> productsToSave = new ArrayList<>();

        for (ExcelImportRow row : request.getRows()) {
            try {
                // Re-validate row
                if (row.getName() == null || row.getName().trim().isEmpty()) {
                    failedCount++;
                    errors.add("Row " + row.getRowNumber() + ": Product name is required");
                    continue;
                }

                if (row.getPrice() == null || row.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
                    failedCount++;
                    errors.add("Row " + row.getRowNumber() + ": Valid positive price is required");
                    continue;
                }

                if (row.getSku() == null || row.getSku().trim().isEmpty()) {
                    failedCount++;
                    errors.add("Row " + row.getRowNumber() + ": SKU is required");
                    continue;
                }

                if (productRepository.existsBySku(row.getSku().trim())) {
                    failedCount++;
                    errors.add("Row " + row.getRowNumber() + ": Duplicate SKU in database: " + row.getSku());
                    continue;
                }

                ProductType productType;
                try {
                    productType = ProductType.valueOf(row.getProductType().trim().toUpperCase());
                } catch (Exception e) {
                    productType = ProductType.DIGITAL;
                }

                Category category = null;
                if (row.getCategoryId() != null) {
                    category = categoryRepository.findById(row.getCategoryId()).orElse(null);
                } else if (row.getCategoryName() != null && !row.getCategoryName().trim().isEmpty()) {
                    category = categoryRepository.findByName(row.getCategoryName().trim()).orElse(null);
                }

                Product product = Product.builder()
                        .name(row.getName().trim())
                        .description(row.getDescription())
                        .price(row.getPrice())
                        .currency(row.getCurrency() != null && !row.getCurrency().trim().isEmpty() ? row.getCurrency().trim() : "INR")
                        .sku(row.getSku().trim())
                        .physicalSku(row.getSku().trim())
                        .productType(productType)
                        .status(ProductStatus.PUBLISHED)
                        .category(category)
                        .vendorId(row.getVendorId())
                        .weight(row.getWeight())
                        .length(row.getLength())
                        .width(row.getWidth())
                        .height(row.getHeight())
                        .physicalWeight(row.getWeight())
                        .build();

                productsToSave.add(product);
                importedCount++;

            } catch (Exception e) {
                failedCount++;
                errors.add("Row " + row.getRowNumber() + ": Unexpected error - " + e.getMessage());
            }
        }

        if (!productsToSave.isEmpty()) {
            productRepository.saveAll(productsToSave);
            log.info("[ExcelImportService] Successfully imported {} products", productsToSave.size());
        }

        return ExcelImportResult.builder()
                .totalRows(request.getRows().size())
                .importedCount(importedCount)
                .failedCount(failedCount)
                .errors(errors)
                .build();
    }

    private ExcelImportRow parseRow(Row row, int rowNum, Map<String, Integer> colMap, Set<String> seenSkus) {
        List<String> errors = new ArrayList<>();
        boolean valid = true;

        String name = getStringValue(row, colMap.get("name"));
        if (name == null || name.trim().isEmpty()) {
            errors.add("Missing product name");
            valid = false;
        }

        String desc = getStringValue(row, colMap.get("description"));

        BigDecimal price = getBigDecimalValue(row, colMap.get("price"));
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            errors.add("Invalid or missing price");
            valid = false;
        }

        String currency = getStringValue(row, colMap.get("currency"));
        if (currency == null || currency.trim().isEmpty()) {
            currency = "INR";
        }

        String sku = getStringValue(row, colMap.get("sku"));
        if (sku == null || sku.trim().isEmpty()) {
            errors.add("Missing SKU");
            valid = false;
        } else {
            String trimmedSku = sku.trim();
            if (seenSkus.contains(trimmedSku)) {
                errors.add("Duplicate SKU in file: " + trimmedSku);
                valid = false;
            } else if (productRepository.existsBySku(trimmedSku)) {
                errors.add("SKU already exists in database: " + trimmedSku);
                valid = false;
            } else {
                seenSkus.add(trimmedSku);
            }
        }

        String typeStr = getStringValue(row, colMap.get("type"));
        if (typeStr == null || typeStr.trim().isEmpty()) {
            typeStr = "DIGITAL";
        } else {
            try {
                ProductType.valueOf(typeStr.trim().toUpperCase());
            } catch (Exception e) {
                errors.add("Invalid product type '" + typeStr + "'. Must be DIGITAL or PHYSICAL");
                valid = false;
            }
        }

        String categoryName = getStringValue(row, colMap.get("category"));
        Long categoryId = null;
        if (categoryName != null && !categoryName.trim().isEmpty()) {
            Optional<Category> catOpt = categoryRepository.findByName(categoryName.trim());
            if (catOpt.isPresent()) {
                categoryId = catOpt.get().getId();
            }
        }

        String vendorIdStr = getStringValue(row, colMap.get("vendorid"));
        UUID vendorId = null;
        if (vendorIdStr != null && !vendorIdStr.trim().isEmpty()) {
            try {
                vendorId = UUID.fromString(vendorIdStr.trim());
            } catch (Exception e) {
                errors.add("Invalid vendor UUID: " + vendorIdStr);
                valid = false;
            }
        }

        Double weight = getDoubleValue(row, colMap.get("weight"));
        Double length = getDoubleValue(row, colMap.get("length"));
        Double width = getDoubleValue(row, colMap.get("width"));
        Double height = getDoubleValue(row, colMap.get("height"));

        return ExcelImportRow.builder()
                .rowNumber(rowNum)
                .name(name)
                .description(desc)
                .price(price)
                .currency(currency)
                .sku(sku)
                .productType(typeStr)
                .categoryName(categoryName)
                .categoryId(categoryId)
                .vendorId(vendorId)
                .weight(weight)
                .length(length)
                .width(width)
                .height(height)
                .valid(valid)
                .errors(errors)
                .build();
    }

    private Map<String, Integer> mapHeaders(Row headerRow) {
        Map<String, Integer> map = new HashMap<>();
        if (headerRow == null) return map;

        for (int c = 0; c < headerRow.getLastCellNum(); c++) {
            Cell cell = headerRow.getCell(c);
            if (cell != null) {
                String val = cell.getStringCellValue().trim().toLowerCase().replaceAll("[ _-]", "");
                if (val.contains("name")) map.put("name", c);
                else if (val.contains("desc")) map.put("description", c);
                else if (val.contains("price")) map.put("price", c);
                else if (val.contains("curr")) map.put("currency", c);
                else if (val.contains("sku")) map.put("sku", c);
                else if (val.contains("type")) map.put("type", c);
                else if (val.contains("cat")) map.put("category", c);
                else if (val.contains("vendor")) map.put("vendorid", c);
                else if (val.contains("weight")) map.put("weight", c);
                else if (val.contains("len")) map.put("length", c);
                else if (val.contains("width")) map.put("width", c);
                else if (val.contains("height")) map.put("height", c);
            }
        }
        return map;
    }

    private boolean isRowEmpty(Row row) {
        for (int c = row.getFirstCellNum(); c < row.getLastCellNum(); c++) {
            Cell cell = row.getCell(c);
            if (cell != null && cell.getCellType() != CellType.BLANK) {
                return false;
            }
        }
        return true;
    }

    private String getStringValue(Row row, Integer colIdx) {
        if (colIdx == null) return null;
        Cell cell = row.getCell(colIdx);
        if (cell == null) return null;
        if (cell.getCellType() == CellType.STRING) {
            return cell.getStringCellValue();
        } else if (cell.getCellType() == CellType.NUMERIC) {
            return String.valueOf((long) cell.getNumericCellValue());
        }
        return null;
    }

    private BigDecimal getBigDecimalValue(Row row, Integer colIdx) {
        if (colIdx == null) return null;
        Cell cell = row.getCell(colIdx);
        if (cell == null) return null;
        if (cell.getCellType() == CellType.NUMERIC) {
            return BigDecimal.valueOf(cell.getNumericCellValue());
        } else if (cell.getCellType() == CellType.STRING) {
            try {
                return new BigDecimal(cell.getStringCellValue().trim());
            } catch (Exception ignored) {}
        }
        return null;
    }

    private Double getDoubleValue(Row row, Integer colIdx) {
        if (colIdx == null) return null;
        Cell cell = row.getCell(colIdx);
        if (cell == null) return null;
        if (cell.getCellType() == CellType.NUMERIC) {
            return cell.getNumericCellValue();
        } else if (cell.getCellType() == CellType.STRING) {
            try {
                return Double.parseDouble(cell.getStringCellValue().trim());
            } catch (Exception ignored) {}
        }
        return null;
    }
}
