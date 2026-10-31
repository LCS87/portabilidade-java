package com.portabilidade.infrastructure.file;

import com.portabilidade.domain.exception.DataLoadingException;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.model.SharedStrings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Lê arquivos Excel (.xlsx) de entrada usando Apache POI.
 *
 * Fase 1 (PLANO_MELHORIAS): {@link #stream(String)} usa o modo event/SAX do
 * POI ({@link XSSFReader}) — as linhas são materializadas uma a uma, sem
 * carregar o workbook inteiro no heap (suporta arquivos maiores que a memória).
 * O parse SAX roda em uma virtual thread dedicada que publica cada linha em
 * uma fila sincronizada e pausa quando o consumidor não pede a próxima.
 *
 * Lê a primeira planilha (aba 0). Normaliza headers para lowercase sem
 * espaços extras e mapeia aliases comuns para a coluna "telefone".
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
        // Compatibilidade: materializa o stream em lista.
        List<Map<String, String>> rows = new ArrayList<>();
        Iterator<Map<String, String>> it = stream(filePath);
        while (it.hasNext()) rows.add(it.next());
        log.info("Excel lido: {} linhas de '{}'", rows.size(), Path.of(filePath).getFileName());
        return rows;
    }

    @Override
    public Iterator<Map<String, String>> stream(String filePath) {
        Path path = Path.of(filePath);
        if (!Files.exists(path)) {
            throw new DataLoadingException("Arquivo não encontrado: " + filePath);
        }
        try {
            OPCPackage pkg = OPCPackage.open(path.toFile());
            XSSFReader reader = new XSSFReader(pkg);
            SharedStrings sst = reader.getSharedStringsTable();

            XSSFReader.SheetIterator sheets = (XSSFReader.SheetIterator) reader.getSheetsData();
            if (!sheets.hasNext()) {
                pkg.close();
                return Collections.emptyIterator();
            }
            InputStream sheetStream = sheets.next();
            return new StreamingSheet(sst, pkg, sheetStream, path);
        } catch (DataLoadingException e) {
            throw e;
        } catch (Exception e) {
            throw new DataLoadingException("Erro ao ler Excel: " + filePath, e);
        }
    }

    /**
     * Iterator lazy sobre o sheet: parser SAX roda em virtual thread separada,
     * comunicando-se por mailbox sincronizado (backpressure natural — o parser
     * bloqueia quando o consumidor não pede a próxima linha).
     */
    private static final class StreamingSheet implements Iterator<Map<String, String>> {
        private final Queue<Map<String, String>> mailbox = new ArrayDeque<>();
        private final Object lock = new Object();
        private volatile Throwable error;
        private volatile boolean eof;
        private Map<String, String> nextRow;

        StreamingSheet(SharedStrings sst, OPCPackage pkg, InputStream sheetStream, Path path) {
            Thread.ofVirtual().name("excel-stream-" + path.getFileName()).start(() -> {
                try {
                    SAXParserFactory factory = SAXParserFactory.newInstance();
                    factory.setNamespaceAware(true);
                    SAXParser parser = factory.newSAXParser();
                    parser.getXMLReader().setContentHandler(new RowCollector(sst));
                    parser.parse(new InputSource(sheetStream));
                } catch (Exception e) {
                    synchronized (lock) { error = e; lock.notifyAll(); }
                } finally {
                    eof = true;
                    synchronized (lock) { lock.notifyAll(); }
                    closeQuietly(sheetStream, pkg);
                }
            });
        }

        /** Handler SAX: monta linha por linha e publica no mailbox com backpressure. */
        private final class RowCollector extends DefaultHandler {
            private final SharedStrings sst;
            private StringBuilder cellValue;
            private boolean inValue;
            private String cellType;
            private int colIndex;
            private List<String> currentCells;
            private String[] headers;

            RowCollector(SharedStrings sst) { this.sst = sst; }

            @Override public void startElement(String uri, String localName, String qName, Attributes attrs) {
                String name = localName.isEmpty() ? qName : localName;
                switch (name) {
                    case "row" -> currentCells = new ArrayList<>();
                    case "c" -> {
                        cellType = attrs.getValue("t");
                        String ref = attrs.getValue("r");
                        colIndex = ref != null ? columnIndexOf(ref) : colIndex + 1;
                        cellValue = new StringBuilder();
                        inValue = false;
                    }
                    case "v", "t" -> inValue = true;
                    default -> { }
                }
            }

            @Override public void characters(char[] ch, int start, int length) {
                if (inValue) cellValue.append(ch, start, length);
            }

            @Override public void endElement(String uri, String localName, String qName) {
                String name = localName.isEmpty() ? qName : localName;
                switch (name) {
                    case "v", "t" -> inValue = false;
                    case "c" -> {
                        String val = cellValue == null ? "" : cellValue.toString();
                        if ("s".equals(cellType)) { // shared string
                            try { val = sst.getItemAt(Integer.parseInt(val.trim())).getString(); }
                            catch (NumberFormatException | IndexOutOfBoundsException ignored) { }
                        } else if (cellType == null || "n".equals(cellType)) {
                            val = numericToPlainString(val);
                        }
                        while (currentCells.size() < colIndex) currentCells.add("");
                        currentCells.add(val.trim());
                    }
                    case "row" -> publishRow();
                    default -> { }
                }
            }

            private void publishRow() {
                if (headers == null) {
                    headers = normalizeHeaders(currentCells);
                    return; // primeira linha = header
                }
                if (currentCells.stream().allMatch(String::isBlank)) return;
                Map<String, String> row = new LinkedHashMap<>();
                for (int i = 0; i < headers.length; i++) {
                    row.put(headers[i], i < currentCells.size() ? currentCells.get(i) : "");
                }
                // backpressure: aguarda o consumidor consumir antes de enfileirar outra
                synchronized (lock) {
                    while (mailbox.size() >= 2) {
                        try { lock.wait(); } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt(); return;
                        }
                    }
                    mailbox.add(row);
                    lock.notifyAll();
                }
            }
        }

        @Override
        public boolean hasNext() {
            if (nextRow != null) return true;
            nextRow = pull();
            return nextRow != null;
        }

        @Override
        public Map<String, String> next() {
            if (!hasNext()) throw new NoSuchElementException();
            Map<String, String> cur = nextRow;
            nextRow = null;
            return cur;
        }

        private Map<String, String> pull() {
            synchronized (lock) {
                while (true) {
                    if (!mailbox.isEmpty()) {
                        Map<String, String> r = mailbox.poll();
                        lock.notifyAll(); // libera o parser para continuar
                        return r;
                    }
                    if (error != null) {
                        throw new DataLoadingException("Erro no streaming do Excel: "
                                + error.getMessage(), error);
                    }
                    if (eof) return null;
                    try { lock.wait(); } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return null;
                    }
                }
            }
        }
    }

    private static void closeQuietly(InputStream in, OPCPackage pkg) {
        try { in.close(); } catch (Exception ignored) { }
        try { pkg.close(); } catch (Exception ignored) { }
    }

    /** Converte serial numérico Excel para string sem notação científica (telefones). */
    private static String numericToPlainString(String raw) {
        if (raw == null || raw.isBlank()) return "";
        try {
            double d = Double.parseDouble(raw);
            if (d == Math.floor(d) && !Double.isInfinite(d) && Math.abs(d) < 1e15) {
                return String.valueOf((long) d);
            }
        } catch (NumberFormatException ignored) { }
        return raw;
    }

    /** "B7" → 1 (zero-based). */
    private static int columnIndexOf(String ref) {
        int col = 0;
        for (char c : ref.toCharArray()) {
            if (Character.isLetter(c)) col = col * 26 + (Character.toUpperCase(c) - 'A' + 1);
            else break;
        }
        return col - 1;
    }

    private String[] normalizeHeaders(List<String> rawCells) {
        String[] result = new String[rawCells.size()];
        for (int i = 0; i < rawCells.size(); i++) {
            String h = rawCells.get(i).trim().toLowerCase()
                    .replaceAll("[\"']", "").replaceAll("\\s+", "_");
            for (String alias : PHONE_COLUMN_ALIASES) {
                if (h.equals(alias) || h.contains(alias)) { h = "telefone"; break; }
            }
            result[i] = h.isEmpty() ? "col_" + i : h;
        }
        return result;
    }
}
