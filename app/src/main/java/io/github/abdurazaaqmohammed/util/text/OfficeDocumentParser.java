package io.github.abdurazaaqmohammed.util.text;

import org.apache.commons.io.FilenameUtils;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Extracts structured, mobile-readable text representations (headings, lists,
 * links, speaker notes, and aligned Markdown tables) from Microsoft Office,
 * OpenDocument, and RTF files so {@code TextEditorActivity} can display them
 * cleanly with dedicated syntax contexts.
 */
public final class OfficeDocumentParser {

    public enum Kind {
        WORD,
        EXCEL,
        POWERPOINT,
        RTF,
        NONE
    }

    private static final int MAX_STREAM_READ_BYTES = 24 * 1024 * 1024;
    private static final int MAX_SHEET_ROWS = 4000;
    private static final int MAX_SHEET_COLS = 64;
    private static final int MAX_OUTPUT_CHARS = 1_500_000;
    private static final Pattern SLIDE_NUM_PATTERN = Pattern.compile("slide(\\d+)\\.xml$", Pattern.CASE_INSENSITIVE);
    private static final Pattern SHEET_NUM_PATTERN = Pattern.compile("sheet(\\d+)\\.xml$", Pattern.CASE_INSENSITIVE);
    private static final Pattern CELL_REF_PATTERN = Pattern.compile("^([A-Za-z]+)\\d+$");

    private OfficeDocumentParser() {}

    public static String scopeForKind(Kind kind) {
        if (kind == null) return null;
        return switch (kind) {
            case WORD, RTF -> "text.office.document";
            case EXCEL -> "text.office.spreadsheet";
            case POWERPOINT -> "text.office.presentation";
            case NONE -> null;
        };
    }

    public static boolean isOfficeFileName(String name) {
        return kindFromName(name) != Kind.NONE;
    }

    public static Kind kindFromName(String name) {
        if (name == null || name.isEmpty()) return Kind.NONE;
        String logical = MimeUtil.logicalDisplayName(name);
        String ext = FilenameUtils.getExtension(logical).toLowerCase(Locale.ROOT);
        return switch (ext) {
            case "docx", "docm", "dotx", "dotm", "doc", "dot", "odt" -> Kind.WORD;
            case "xlsx", "xlsm", "xltx", "xltm", "xls", "xlt", "ods" -> Kind.EXCEL;
            case "pptx", "pptm", "potx", "ppsx", "ppsm", "ppt", "pps", "odp" -> Kind.POWERPOINT;
            case "rtf" -> Kind.RTF;
            default -> Kind.NONE;
        };
    }

    public static boolean isOfficeDocument(File file, String logicalName) {
        return detectKind(file, logicalName) != Kind.NONE;
    }

    public static Kind detectKind(File file, String logicalName) {
        Kind byName = kindFromName(logicalName);
        if (byName != Kind.NONE) return byName;
        if (file != null) {
            Kind byFileName = kindFromName(file.getName());
            if (byFileName != Kind.NONE) return byFileName;
            String realMime = MimeUtil.getRealMimeType(file);
            if (realMime != null) {
                if (realMime.contains("wordprocessingml") || realMime.equals("application/msword")
                        || realMime.equals("application/vnd.oasis.opendocument.text")) return Kind.WORD;
                if (realMime.contains("spreadsheetml") || realMime.equals("application/vnd.ms-excel")
                        || realMime.equals("application/vnd.oasis.opendocument.spreadsheet")) return Kind.EXCEL;
                if (realMime.contains("presentationml") || realMime.equals("application/vnd.ms-powerpoint")
                        || realMime.equals("application/vnd.oasis.opendocument.presentation")) return Kind.POWERPOINT;
                if (realMime.equals("application/rtf")) return Kind.RTF;
            }
        }
        return Kind.NONE;
    }

    public static String parseToText(File file, String logicalName) throws Exception {
        if (file == null || !file.isFile()) return "";
        String name = logicalName != null && !logicalName.isEmpty() ? logicalName : file.getName();
        byte[] head = new byte[16];
        int headLen;
        try (InputStream is = new FileInputStream(file)) {
            headLen = is.read(head);
        }

        if (headLen >= 5 && head[0] == '{' && head[1] == '\\' && head[2] == 'r' && head[3] == 't' && head[4] == 'f') {
            return parseRtf(file, name);
        }
        if (headLen >= 4 && head[0] == 'P' && head[1] == 'K' && head[2] == 3 && head[3] == 4) {
            return parseZipOffice(file, name);
        }
        if (headLen >= 8
                && (head[0] & 0xFF) == 0xD0 && (head[1] & 0xFF) == 0xCF
                && (head[2] & 0xFF) == 0x11 && (head[3] & 0xFF) == 0xE0) {
            return parseOle2Office(file, name);
        }

        Kind kind = kindFromName(name);
        if (kind == Kind.RTF) return parseRtf(file, name);
        return readPlainUtf8Fallback(file);
    }

    // =========================================================================
    // OOXML / ODF (ZIP + XML)
    // =========================================================================

    private static String parseZipOffice(File file, String name) throws Exception {
        try (ZipFile zf = new ZipFile(file)) {
            if (zf.getEntry("word/document.xml") != null) {
                return parseDocx(zf, name);
            }
            if (zf.getEntry("xl/workbook.xml") != null || findEntryStartingWith(zf, "xl/worksheets/") != null) {
                return parseXlsx(zf, name);
            }
            if (zf.getEntry("ppt/presentation.xml") != null || findEntryStartingWith(zf, "ppt/slides/") != null) {
                return parsePptx(zf, name);
            }
            if (zf.getEntry("content.xml") != null) {
                return parseOdf(zf, name);
            }
            return parseGenericOfficeXmlZip(zf, name);
        }
    }

    private static ZipEntry findEntryStartingWith(ZipFile zf, String prefix) {
        Enumeration<? extends ZipEntry> en = zf.entries();
        while (en.hasMoreElements()) {
            ZipEntry ze = en.nextElement();
            if (!ze.isDirectory() && ze.getName().startsWith(prefix)) return ze;
        }
        return null;
    }

    private static XmlPullParser createPullParser(InputStream is) throws Exception {
        XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
        factory.setNamespaceAware(true);
        XmlPullParser parser = factory.newPullParser();
        parser.setInput(is, StandardCharsets.UTF_8.name());
        return parser;
    }

    private static void appendCoreMetadata(ZipFile zf, StringBuilder out, String fallbackTitle, String typeLabel) {
        ZipEntry core = zf.getEntry("docProps/core.xml");
        String title = null;
        String creator = null;
        String subject = null;
        String modified = null;
        if (core != null) {
            try (InputStream is = zf.getInputStream(core)) {
                XmlPullParser p = createPullParser(is);
                int ev = p.getEventType();
                String current = null;
                while (ev != XmlPullParser.END_DOCUMENT) {
                    if (ev == XmlPullParser.START_TAG) {
                        current = p.getName();
                    } else if (ev == XmlPullParser.TEXT && current != null) {
                        String t = p.getText().trim();
                        if (!t.isEmpty()) {
                            switch (current) {
                                case "title" -> title = t;
                                case "creator" -> creator = t;
                                case "subject" -> subject = t;
                                case "modified" -> modified = t;
                                default -> {}
                            }
                        }
                    } else if (ev == XmlPullParser.END_TAG) {
                        current = null;
                    }
                    ev = p.next();
                }
            } catch (Exception ignored) {}
        }
        String displayTitle = (title != null && !title.isEmpty()) ? title : fallbackTitle;
        out.append("# ").append(displayTitle).append('\n');
        out.append("Type: ").append(typeLabel).append('\n');
        if (creator != null) out.append("Author: ").append(creator).append('\n');
        if (subject != null) out.append("Subject: ").append(subject).append('\n');
        if (modified != null) out.append("Modified: ").append(modified).append('\n');
        out.append("\n---\n\n");
    }

    private static Map<String, String> parseRels(ZipFile zf, String relsPath) {
        Map<String, String> rels = new HashMap<>();
        ZipEntry entry = zf.getEntry(relsPath);
        if (entry == null) return rels;
        try (InputStream is = zf.getInputStream(entry)) {
            XmlPullParser p = createPullParser(is);
            int ev = p.getEventType();
            while (ev != XmlPullParser.END_DOCUMENT) {
                if (ev == XmlPullParser.START_TAG && "Relationship".equals(p.getName())) {
                    String id = p.getAttributeValue(null, "Id");
                    String target = p.getAttributeValue(null, "Target");
                    if (id != null && target != null) rels.put(id, target);
                }
                ev = p.next();
            }
        } catch (Exception ignored) {}
        return rels;
    }

    // ---- DOCX ----

    private static String parseDocx(ZipFile zf, String name) throws Exception {
        StringBuilder out = new StringBuilder(4096);
        appendCoreMetadata(zf, out, name, "Word Document (.docx)");
        Map<String, String> rels = parseRels(zf, "word/_rels/document.xml.rels");

        ZipEntry doc = zf.getEntry("word/document.xml");
        if (doc != null) {
            try (InputStream is = zf.getInputStream(doc)) {
                parseWordBodyXml(is, rels, out);
            }
        }

        appendWordExtraXml(zf, "word/footnotes.xml", "Footnotes", rels, out);
        appendWordExtraXml(zf, "word/comments.xml", "Comments", rels, out);
        return truncateOutput(out);
    }

    private static void appendWordExtraXml(ZipFile zf, String entryPath, String title,
                                           Map<String, String> rels, StringBuilder out) {
        ZipEntry entry = zf.getEntry(entryPath);
        if (entry == null) return;
        StringBuilder extra = new StringBuilder();
        try (InputStream is = zf.getInputStream(entry)) {
            parseWordBodyXml(is, rels, extra);
        } catch (Exception ignored) {
            return;
        }
        String trimmed = extra.toString().trim();
        if (!trimmed.isEmpty()) {
            out.append("\n---\n\n## ").append(title).append("\n\n").append(trimmed).append('\n');
        }
    }

    private static void parseWordBodyXml(InputStream is, Map<String, String> rels, StringBuilder out) throws Exception {
        XmlPullParser p = createPullParser(is);
        int ev = p.getEventType();

        boolean inTable = false;
        List<List<String>> tableRows = new ArrayList<>();
        List<String> currentRow = null;
        StringBuilder cellBuf = null;

        boolean inPara = false;
        String paraStyle = null;
        boolean paraIsList = false;
        boolean inRun = false;
        boolean runBold = false;
        boolean runItalic = false;
        String hyperlinkTarget = null;
        StringBuilder linkText = null;
        StringBuilder paraBuf = new StringBuilder();

        while (ev != XmlPullParser.END_DOCUMENT) {
            if (out.length() > MAX_OUTPUT_CHARS) break;
            if (ev == XmlPullParser.START_TAG) {
                String tag = p.getName();
                if ("tbl".equals(tag)) {
                    inTable = true;
                    tableRows.clear();
                } else if (inTable && "tr".equals(tag)) {
                    currentRow = new ArrayList<>();
                } else if (inTable && "tc".equals(tag)) {
                    cellBuf = new StringBuilder();
                } else if ("p".equals(tag)) {
                    inPara = true;
                    paraStyle = null;
                    paraIsList = false;
                    paraBuf.setLength(0);
                } else if (inPara && "pStyle".equals(tag)) {
                    paraStyle = attrAnyNs(p, "val");
                } else if (inPara && "numPr".equals(tag)) {
                    paraIsList = true;
                } else if ("hyperlink".equals(tag)) {
                    String rId = attrAnyNs(p, "id");
                    String anchor = attrAnyNs(p, "anchor");
                    if (rId != null && rels.containsKey(rId)) hyperlinkTarget = rels.get(rId);
                    else if (anchor != null) hyperlinkTarget = "#" + anchor;
                    linkText = new StringBuilder();
                } else if ("r".equals(tag)) {
                    inRun = true;
                    runBold = false;
                    runItalic = false;
                } else if (inRun && "b".equals(tag)) {
                    String v = attrAnyNs(p, "val");
                    runBold = v == null || !"0".equals(v) && !"false".equalsIgnoreCase(v);
                } else if (inRun && "i".equals(tag)) {
                    String v = attrAnyNs(p, "val");
                    runItalic = v == null || !"0".equals(v) && !"false".equalsIgnoreCase(v);
                } else if ("t".equals(tag) || "delText".equals(tag) || "instrText".equals(tag)) {
                    if ("instrText".equals(tag)) {
                        // Skip raw field codes unless needed
                        skipElement(p);
                    } else {
                        String text = p.nextText();
                        if (text != null && !text.isEmpty()) {
                            StringBuilder dest = (inTable && cellBuf != null) ? cellBuf
                                    : (linkText != null ? linkText : paraBuf);
                            if (!inTable && linkText == null && runBold && !text.trim().isEmpty()) {
                                dest.append("**").append(text).append("**");
                            } else if (!inTable && linkText == null && runItalic && !text.trim().isEmpty()) {
                                dest.append('*').append(text).append('*');
                            } else {
                                dest.append(text);
                            }
                        }
                    }
                } else if ("tab".equals(tag)) {
                    StringBuilder dest = (inTable && cellBuf != null) ? cellBuf : paraBuf;
                    dest.append("    ");
                } else if ("br".equals(tag) || "cr".equals(tag)) {
                    StringBuilder dest = (inTable && cellBuf != null) ? cellBuf : paraBuf;
                    dest.append(inTable ? " " : "\n");
                }
            } else if (ev == XmlPullParser.END_TAG) {
                String tag = p.getName();
                if ("r".equals(tag)) {
                    inRun = false;
                    runBold = false;
                    runItalic = false;
                } else if ("hyperlink".equals(tag)) {
                    if (linkText != null) {
                        String label = linkText.toString().trim();
                        StringBuilder dest = (inTable && cellBuf != null) ? cellBuf : paraBuf;
                        if (!label.isEmpty() && hyperlinkTarget != null && !hyperlinkTarget.isEmpty()) {
                            dest.append('[').append(label).append("](").append(hyperlinkTarget).append(')');
                        } else {
                            dest.append(label);
                        }
                    }
                    hyperlinkTarget = null;
                    linkText = null;
                } else if ("p".equals(tag)) {
                    String text = paraBuf.toString().replaceAll("\\*\\*\\s*\\*\\*", "").trim();
                    if (inTable && cellBuf != null) {
                        if (!text.isEmpty()) {
                            if (cellBuf.length() > 0 && !cellBuf.toString().endsWith(" ")) cellBuf.append(' ');
                            cellBuf.append(text);
                        }
                    } else if (!text.isEmpty()) {
                        formatWordParagraph(text, paraStyle, paraIsList, out);
                    } else if (!inTable) {
                        out.append('\n');
                    }
                    inPara = false;
                } else if (inTable && "tc".equals(tag)) {
                    if (currentRow != null && cellBuf != null) {
                        currentRow.add(sanitizeCell(cellBuf.toString()));
                    }
                    cellBuf = null;
                } else if (inTable && "tr".equals(tag)) {
                    if (currentRow != null && !currentRow.isEmpty()) {
                        tableRows.add(currentRow);
                    }
                    currentRow = null;
                } else if ("tbl".equals(tag)) {
                    inTable = false;
                    if (!tableRows.isEmpty()) {
                        appendMarkdownTable(tableRows, out);
                        out.append('\n');
                    }
                    tableRows.clear();
                }
            }
            ev = p.next();
        }
    }

    private static void formatWordParagraph(String text, String style, boolean isList, StringBuilder out) {
        String s = style == null ? "" : style.toLowerCase(Locale.ROOT);
        if (s.equals("title")) {
            out.append("# ").append(text).append("\n\n");
        } else if (s.equals("subtitle")) {
            out.append("## ").append(text).append("\n\n");
        } else if (s.startsWith("heading")) {
            int level = 2;
            try {
                String num = s.replaceAll("[^0-9]", "");
                if (!num.isEmpty()) level = Math.max(1, Math.min(6, Integer.parseInt(num)));
            } catch (Exception ignored) {}
            out.append("#".repeat(level)).append(' ').append(text).append("\n\n");
        } else if (s.contains("quote")) {
            out.append("> ").append(text).append("\n\n");
        } else if (isList || s.contains("list")) {
            out.append("- ").append(text).append('\n');
        } else {
            out.append(text).append("\n\n");
        }
    }

    // ---- XLSX ----

    private static String parseXlsx(ZipFile zf, String name) throws Exception {
        StringBuilder out = new StringBuilder(4096);
        out.append("# ").append(name).append('\n');
        out.append("Type: Excel Workbook (.xlsx)\n");

        List<String> sharedStrings = parseXlsxSharedStrings(zf);
        LinkedHashMap<String, String> sheets = resolveXlsxSheets(zf);
        out.append("Sheets: ").append(sheets.size()).append("\n\n---\n\n");

        for (Map.Entry<String, String> entry : sheets.entrySet()) {
            if (out.length() > MAX_OUTPUT_CHARS) break;
            ZipEntry ze = zf.getEntry(entry.getValue());
            if (ze == null) continue;
            out.append("## Sheet: ").append(entry.getKey()).append('\n');
            List<List<String>> rows = new ArrayList<>();
            int maxCols = 0;
            boolean truncated = false;
            try (InputStream is = zf.getInputStream(ze)) {
                truncated = parseXlsxSheet(is, sharedStrings, rows);
            }
            for (List<String> r : rows) {
                if (r.size() > maxCols) maxCols = r.size();
            }
            out.append("Rows: ").append(rows.size()).append(" | Columns: ").append(maxCols).append("\n\n");
            if (!rows.isEmpty()) {
                appendMarkdownTable(rows, out);
            }
            if (truncated) {
                out.append("\n> Sheet output truncated after ").append(MAX_SHEET_ROWS).append(" rows.\n");
            }
            out.append('\n');
        }
        return truncateOutput(out);
    }

    private static List<String> parseXlsxSharedStrings(ZipFile zf) {
        List<String> list = new ArrayList<>();
        ZipEntry ss = zf.getEntry("xl/sharedStrings.xml");
        if (ss == null) return list;
        try (InputStream is = zf.getInputStream(ss)) {
            XmlPullParser p = createPullParser(is);
            int ev = p.getEventType();
            StringBuilder siText = null;
            while (ev != XmlPullParser.END_DOCUMENT) {
                if (ev == XmlPullParser.START_TAG) {
                    String tag = p.getName();
                    if ("si".equals(tag)) {
                        siText = new StringBuilder();
                    } else if ("t".equals(tag) && siText != null) {
                        String t = p.nextText();
                        if (t != null) siText.append(t);
                    }
                } else if (ev == XmlPullParser.END_TAG && "si".equals(p.getName())) {
                    list.add(siText != null ? siText.toString() : "");
                    siText = null;
                }
                ev = p.next();
            }
        } catch (Exception ignored) {}
        return list;
    }

    private static LinkedHashMap<String, String> resolveXlsxSheets(ZipFile zf) {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        Map<String, String> rels = parseRels(zf, "xl/_rels/workbook.xml.rels");
        ZipEntry wb = zf.getEntry("xl/workbook.xml");
        if (wb != null) {
            try (InputStream is = zf.getInputStream(wb)) {
                XmlPullParser p = createPullParser(is);
                int ev = p.getEventType();
                while (ev != XmlPullParser.END_DOCUMENT) {
                    if (ev == XmlPullParser.START_TAG && "sheet".equals(p.getName())) {
                        String sheetName = attrAnyNs(p, "name");
                        String rId = attrAnyNs(p, "id");
                        String target = rId != null ? rels.get(rId) : null;
                        if (target != null) {
                            while (target.startsWith("/")) target = target.substring(1);
                            if (!target.startsWith("xl/")) target = "xl/" + target;
                            result.put(sheetName != null ? sheetName : target, target);
                        }
                    }
                    ev = p.next();
                }
            } catch (Exception ignored) {}
        }
        if (result.isEmpty()) {
            List<String> sheetPaths = new ArrayList<>();
            Enumeration<? extends ZipEntry> en = zf.entries();
            while (en.hasMoreElements()) {
                ZipEntry ze = en.nextElement();
                if (!ze.isDirectory() && ze.getName().startsWith("xl/worksheets/") && ze.getName().endsWith(".xml")) {
                    sheetPaths.add(ze.getName());
                }
            }
            sheetPaths.sort(Comparator.comparingInt(OfficeDocumentParser::extractTrailingNumber));
            for (int i = 0; i < sheetPaths.size(); i++) {
                result.put("Sheet" + (i + 1), sheetPaths.get(i));
            }
        }
        return result;
    }

    private static boolean parseXlsxSheet(InputStream is, List<String> sharedStrings,
                                          List<List<String>> rowsOut) throws Exception {
        XmlPullParser p = createPullParser(is);
        int ev = p.getEventType();
        List<String> currentRow = null;
        int nextCol = 0;
        String cellType = null;
        int targetCol = -1;
        String cellValue = null;
        String formulaText = null;
        String inlineStr = null;

        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG) {
                String tag = p.getName();
                if ("row".equals(tag)) {
                    if (rowsOut.size() >= MAX_SHEET_ROWS) return true;
                    currentRow = new ArrayList<>();
                    nextCol = 0;
                } else if ("c".equals(tag) && currentRow != null) {
                    cellType = p.getAttributeValue(null, "t");
                    String ref = p.getAttributeValue(null, "r");
                    targetCol = ref != null ? columnIndexFromRef(ref) : nextCol;
                    if (targetCol < 0) targetCol = nextCol;
                    cellValue = null;
                    formulaText = null;
                    inlineStr = null;
                } else if ("v".equals(tag) && currentRow != null) {
                    cellValue = p.nextText();
                } else if ("f".equals(tag) && currentRow != null) {
                    formulaText = p.nextText();
                } else if ("is".equals(tag) && currentRow != null) {
                    inlineStr = readNestedText(p, "is");
                }
            } else if (ev == XmlPullParser.END_TAG) {
                String tag = p.getName();
                if ("c".equals(tag) && currentRow != null) {
                    String resolved = resolveCellText(cellType, cellValue, inlineStr, formulaText, sharedStrings);
                    if (targetCol < MAX_SHEET_COLS) {
                        while (currentRow.size() < targetCol) currentRow.add("");
                        if (currentRow.size() == targetCol) currentRow.add(resolved);
                        else currentRow.set(targetCol, resolved);
                        nextCol = targetCol + 1;
                    }
                    cellType = null;
                } else if ("row".equals(tag) && currentRow != null) {
                    if (!isRowBlank(currentRow)) {
                        rowsOut.add(currentRow);
                    }
                    currentRow = null;
                }
            }
            ev = p.next();
        }
        return false;
    }

    private static String resolveCellText(String type, String value, String inlineStr,
                                          String formula, List<String> sharedStrings) {
        String text = "";
        if ("inlineStr".equals(type) && inlineStr != null) {
            text = inlineStr;
        } else if ("s".equals(type) && value != null) {
            try {
                int idx = Integer.parseInt(value.trim());
                if (idx >= 0 && idx < sharedStrings.size()) text = sharedStrings.get(idx);
            } catch (Exception ignored) {
                text = value;
            }
        } else if ("b".equals(type) && value != null) {
            text = "1".equals(value.trim()) ? "TRUE" : "FALSE";
        } else if (value != null && !value.isEmpty()) {
            text = value;
        } else if (formula != null && !formula.isEmpty()) {
            text = "=" + formula;
        }
        return sanitizeCell(text);
    }

    private static String readNestedText(XmlPullParser p, String containerTag) throws Exception {
        StringBuilder sb = new StringBuilder();
        int ev = p.next();
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.TEXT) {
                sb.append(p.getText());
            } else if (ev == XmlPullParser.END_TAG && containerTag.equals(p.getName())) {
                break;
            }
            ev = p.next();
        }
        return sb.toString();
    }

    // ---- PPTX ----

    private static String parsePptx(ZipFile zf, String name) throws Exception {
        StringBuilder out = new StringBuilder(4096);
        appendCoreMetadata(zf, out, name, "PowerPoint Presentation (.pptx)");

        List<String> slidePaths = resolvePptxSlides(zf);
        for (int i = 0; i < slidePaths.size(); i++) {
            if (out.length() > MAX_OUTPUT_CHARS) break;
            String slidePath = slidePaths.get(i);
            ZipEntry slideEntry = zf.getEntry(slidePath);
            if (slideEntry == null) continue;
            StringBuilder slideBody = new StringBuilder();
            List<String> titleHolder = new ArrayList<>(1);
            try (InputStream is = zf.getInputStream(slideEntry)) {
                parsePptxSlideXml(is, slideBody, titleHolder);
            }
            String title = titleHolder.isEmpty() ? "" : " — " + titleHolder.get(0);
            out.append("## Slide ").append(i + 1).append(title).append('\n');
            String body = slideBody.toString().trim();
            if (!body.isEmpty()) {
                out.append(body).append('\n');
            }
            String notes = readPptxNotesForSlide(zf, slidePath, i + 1);
            if (!notes.isEmpty()) {
                out.append("\n> Notes: ").append(notes.replace('\n', ' ')).append('\n');
            }
            out.append("\n---\n\n");
        }
        return truncateOutput(out);
    }

    private static List<String> resolvePptxSlides(ZipFile zf) {
        List<String> ordered = new ArrayList<>();
        Map<String, String> rels = parseRels(zf, "ppt/_rels/presentation.xml.rels");
        ZipEntry pres = zf.getEntry("ppt/presentation.xml");
        if (pres != null && !rels.isEmpty()) {
            try (InputStream is = zf.getInputStream(pres)) {
                XmlPullParser p = createPullParser(is);
                int ev = p.getEventType();
                while (ev != XmlPullParser.END_DOCUMENT) {
                    if (ev == XmlPullParser.START_TAG && "sldId".equals(p.getName())) {
                        String rId = attrAnyNs(p, "id");
                        String target = rId != null ? rels.get(rId) : null;
                        if (target != null) {
                            while (target.startsWith("/")) target = target.substring(1);
                            if (!target.startsWith("ppt/")) target = "ppt/" + target;
                            ordered.add(target);
                        }
                    }
                    ev = p.next();
                }
            } catch (Exception ignored) {}
        }
        if (ordered.isEmpty()) {
            Enumeration<? extends ZipEntry> en = zf.entries();
            while (en.hasMoreElements()) {
                ZipEntry ze = en.nextElement();
                String n = ze.getName();
                if (!ze.isDirectory() && n.startsWith("ppt/slides/slide") && n.endsWith(".xml")) {
                    ordered.add(n);
                }
            }
            ordered.sort(Comparator.comparingInt(OfficeDocumentParser::extractTrailingNumber));
        }
        return ordered;
    }

    private static void parsePptxSlideXml(InputStream is, StringBuilder out, List<String> titleHolder) throws Exception {
        XmlPullParser p = createPullParser(is);
        int ev = p.getEventType();
        boolean inTable = false;
        List<List<String>> tableRows = new ArrayList<>();
        List<String> currentRow = null;
        StringBuilder cellBuf = null;
        StringBuilder paraBuf = new StringBuilder();
        int paraLevel = 0;
        boolean isTitleShape = false;

        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG) {
                String tag = p.getName();
                if ("ph".equals(tag)) {
                    String phType = attrAnyNs(p, "type");
                    if ("title".equals(phType) || "ctrTitle".equals(phType)) isTitleShape = true;
                } else if ("sp".equals(tag)) {
                    isTitleShape = false;
                } else if ("tbl".equals(tag)) {
                    inTable = true;
                    tableRows.clear();
                } else if (inTable && "tr".equals(tag)) {
                    currentRow = new ArrayList<>();
                } else if (inTable && "tc".equals(tag)) {
                    cellBuf = new StringBuilder();
                } else if ("p".equals(tag)) {
                    paraBuf.setLength(0);
                    paraLevel = 0;
                } else if ("pPr".equals(tag)) {
                    String lvl = p.getAttributeValue(null, "lvl");
                    if (lvl != null) {
                        try { paraLevel = Math.max(0, Math.min(4, Integer.parseInt(lvl))); } catch (Exception ignored) {}
                    }
                } else if ("t".equals(tag)) {
                    String text = p.nextText();
                    if (text != null) {
                        if (inTable && cellBuf != null) cellBuf.append(text);
                        else paraBuf.append(text);
                    }
                } else if ("br".equals(tag)) {
                    if (inTable && cellBuf != null) cellBuf.append(' ');
                    else paraBuf.append('\n');
                }
            } else if (ev == XmlPullParser.END_TAG) {
                String tag = p.getName();
                if ("p".equals(tag)) {
                    String line = paraBuf.toString().trim();
                    if (!line.isEmpty() && !inTable) {
                        if (isTitleShape && titleHolder.isEmpty()) {
                            titleHolder.add(line.replace('\n', ' '));
                        } else {
                            out.append("  ".repeat(paraLevel)).append("- ").append(line).append('\n');
                        }
                    }
                } else if (inTable && "tc".equals(tag)) {
                    if (currentRow != null && cellBuf != null) currentRow.add(sanitizeCell(cellBuf.toString()));
                    cellBuf = null;
                } else if (inTable && "tr".equals(tag)) {
                    if (currentRow != null && !currentRow.isEmpty()) tableRows.add(currentRow);
                    currentRow = null;
                } else if ("tbl".equals(tag)) {
                    inTable = false;
                    if (!tableRows.isEmpty()) {
                        out.append('\n');
                        appendMarkdownTable(tableRows, out);
                        out.append('\n');
                    }
                    tableRows.clear();
                } else if ("sp".equals(tag)) {
                    isTitleShape = false;
                }
            }
            ev = p.next();
        }
    }

    private static String readPptxNotesForSlide(ZipFile zf, String slidePath, int slideNum) {
        String baseName = FilenameUtils.getName(slidePath);
        Map<String, String> rels = parseRels(zf, "ppt/slides/_rels/" + baseName + ".rels");
        String notesPath = null;
        for (String target : rels.values()) {
            if (target != null && target.contains("notesSlide")) {
                notesPath = target.replace("../", "ppt/");
                if (!notesPath.startsWith("ppt/")) notesPath = "ppt/notesSlides/" + FilenameUtils.getName(target);
                break;
            }
        }
        if (notesPath == null) notesPath = "ppt/notesSlides/notesSlide" + slideNum + ".xml";
        ZipEntry ze = zf.getEntry(notesPath);
        if (ze == null) return "";
        StringBuilder sb = new StringBuilder();
        try (InputStream is = zf.getInputStream(ze)) {
            XmlPullParser p = createPullParser(is);
            int ev = p.getEventType();
            StringBuilder para = new StringBuilder();
            while (ev != XmlPullParser.END_DOCUMENT) {
                if (ev == XmlPullParser.START_TAG && "p".equals(p.getName())) {
                    para.setLength(0);
                } else if (ev == XmlPullParser.START_TAG && "t".equals(p.getName())) {
                    String t = p.nextText();
                    if (t != null) para.append(t);
                } else if (ev == XmlPullParser.END_TAG && "p".equals(p.getName())) {
                    String s = para.toString().trim();
                    if (!s.isEmpty() && !s.matches("\\d+")) {
                        if (sb.length() > 0) sb.append('\n');
                        sb.append(s);
                    }
                }
                ev = p.next();
            }
        } catch (Exception ignored) {}
        return sb.toString().trim();
    }

    // ---- ODF (.odt / .ods / .odp) ----

    private static String parseOdf(ZipFile zf, String name) throws Exception {
        StringBuilder out = new StringBuilder(2048);
        out.append("# ").append(name).append('\n');
        out.append("Type: OpenDocument\n\n---\n\n");
        ZipEntry content = zf.getEntry("content.xml");
        if (content == null) return out.toString();
        try (InputStream is = zf.getInputStream(content)) {
            XmlPullParser p = createPullParser(is);
            int ev = p.getEventType();
            StringBuilder para = new StringBuilder();
            boolean inTable = false;
            List<List<String>> rows = new ArrayList<>();
            List<String> row = null;
            StringBuilder cell = null;
            while (ev != XmlPullParser.END_DOCUMENT) {
                if (ev == XmlPullParser.START_TAG) {
                    String tag = p.getName();
                    if ("table".equals(tag)) {
                        inTable = true;
                        rows.clear();
                        String tableName = attrAnyNs(p, "name");
                        if (tableName != null && !tableName.isEmpty()) {
                            out.append("## ").append(tableName).append('\n');
                        }
                    } else if (inTable && "table-row".equals(tag)) {
                        row = new ArrayList<>();
                    } else if (inTable && ("table-cell".equals(tag) || "covered-table-cell".equals(tag))) {
                        cell = new StringBuilder();
                    } else if ("h".equals(tag) || "p".equals(tag)) {
                        para.setLength(0);
                    }
                } else if (ev == XmlPullParser.TEXT) {
                    String t = p.getText();
                    if (t != null) {
                        if (inTable && cell != null) cell.append(t);
                        else para.append(t);
                    }
                } else if (ev == XmlPullParser.END_TAG) {
                    String tag = p.getName();
                    if ("h".equals(tag)) {
                        String s = para.toString().trim();
                        if (!s.isEmpty() && !inTable) out.append("## ").append(s).append("\n\n");
                    } else if ("p".equals(tag)) {
                        String s = para.toString().trim();
                        if (!s.isEmpty() && !inTable) out.append(s).append("\n\n");
                    } else if (inTable && ("table-cell".equals(tag) || "covered-table-cell".equals(tag))) {
                        if (row != null && cell != null) row.add(sanitizeCell(cell.toString()));
                        cell = null;
                    } else if (inTable && "table-row".equals(tag)) {
                        if (row != null && !isRowBlank(row)) rows.add(row);
                        row = null;
                    } else if ("table".equals(tag)) {
                        inTable = false;
                        if (!rows.isEmpty()) {
                            appendMarkdownTable(rows, out);
                            out.append('\n');
                        }
                        rows.clear();
                    }
                }
                ev = p.next();
            }
        }
        return truncateOutput(out);
    }

    private static String parseGenericOfficeXmlZip(ZipFile zf, String name) {
        StringBuilder out = new StringBuilder(1024);
        out.append("# ").append(name).append("\n\n");
        Enumeration<? extends ZipEntry> en = zf.entries();
        while (en.hasMoreElements()) {
            ZipEntry ze = en.nextElement();
            if (!ze.isDirectory() && ze.getName().endsWith(".xml")) {
                out.append("- ").append(ze.getName()).append('\n');
            }
        }
        return out.toString();
    }

    // =========================================================================
    // RTF Parser (Unicode + Windows-1256 / 1252 codepages)
    // =========================================================================

    private static String parseRtf(File file, String name) throws Exception {
        byte[] bytes = readFileBounded(file, MAX_STREAM_READ_BYTES);
        StringBuilder out = new StringBuilder(Math.min(bytes.length, 65536));
        out.append("# ").append(name).append("\nType: Rich Text Format (.rtf)\n\n---\n\n");

        Charset charset = Charset.forName("windows-1252");
        int n = bytes.length;
        int depth = 0;
        int skipDepth = -1;
        int unicodeSkipCount = 1;
        int[] ucStack = new int[64];
        ucStack[0] = 1;

        int i = 0;
        while (i < n && out.length() < MAX_OUTPUT_CHARS) {
            byte b = bytes[i];
            if (b == '{') {
                depth++;
                if (depth < ucStack.length) ucStack[depth] = unicodeSkipCount;
                i++;
            } else if (b == '}') {
                if (skipDepth == depth) skipDepth = -1;
                if (depth > 0) depth--;
                if (depth < ucStack.length) unicodeSkipCount = ucStack[depth];
                i++;
            } else if (b == '\\') {
                i++;
                if (i >= n) break;
                char next = (char) (bytes[i] & 0xFF);
                if (next == '\\' || next == '{' || next == '}') {
                    if (skipDepth == -1) out.append(next);
                    i++;
                } else if (next == '\'') {
                    i++;
                    if (i + 1 < n) {
                        int val = hexPair(bytes[i], bytes[i + 1]);
                        i += 2;
                        if (val >= 0 && skipDepth == -1) {
                            byte[] single = new byte[]{(byte) val};
                            out.append(new String(single, charset));
                        }
                    }
                } else if (next == '\r' || next == '\n') {
                    if (skipDepth == -1) out.append('\n');
                    i++;
                } else if (next == '*' && i + 1 < n && bytes[i + 1] == '\\') {
                    if (skipDepth == -1) skipDepth = depth;
                    i++;
                } else if (next == '~') {
                    if (skipDepth == -1) out.append(' ');
                    i++;
                } else if (next == '_') {
                    if (skipDepth == -1) out.append('-');
                    i++;
                } else {
                    int startWord = i;
                    while (i < n && Character.isLetter((char) (bytes[i] & 0xFF))) i++;
                    String word = new String(bytes, startWord, i - startWord, StandardCharsets.US_ASCII);
                    int numStart = i;
                    if (i < n && (bytes[i] == '-' || Character.isDigit((char) (bytes[i] & 0xFF)))) {
                        i++;
                        while (i < n && Character.isDigit((char) (bytes[i] & 0xFF))) i++;
                    }
                    String paramStr = i > numStart ? new String(bytes, numStart, i - numStart, StandardCharsets.US_ASCII) : null;
                    if (i < n && bytes[i] == ' ') i++;

                    if ("ansicpg".equals(word) && paramStr != null) {
                        charset = codepageCharset(paramStr, charset);
                    } else if ("uc".equals(word) && paramStr != null) {
                        try { unicodeSkipCount = Math.max(0, Integer.parseInt(paramStr)); } catch (Exception ignored) {}
                    } else if (skipDepth == -1 && isRtfIgnoredGroup(word)) {
                        skipDepth = depth;
                    } else if (skipDepth == -1) {
                        switch (word) {
                            case "par", "line" -> out.append('\n');
                            case "tab" -> out.append('\t');
                            case "cell" -> out.append(" | ");
                            case "row" -> out.append('\n');
                            case "u" -> {
                                if (paramStr != null) {
                                    try {
                                        int code = Integer.parseInt(paramStr);
                                        if (code < 0) code += 65536;
                                        out.append((char) code);
                                        i = skipRtfFallbackChars(bytes, i, unicodeSkipCount);
                                    } catch (Exception ignored) {}
                                }
                            }
                            default -> {}
                        }
                    }
                }
            } else if (b == '\r' || b == '\n') {
                i++;
            } else {
                if (skipDepth == -1) out.append((char) (b & 0xFF));
                i++;
            }
        }
        return truncateOutput(out);
    }

    private static int skipRtfFallbackChars(byte[] bytes, int pos, int skip) {
        int left = skip;
        while (left > 0 && pos < bytes.length) {
            byte b = bytes[pos];
            if (b == '{' || b == '}') break;
            if (b == '\\' && pos + 3 < bytes.length && bytes[pos + 1] == '\'') {
                pos += 4;
            } else {
                pos++;
            }
            left--;
        }
        return pos;
    }

    private static boolean isRtfIgnoredGroup(String word) {
        return switch (word) {
            case "fonttbl", "colortbl", "stylesheet", "info", "pict", "object",
                    "fldinst", "header", "footer", "headerl", "headerr", "headerf",
                    "footerl", "footerr", "footerf", "listtable", "listoverridetable",
                    "revtbl", "rsidtbl", "xmlnstbl", "datastore", "themedata",
                    "colorschememapping", "latentstyles" -> true;
            default -> false;
        };
    }

    private static Charset codepageCharset(String codepage, Charset fallback) {
        try {
            return switch (codepage) {
                case "1256" -> Charset.forName("windows-1256");
                case "1251" -> Charset.forName("windows-1251");
                case "1252" -> Charset.forName("windows-1252");
                case "1250" -> Charset.forName("windows-1250");
                case "1254" -> Charset.forName("windows-1254");
                case "65001" -> StandardCharsets.UTF_8;
                default -> Charset.forName("windows-" + codepage);
            };
        } catch (Exception ignored) {
            return fallback;
        }
    }

    // =========================================================================
    // Legacy OLE2 / CFBF (.doc, .xls, .ppt)
    // =========================================================================

    private static String parseOle2Office(File file, String name) {
        try {
            Map<String, byte[]> streams = readOle2Streams(file);
            if (streams.containsKey("WordDocument")) {
                return parseLegacyDoc(streams, name);
            }
            if (streams.containsKey("Workbook") || streams.containsKey("Book")) {
                byte[] wb = streams.containsKey("Workbook") ? streams.get("Workbook") : streams.get("Book");
                return parseLegacyXls(wb, name);
            }
            if (streams.containsKey("PowerPoint Document")) {
                return parseLegacyPpt(streams.get("PowerPoint Document"), name);
            }
        } catch (Exception ignored) {}
        return fallbackScanOle2(file, name);
    }

    private static Map<String, byte[]> readOle2Streams(File file) throws Exception {
        byte[] data = readFileBounded(file, MAX_STREAM_READ_BYTES);
        if (data.length < 512) return Collections.emptyMap();
        ByteBuffer hdr = ByteBuffer.wrap(data, 0, 512).order(ByteOrder.LITTLE_ENDIAN);
        int sectorShift = hdr.getShort(0x1E) & 0xFFFF;
        int sectorSize = 1 << (sectorShift == 12 ? 12 : 9);
        int miniSectorShift = hdr.getShort(0x20) & 0xFFFF;
        int miniSectorSize = 1 << (miniSectorShift == 6 ? 6 : 6);
        int firstDirSector = hdr.getInt(0x30);
        int miniCutoff = hdr.getInt(0x38);
        int firstMiniFatSector = hdr.getInt(0x3C);
        int numMiniFatSectors = hdr.getInt(0x40);
        int firstDifatSector = hdr.getInt(0x44);
        int numDifatSectors = hdr.getInt(0x48);

        List<Integer> fatSectors = new ArrayList<>();
        for (int i = 0; i < 109; i++) {
            int s = hdr.getInt(0x4C + i * 4);
            if (s >= 0) fatSectors.add(s);
        }
        int difatSec = firstDifatSector;
        int difatCount = 0;
        while (difatSec >= 0 && difatCount < numDifatSectors && difatCount < 64) {
            int off = (difatSec + 1) * sectorSize;
            if (off + sectorSize > data.length) break;
            ByteBuffer db = ByteBuffer.wrap(data, off, sectorSize).order(ByteOrder.LITTLE_ENDIAN);
            int entries = sectorSize / 4 - 1;
            for (int i = 0; i < entries; i++) {
                int s = db.getInt(i * 4);
                if (s >= 0) fatSectors.add(s);
            }
            difatSec = db.getInt(entries * 4);
            difatCount++;
        }

        int[] fat = new int[fatSectors.size() * (sectorSize / 4)];
        int fatPos = 0;
        for (int s : fatSectors) {
            int off = (s + 1) * sectorSize;
            if (off + sectorSize > data.length) continue;
            ByteBuffer fb = ByteBuffer.wrap(data, off, sectorSize).order(ByteOrder.LITTLE_ENDIAN);
            for (int i = 0; i < sectorSize / 4; i++) {
                fat[fatPos++] = fb.getInt(i * 4);
            }
        }

        byte[] dirBytes = readChain(data, fat, firstDirSector, sectorSize, -1);
        byte[] miniFatBytes = (firstMiniFatSector >= 0 && numMiniFatSectors > 0)
                ? readChain(data, fat, firstMiniFatSector, sectorSize, numMiniFatSectors * sectorSize)
                : new byte[0];
        int[] miniFat = new int[miniFatBytes.length / 4];
        if (miniFat.length > 0) {
            ByteBuffer mb = ByteBuffer.wrap(miniFatBytes).order(ByteOrder.LITTLE_ENDIAN);
            for (int i = 0; i < miniFat.length; i++) miniFat[i] = mb.getInt(i * 4);
        }

        byte[] miniStream = new byte[0];
        Map<String, byte[]> streams = new LinkedHashMap<>();
        int numEntries = dirBytes.length / 128;
        for (int i = 0; i < numEntries; i++) {
            int off = i * 128;
            int type = dirBytes[off + 0x42] & 0xFF;
            if (type != 5 && type != 2) continue;
            ByteBuffer eb = ByteBuffer.wrap(dirBytes, off, 128).order(ByteOrder.LITTLE_ENDIAN);
            int nameLen = eb.getShort(0x40) & 0xFFFF;
            if (nameLen < 2 || nameLen > 64) continue;
            String entryName = new String(dirBytes, off, nameLen - 2, StandardCharsets.UTF_16LE);
            int startSector = eb.getInt(0x74);
            long size = eb.getInt(0x78) & 0xFFFFFFFFL;
            if (size == 0 || startSector < 0) continue;
            if (type == 5) {
                miniStream = readChain(data, fat, startSector, sectorSize, (int) Math.min(size, MAX_STREAM_READ_BYTES));
            } else {
                int capped = (int) Math.min(size, MAX_STREAM_READ_BYTES);
                byte[] streamBytes = size < miniCutoff && miniStream.length > 0 && miniFat.length > 0
                        ? readMiniChain(miniStream, miniFat, startSector, miniSectorSize, capped)
                        : readChain(data, fat, startSector, sectorSize, capped);
                streams.put(entryName, streamBytes);
            }
        }
        return streams;
    }

    private static byte[] readChain(byte[] data, int[] fat, int startSector, int sectorSize, int expectedSize) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int sec = startSector;
        int steps = 0;
        int maxSteps = Math.max(1024, data.length / sectorSize + 8);
        while (sec >= 0 && steps++ < maxSteps) {
            int off = (sec + 1) * sectorSize;
            if (off < 0 || off + sectorSize > data.length) break;
            int toCopy = expectedSize > 0 ? Math.min(sectorSize, expectedSize - baos.size()) : sectorSize;
            if (toCopy <= 0) break;
            baos.write(data, off, toCopy);
            if (sec >= fat.length) break;
            sec = fat[sec];
        }
        return baos.toByteArray();
    }

    private static byte[] readMiniChain(byte[] miniStream, int[] miniFat, int startSector, int miniSectorSize, int expectedSize) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int sec = startSector;
        int steps = 0;
        int maxSteps = Math.max(1024, miniStream.length / miniSectorSize + 8);
        while (sec >= 0 && steps++ < maxSteps) {
            int off = sec * miniSectorSize;
            if (off < 0 || off + miniSectorSize > miniStream.length) break;
            int toCopy = Math.min(miniSectorSize, expectedSize - baos.size());
            if (toCopy <= 0) break;
            baos.write(miniStream, off, toCopy);
            if (sec >= miniFat.length) break;
            sec = miniFat[sec];
        }
        return baos.toByteArray();
    }

    private static String parseLegacyDoc(Map<String, byte[]> streams, String name) {
        byte[] wordDoc = streams.get("WordDocument");
        StringBuilder out = new StringBuilder();
        out.append("# ").append(name).append("\nType: Word Document (.doc)\n\n---\n\n");
        if (wordDoc == null || wordDoc.length < 0x1B0) return out.toString();

        ByteBuffer fib = ByteBuffer.wrap(wordDoc).order(ByteOrder.LITTLE_ENDIAN);
        int flags = fib.getShort(0x0A) & 0xFFFF;
        boolean use1Table = (flags & 0x0200) != 0;
        byte[] table = streams.get(use1Table ? "1Table" : "0Table");
        int fcMin = fib.getInt(0x18);
        int ccpText = fib.getInt(0x4C);
        int fcClx = wordDoc.length > 0x1A6 ? fib.getInt(0x1A2) : 0;
        int lcbClx = wordDoc.length > 0x1AA ? fib.getInt(0x1A6) : 0;

        String extracted = null;
        if (table != null && fcClx >= 0 && lcbClx > 8 && fcClx + lcbClx <= table.length) {
            extracted = extractDocPieceTable(wordDoc, table, fcClx, lcbClx, ccpText);
        }
        if (extracted == null || extracted.trim().isEmpty()) {
            // Fallback bounded text span after FIB
            int start = Math.max(0, Math.min(wordDoc.length, fcMin));
            int end = ccpText > 0 ? Math.min(wordDoc.length, start + ccpText * 2) : wordDoc.length;
            extracted = cleanDocControlChars(extractReadableRuns(wordDoc, start, end));
        }
        out.append(extracted);
        return truncateOutput(out);
    }

    private static String extractDocPieceTable(byte[] wordDoc, byte[] table, int fcClx, int lcbClx, int ccpText) {
        int pos = fcClx;
        int end = fcClx + lcbClx;
        while (pos < end) {
            int clxt = table[pos] & 0xFF;
            if (clxt == 1 && pos + 3 <= end) {
                int cbGrpprl = ((table[pos + 1] & 0xFF) | ((table[pos + 2] & 0xFF) << 8));
                pos += 3 + cbGrpprl;
            } else if (clxt == 2 && pos + 5 <= end) {
                ByteBuffer pb = ByteBuffer.wrap(table, pos + 1, end - (pos + 1)).order(ByteOrder.LITTLE_ENDIAN);
                int lcb = pb.getInt(0);
                if (lcb < 12 || pos + 5 + lcb > end) break;
                int n = (lcb - 4) / 12;
                int[] cps = new int[n + 1];
                for (int i = 0; i <= n; i++) cps[i] = pb.getInt(4 + i * 4);
                StringBuilder text = new StringBuilder();
                int limitCp = ccpText > 0 ? ccpText : Integer.MAX_VALUE;
                int pcdStart = 4 + (n + 1) * 4;
                for (int i = 0; i < n; i++) {
                    if (cps[i] >= limitCp) break;
                    int cpLen = Math.min(cps[i + 1], limitCp) - cps[i];
                    if (cpLen <= 0) continue;
                    int fc = pb.getInt(pcdStart + i * 8 + 2);
                    boolean compressed = (fc & 0x40000000) != 0;
                    int offset = compressed ? (fc & ~0x40000000) / 2 : fc;
                    int byteLen = compressed ? cpLen : cpLen * 2;
                    if (offset < 0 || offset + byteLen > wordDoc.length) continue;
                    if (compressed) {
                        text.append(new String(wordDoc, offset, byteLen, Charset.forName("windows-1252")));
                    } else {
                        text.append(new String(wordDoc, offset, byteLen, StandardCharsets.UTF_16LE));
                    }
                }
                return cleanDocControlChars(text.toString());
            } else {
                break;
            }
        }
        return null;
    }

    private static String cleanDocControlChars(String raw) {
        StringBuilder sb = new StringBuilder(raw.length());
        boolean inFieldCode = false;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == 0x13) { inFieldCode = true; continue; }
            if (c == 0x14) { inFieldCode = false; continue; }
            if (c == 0x15) { inFieldCode = false; continue; }
            if (inFieldCode) continue;
            if (c == '\r' || c == 0x0B) sb.append('\n');
            else if (c == 0x07) sb.append(" | ");
            else if (c == '\t') sb.append("    ");
            else if (c >= 32 || c == '\n') sb.append(c);
        }
        return sb.toString().replaceAll("\n{3,}", "\n\n").trim();
    }

    private static String parseLegacyXls(byte[] wb, String name) {
        StringBuilder out = new StringBuilder();
        out.append("# ").append(name).append("\nType: Excel Workbook (.xls)\n");
        if (wb == null || wb.length < 4) return out.toString();

        List<String> sheetNames = new ArrayList<>();
        List<Integer> sheetOffsets = new ArrayList<>();
        List<String> sst = new ArrayList<>();

        // Pass 1: Boundsheets & SST
        int pos = 0;
        while (pos + 4 <= wb.length) {
            int recId = (wb[pos] & 0xFF) | ((wb[pos + 1] & 0xFF) << 8);
            int recLen = (wb[pos + 2] & 0xFF) | ((wb[pos + 3] & 0xFF) << 8);
            pos += 4;
            if (pos + recLen > wb.length) break;
            if (recId == 0x0085 && recLen >= 8) {
                ByteBuffer bb = ByteBuffer.wrap(wb, pos, recLen).order(ByteOrder.LITTLE_ENDIAN);
                int bofOffset = bb.getInt(0);
                int cch = bb.get(6) & 0xFF;
                int flags = bb.get(7) & 0xFF;
                boolean unicode = (flags & 0x01) != 0;
                String sheetName = readBiffStringBody(wb, pos + 8, recLen - 8, cch, unicode);
                sheetNames.add(sheetName);
                sheetOffsets.add(bofOffset);
            } else if (recId == 0x00FC) {
                parseBiffSst(wb, pos - 4, sst);
            }
            pos += recLen;
        }

        out.append("Sheets: ").append(sheetNames.size()).append("\n\n---\n\n");
        for (int s = 0; s < sheetNames.size(); s++) {
            if (out.length() > MAX_OUTPUT_CHARS) break;
            out.append("## Sheet: ").append(sheetNames.get(s)).append("\n\n");
            int start = sheetOffsets.get(s);
            Map<Integer, List<String>> rowMap = new TreeMap<>();
            int p = Math.max(0, start);
            boolean started = false;
            while (p + 4 <= wb.length) {
                int recId = (wb[p] & 0xFF) | ((wb[p + 1] & 0xFF) << 8);
                int recLen = (wb[p + 2] & 0xFF) | ((wb[p + 3] & 0xFF) << 8);
                p += 4;
                if (p + recLen > wb.length) break;
                if (recId == 0x0809) {
                    if (started) break;
                    started = true;
                } else if (recId == 0x000A && started) {
                    break;
                } else if (started && rowMap.size() < MAX_SHEET_ROWS) {
                    parseBiffCellRecord(recId, wb, p, recLen, sst, rowMap);
                }
                p += recLen;
            }
            if (!rowMap.isEmpty()) {
                List<List<String>> table = new ArrayList<>(rowMap.values());
                appendMarkdownTable(table, out);
            }
            out.append('\n');
        }
        return truncateOutput(out);
    }

    private static void parseBiffSst(byte[] wb, int sstHeaderPos, List<String> sstOut) {
        try {
            int pos = sstHeaderPos;
            int firstLen = (wb[pos + 2] & 0xFF) | ((wb[pos + 3] & 0xFF) << 8);
            if (firstLen < 8 || pos + 4 + firstLen > wb.length) return;
            ByteBuffer hdr = ByteBuffer.wrap(wb, pos + 4, firstLen).order(ByteOrder.LITTLE_ENDIAN);
            int totalUnique = hdr.getInt(4);
            pos += 12;
            int curEnd = sstHeaderPos + 4 + firstLen;
            for (int idx = 0; idx < totalUnique && idx < 200_000; idx++) {
                if (pos >= curEnd) {
                    if (curEnd + 4 > wb.length) break;
                    int nextId = (wb[curEnd] & 0xFF) | ((wb[curEnd + 1] & 0xFF) << 8);
                    int nextLen = (wb[curEnd + 2] & 0xFF) | ((wb[curEnd + 3] & 0xFF) << 8);
                    if (nextId != 0x003C) break;
                    pos = curEnd + 4;
                    curEnd = pos + nextLen;
                }
                if (pos + 3 > curEnd) break;
                int cch = (wb[pos] & 0xFF) | ((wb[pos + 1] & 0xFF) << 8);
                int flags = wb[pos + 2] & 0xFF;
                pos += 3;
                boolean highByte = (flags & 0x01) != 0;
                boolean ext = (flags & 0x04) != 0;
                boolean rich = (flags & 0x08) != 0;
                int rt = 0;
                int cchExt = 0;
                if (rich && pos + 2 <= curEnd) {
                    rt = (wb[pos] & 0xFF) | ((wb[pos + 1] & 0xFF) << 8);
                    pos += 2;
                }
                if (ext && pos + 4 <= curEnd) {
                    cchExt = ByteBuffer.wrap(wb, pos, 4).order(ByteOrder.LITTLE_ENDIAN).getInt(0);
                    pos += 4;
                }
                StringBuilder s = new StringBuilder(Math.min(cch, 256));
                int remaining = cch;
                while (remaining > 0) {
                    if (pos >= curEnd) {
                        if (curEnd + 4 > wb.length) break;
                        int nextId = (wb[curEnd] & 0xFF) | ((wb[curEnd + 1] & 0xFF) << 8);
                        int nextLen = (wb[curEnd + 2] & 0xFF) | ((wb[curEnd + 3] & 0xFF) << 8);
                        if (nextId != 0x003C || nextLen < 1) break;
                        pos = curEnd + 4;
                        curEnd = pos + nextLen;
                        highByte = (wb[pos] & 0xFF) != 0;
                        pos++;
                    }
                    int step = highByte ? 2 : 1;
                    int availChars = (curEnd - pos) / step;
                    if (availChars <= 0) break;
                    int take = Math.min(remaining, availChars);
                    s.append(new String(wb, pos, take * step, highByte ? StandardCharsets.UTF_16LE : StandardCharsets.ISO_8859_1));
                    pos += take * step;
                    remaining -= take;
                }
                sstOut.add(s.toString());
                int extraBytes = rt * 4 + Math.max(0, cchExt);
                while (extraBytes > 0) {
                    int avail = curEnd - pos;
                    if (avail >= extraBytes) {
                        pos += extraBytes;
                        break;
                    }
                    extraBytes -= avail;
                    pos = curEnd;
                    if (curEnd + 4 > wb.length) break;
                    int nextId = (wb[curEnd] & 0xFF) | ((wb[curEnd + 1] & 0xFF) << 8);
                    int nextLen = (wb[curEnd + 2] & 0xFF) | ((wb[curEnd + 3] & 0xFF) << 8);
                    if (nextId != 0x003C) break;
                    pos = curEnd + 4;
                    curEnd = pos + nextLen;
                }
            }
        } catch (Exception ignored) {}
    }

    private static void parseBiffCellRecord(int recId, byte[] wb, int pos, int len,
                                            List<String> sst, Map<Integer, List<String>> rowMap) {
        if (len < 6) return;
        ByteBuffer bb = ByteBuffer.wrap(wb, pos, len).order(ByteOrder.LITTLE_ENDIAN);
        int row = bb.getShort(0) & 0xFFFF;
        int col = bb.getShort(2) & 0xFFFF;
        if (col >= MAX_SHEET_COLS) return;
        switch (recId) {
            case 0x00FD -> { // LABELSST
                if (len >= 10) {
                    int sstIdx = bb.getInt(6);
                    String val = sstIdx >= 0 && sstIdx < sst.size() ? sst.get(sstIdx) : "";
                    putSheetCell(rowMap, row, col, val);
                }
            }
            case 0x0204 -> { // LABEL
                if (len >= 9) {
                    int cch = bb.getShort(6) & 0xFFFF;
                    boolean uni = (wb[pos + 8] & 0x01) != 0;
                    putSheetCell(rowMap, row, col, readBiffStringBody(wb, pos + 9, len - 9, cch, uni));
                }
            }
            case 0x0203 -> { // NUMBER
                if (len >= 14) {
                    putSheetCell(rowMap, row, col, formatDouble(bb.getDouble(6)));
                }
            }
            case 0x027E -> { // RK
                if (len >= 10) {
                    putSheetCell(rowMap, row, col, formatDouble(decodeRk(bb.getInt(6))));
                }
            }
            case 0x00BD -> { // MULRK
                int firstCol = col;
                int lastCol = len >= 6 ? (bb.getShort(len - 2) & 0xFFFF) : firstCol;
                int count = Math.min(MAX_SHEET_COLS - firstCol, (len - 6) / 6);
                for (int c = 0; c < count && firstCol + c <= lastCol; c++) {
                    int rk = bb.getInt(4 + c * 6 + 2);
                    putSheetCell(rowMap, row, firstCol + c, formatDouble(decodeRk(rk)));
                }
            }
            case 0x0205 -> { // BOOLERR
                if (len >= 8) {
                    int v = wb[pos + 6] & 0xFF;
                    int isErr = wb[pos + 7] & 0xFF;
                    putSheetCell(rowMap, row, col, isErr == 0 ? (v != 0 ? "TRUE" : "FALSE") : "#ERR");
                }
            }
            default -> {}
        }
    }

    private static void putSheetCell(Map<Integer, List<String>> rowMap, int row, int col, String value) {
        List<String> list = rowMap.computeIfAbsent(row, k -> new ArrayList<>());
        while (list.size() < col) list.add("");
        String clean = sanitizeCell(value);
        if (list.size() == col) list.add(clean);
        else list.set(col, clean);
    }

    private static double decodeRk(int rk) {
        double val;
        if ((rk & 0x02) != 0) {
            val = rk >> 2;
        } else {
            long bits = ((long) (rk & ~0x03)) << 32;
            val = Double.longBitsToDouble(bits);
        }
        return (rk & 0x01) != 0 ? val / 100.0 : val;
    }

    private static String formatDouble(double d) {
        if (d == Math.rint(d) && Math.abs(d) < 1e15) {
            return String.valueOf((long) d);
        }
        return String.valueOf(d);
    }

    private static String readBiffStringBody(byte[] wb, int pos, int maxLen, int cch, boolean unicode) {
        int byteLen = Math.min(maxLen, unicode ? cch * 2 : cch);
        if (byteLen <= 0) return "";
        return new String(wb, pos, byteLen, unicode ? StandardCharsets.UTF_16LE : StandardCharsets.ISO_8859_1);
    }

    private static String parseLegacyPpt(byte[] ppt, String name) {
        StringBuilder out = new StringBuilder();
        out.append("# ").append(name).append("\nType: PowerPoint Presentation (.ppt)\n\n---\n\n");
        if (ppt == null || ppt.length < 8) return out.toString();

        List<String> texts = new ArrayList<>();
        scanPptRecords(ppt, 0, ppt.length, texts);
        int idx = 1;
        for (String t : texts) {
            String clean = t.trim();
            if (!clean.isEmpty()) {
                out.append("## Text Block ").append(idx++).append('\n').append(clean).append("\n\n");
            }
        }
        return truncateOutput(out);
    }

    private static void scanPptRecords(byte[] data, int pos, int end, List<String> out) {
        int cur = pos;
        while (cur + 8 <= end) {
            ByteBuffer bb = ByteBuffer.wrap(data, cur, 8).order(ByteOrder.LITTLE_ENDIAN);
            int verInst = bb.getShort(0) & 0xFFFF;
            int ver = verInst & 0x0F;
            int type = bb.getShort(2) & 0xFFFF;
            int len = bb.getInt(4);
            cur += 8;
            if (len < 0 || cur + len > end) break;
            if (ver == 0x0F) {
                scanPptRecords(data, cur, cur + len, out);
            } else if (type == 0x0FA0 && len > 0) { // TextCharsAtom (UTF-16LE)
                out.add(new String(data, cur, len, StandardCharsets.UTF_16LE).replace('\r', '\n'));
            } else if (type == 0x0FA8 && len > 0) { // TextBytesAtom (ANSI)
                out.add(new String(data, cur, len, Charset.forName("windows-1252")).replace('\r', '\n'));
            }
            cur += len;
        }
    }

    private static String fallbackScanOle2(File file, String name) {
        try {
            byte[] data = readFileBounded(file, 4 * 1024 * 1024);
            return "# " + name + "\n\n" + extractReadableRuns(data, 0, data.length);
        } catch (Exception e) {
            return "# " + name + "\n\n" + e.getMessage();
        }
    }

    private static String extractReadableRuns(byte[] data, int start, int end) {
        StringBuilder sb = new StringBuilder();
        StringBuilder run = new StringBuilder();
        for (int i = start; i < end; i++) {
            int b = data[i] & 0xFF;
            if ((b >= 32 && b < 127) || b == '\n' || b == '\r' || b == '\t') {
                run.append(b == '\r' ? '\n' : (char) b);
            } else {
                if (run.length() >= 8) {
                    sb.append(run).append('\n');
                }
                run.setLength(0);
            }
        }
        if (run.length() >= 8) sb.append(run);
        return sb.toString();
    }

    // =========================================================================
    // Shared formatting & utility helpers
    // =========================================================================

    private static void appendMarkdownTable(List<List<String>> rows, StringBuilder out) {
        int colCount = 0;
        for (List<String> r : rows) {
            if (r.size() > colCount) colCount = r.size();
        }
        if (colCount == 0) return;

        int[] widths = new int[colCount];
        for (int c = 0; c < colCount; c++) widths[c] = 3;
        int sample = Math.min(rows.size(), 150);
        for (int r = 0; r < sample; r++) {
            List<String> row = rows.get(r);
            for (int c = 0; c < Math.min(colCount, row.size()); c++) {
                widths[c] = Math.min(36, Math.max(widths[c], row.get(c).length()));
            }
        }

        for (int r = 0; r < rows.size(); r++) {
            if (out.length() > MAX_OUTPUT_CHARS) break;
            List<String> row = rows.get(r);
            out.append('|');
            for (int c = 0; c < colCount; c++) {
                String cell = c < row.size() ? row.get(c) : "";
                out.append(' ').append(padRight(cell, widths[c])).append(" |");
            }
            out.append('\n');
            if (r == 0) {
                out.append('|');
                for (int c = 0; c < colCount; c++) {
                    out.append(' ').append("-".repeat(widths[c])).append(" |");
                }
                out.append('\n');
            }
        }
    }

    private static String padRight(String s, int width) {
        if (s.length() >= width) return s;
        return s + " ".repeat(width - s.length());
    }

    private static String sanitizeCell(String text) {
        if (text == null || text.isEmpty()) return "";
        return text.replace('\r', ' ').replace('\n', ' ').replace("|", "\\|").trim();
    }

    private static boolean isRowBlank(List<String> row) {
        for (String cell : row) {
            if (cell != null && !cell.isEmpty()) return false;
        }
        return true;
    }

    private static int columnIndexFromRef(String ref) {
        Matcher m = CELL_REF_PATTERN.matcher(ref.trim());
        if (!m.matches()) return -1;
        String letters = m.group(1).toUpperCase(Locale.ROOT);
        int col = 0;
        for (int i = 0; i < letters.length(); i++) {
            col = col * 26 + (letters.charAt(i) - 'A' + 1);
        }
        return col - 1;
    }

    private static int extractTrailingNumber(String path) {
        Matcher m1 = SLIDE_NUM_PATTERN.matcher(path);
        if (m1.find()) return Integer.parseInt(m1.group(1));
        Matcher m2 = SHEET_NUM_PATTERN.matcher(path);
        if (m2.find()) return Integer.parseInt(m2.group(1));
        return Integer.MAX_VALUE;
    }

    private static String attrAnyNs(XmlPullParser p, String localName) {
        for (int i = 0; i < p.getAttributeCount(); i++) {
            if (localName.equals(p.getAttributeName(i))) return p.getAttributeValue(i);
        }
        return null;
    }

    private static void skipElement(XmlPullParser p) throws Exception {
        int depth = 1;
        while (depth > 0) {
            int ev = p.next();
            if (ev == XmlPullParser.START_TAG) depth++;
            else if (ev == XmlPullParser.END_TAG) depth--;
            else if (ev == XmlPullParser.END_DOCUMENT) break;
        }
    }

    private static int hexPair(byte b1, byte b2) {
        int h1 = Character.digit((char) (b1 & 0xFF), 16);
        int h2 = Character.digit((char) (b2 & 0xFF), 16);
        return (h1 >= 0 && h2 >= 0) ? ((h1 << 4) | h2) : -1;
    }

    private static byte[] readFileBounded(File file, int limit) throws Exception {
        int len = (int) Math.min(file.length(), limit);
        byte[] buf = new byte[len];
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            raf.readFully(buf);
        }
        return buf;
    }

    private static String readPlainUtf8Fallback(File file) throws Exception {
        return new String(readFileBounded(file, MAX_STREAM_READ_BYTES), StandardCharsets.UTF_8);
    }

    private static String truncateOutput(StringBuilder out) {
        if (out.length() > MAX_OUTPUT_CHARS) {
            return out.substring(0, MAX_OUTPUT_CHARS) + "\n\n... (output truncated)";
        }
        return out.toString();
    }
}
