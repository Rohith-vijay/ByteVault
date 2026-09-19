package com.bytevault.product.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExcelImportPreviewResponse {
    private int totalRows;
    private int validRows;
    private int invalidRows;
    
    @Builder.Default
    private List<ExcelImportRow> previewRows = new ArrayList<>();
}
