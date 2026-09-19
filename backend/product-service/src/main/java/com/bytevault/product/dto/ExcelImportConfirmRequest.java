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
public class ExcelImportConfirmRequest {
    @Builder.Default
    private List<ExcelImportRow> rows = new ArrayList<>();
}
