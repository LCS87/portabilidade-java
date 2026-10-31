package com.portabilidade.infrastructure.file;

import com.portabilidade.domain.exception.DataLoadingException;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Lê arquivos Excel (.xlsx) de entrada usando Apache POI.
 * Lê a primeira planilha (aba 0).
 * Normaliza os headers para lowercase sem espaços extras.
 * Garante que exista uma coluna "telefone" (tenta mapear variantes comuns).
 */
public class ExcelInputReader implements InputFileReader {

    private static final Logger log = LoggerFactory.getLogger(ExcelInputReader.class);

    private static final List<String> PHONE_COLUMN_ALIASES = List.of(
            "telefone", "fone", "celular", "numero", "número", "tel",
            "phone", "mobile", "msisdn", "ddd_numero", "nr_telefone"
    );

    @Override
    public boolean supports(String filePath) {
        return filePath != null && filePath.toLowerCase().endsWith(".xlsx");
    }

    @Override
    public List<Map<String, String>> read(String filePath) {
        Path path = Path.of(filePath);
        if (!Files.exists(path)) {
            throw new DataLoadingException("Arquivo não encontrado: " + filePath);
        }

        try (FileInputStream fis = new FileInputStream(path.toFile());
             Workbook workbook = new XSSFWorkbook(fis)) {

            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                log.warn("Planilha vazia: {}", filePath);
                return Collections.emptyList();
            }

            Iterator<Row> rowIterator = sheet.iterator();
            if (!rowIterator.hasNext()) return Collections.emptyList();

            // Primeira linha = headers
            Row headerRow = rowIterator.next();
            String[] headers = extractHeaders(headerRow);

            List<Map<String, String>> rows = new ArrayList<>();
            while (rowIterator.hasNext()) {
                Row row = rowIterator.next();
                if (isRowEmpty(row)) continue;
                Map<String, String> rowMap = new LinkedHashMap<>();
                for (int i = 0; i < headers.length; i++) {
                    Cell cell = row.getCell(i, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                    rowMap.put(headers[i], cellToString(cell));
                }
                rows.add(rowMap);
            }

            log.info("Excel lido: {} linhas de '{}'", rows.size(), path.getFileName());
            return rows;

        } catch (DataLoadingException e) {
            throw e;
        } catch (Exception e) {
            throw new DataLoadingException("Erro ao ler Excel: " + filePath, e);
        }
    }

    private String[] extractHeaders(Row headerRow) {
        int lastCol = headerRow.getLastCellNum();
        String[] headers = new String[lastCol];
        for (int i = 0; i < lastCol; i++) {
            Cell cell = headerRow.getCell(i, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
            String h = (cell != null) ? cell.getStringCellValue().trim().toLowerCase()
                    .replaceAll("[\"']", "").replaceAll("\\s+", "_") : "col_" + i;

            // Mapeia aliases para "telefone"
            for (String alias : PHONE_COLUMN_ALIASES) {
                if (h.equals(alias) || h.contains(alias)) {
                    h = "telefone";
                    break;
                }
            }
            headers[i] = h.isEmpty() ? "col_" + i : h;
        }
        return headers;
    }

    private String cellToString(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING  -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                // Evita notação científica para números grandes (telefones)
                double val = cell.getNumericCellValue();
                if (val == Math.floor(val) && !Double.isInfinite(val)) {
                    yield String.valueOf((long) val);
                }
                yield String.valueOf(val);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                try {
                    yield String.valueOf((long) cell.getNumericCellValue());
                } catch (Exception e) {
                    yield cell.getStringCellValue();
                }
            }
            default -> "";
        };
    }

    private boolean isRowEmpty(Row row) {
        if (row == null) return true;
        for (int i = row.getFirstCellNum(); i < row.getLastCellNum(); i++) {
            Cell cell = row.getCell(i);
            if (cell != null && cell.getCellType() != CellType.BLANK) {
                return false;
            }
        }
        return true;
    }
}
