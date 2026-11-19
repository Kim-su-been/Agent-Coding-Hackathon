package com.outputhub.api;

import com.outputhub.domain.ChecklistItem;
import com.outputhub.domain.Deliverable;
import com.outputhub.domain.Project;
import com.outputhub.domain.Version;
import com.outputhub.domain.VersionNaming;
import com.outputhub.storage.StorageService;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xddf.usermodel.chart.BarDirection;
import org.apache.poi.xddf.usermodel.chart.XDDFBarChartData;
import org.apache.poi.xddf.usermodel.chart.AxisPosition;
import org.apache.poi.xddf.usermodel.chart.XDDFCategoryAxis;
import org.apache.poi.xddf.usermodel.chart.XDDFValueAxis;
import org.apache.poi.xddf.usermodel.chart.ChartTypes;
import org.apache.poi.xddf.usermodel.chart.XDDFDoughnutChartData;
import org.apache.poi.xddf.usermodel.chart.XDDFDataSourcesFactory;
import org.apache.poi.xssf.usermodel.XSSFChart;
import org.apache.poi.xssf.usermodel.XSSFClientAnchor;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xddf.usermodel.text.XDDFFont;
import org.apache.poi.xddf.usermodel.text.XDDFTextBody;
import org.apache.poi.xddf.usermodel.text.XDDFTextParagraph;
import org.apache.poi.xddf.usermodel.text.XDDFRunProperties;
import org.apache.poi.common.usermodel.fonts.FontGroup;
import org.openxmlformats.schemas.drawingml.x2006.chart.CTDLbls;
import org.openxmlformats.schemas.drawingml.x2006.main.CTTextBody;
import org.apache.poi.xddf.usermodel.chart.XDDFChartLegend;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** 대시보드 엑셀 내보내기(REQ-outputhub-012)와 산출물 폴더 다운로드(REQ-outputhub-011). */
@RestController
public class ExportController {

    private static final DateTimeFormatter EXPORT_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String OLD_FOLDER = "old";

    /** REQ-outputhub-016의 대시보드 그래프를 엑셀에도 동일하게 옮긴다(모두 검토완료 기준, 사용자 요구 2026-11-19). */
    private static final String PHASE_CHART_TITLE = "단계별 완료율(%) · 검토완료 기준";
    private static final String OVERALL_CHART_TITLE = "전체 산출물 완료율(%) · 검토완료 기준";
    private static final String CONFIRMED_LABEL = "검토완료";
    private static final String REMAINING_LABEL = "미완료";

    /** 엑셀과 차트 텍스트에 쓸 폰트(사용자 요구 2026-11-19). Windows 기본 한글 폰트인 맑은 고딕. */
    private static final String FONT_NAME = "맑은 고딕";
    private static final int CHART_FIRST_COLUMN = 7;
    private static final int CHART_LAST_COLUMN = 15;
    private static final int PHASE_CHART_LAST_ROW = 16;
    private static final int OVERALL_CHART_FIRST_ROW = 17;
    private static final int OVERALL_CHART_LAST_ROW = 33;

    private final Project.Repository projects;
    private final ChecklistItem.Repository items;
    private final Deliverable.Repository deliverables;
    private final Version.Repository versions;
    private final StorageService storage;

    public ExportController(Project.Repository projects, ChecklistItem.Repository items,
                            Deliverable.Repository deliverables, Version.Repository versions,
                            StorageService storage) {
        this.projects = projects;
        this.items = items;
        this.deliverables = deliverables;
        this.versions = versions;
        this.storage = storage;
    }

    /** 대시보드 내보내기: {프로젝트}_산출물현황_{날짜8자리}.xlsx */
    @Transactional(readOnly = true)
    @GetMapping("/api/projects/{id}/export/dashboard.xlsx")
    public ResponseEntity<FileSystemResource> exportDashboard(@PathVariable Long id) {
        Project project = projects.findById(id).orElse(null);
        if (project == null) {
            return ResponseEntity.notFound().build();
        }
        String date = LocalDate.now().format(EXPORT_DATE);
        String fileName = sanitize(project.getName()) + "_산출물현황_" + date + ".xlsx";
        Path target = storage.root().resolve(fileName);
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            applyDefaultCellFont(workbook);
            writeSummarySheet(workbook, project.getId());
            writeLedgerSheet(workbook, project.getId());
            Files.createDirectories(target.getParent());
            try (OutputStream out = Files.newOutputStream(target)) {
                workbook.write(out);
            }
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
        return attachment(target, fileName,
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    }

    /** 산출물 다운로드: {프로젝트}_산출물목록_{날짜8자리}.zip (최신본만, old는 old/ 폴더) */
    @Transactional(readOnly = true)
    @GetMapping("/api/projects/{id}/export/deliverables.zip")
    public ResponseEntity<FileSystemResource> exportDeliverables(@PathVariable Long id) throws IOException {
        Project project = projects.findById(id).orElse(null);
        if (project == null) {
            return ResponseEntity.notFound().build();
        }
        String date = LocalDate.now().format(EXPORT_DATE);
        String base = sanitize(project.getName()) + "_산출물목록_" + date;
        Path zip = storage.root().resolve(base + ".zip");
        try (ZipOutputStream zipOut = new ZipOutputStream(Files.newOutputStream(zip))) {
            for (ChecklistItem item : items.findByProjectIdOrderByIdAsc(project.getId())) {
                addVersionEntries(zipOut, base, item);
            }
        }
        return attachment(zip, base + ".zip", "application/zip");
    }

    private void addVersionEntries(ZipOutputStream zipOut, String base, ChecklistItem item) throws IOException {
        var deliverable = deliverables.findByItemId(item.getId()).orElse(null);
        if (deliverable == null) {
            return;
        }
        List<Version> ordered = versions.findByDeliverableIdOrderByVersionNoDesc(deliverable.getId());
        for (int i = 0; i < ordered.size(); i++) {
            Version version = ordered.get(i);
            String fileKey = version.getSubmission().getFileName();
            String entry = i == 0
                    ? base + "/" + item.getPhase() + "/" + fileKey
                    : base + "/" + OLD_FOLDER + "/" + fileKey;
            addEntry(zipOut, storage.resolve(version.getSubmission().getStoredPath()), entry);
        }
    }

    private void writeSummarySheet(XSSFWorkbook workbook, Long projectId) {
        Sheet sheet = workbook.createSheet("제출현황");
        int lastPhaseRow = writePhaseTable(sheet, projectId);
        if (lastPhaseRow < 1) {
            return;
        }
        int overallLabelRow = writeOverallData(sheet, lastPhaseRow + 2, projectId);
        drawPhaseCompletionChart(sheet, lastPhaseRow);
        drawOverallCompletionChart(sheet, overallLabelRow);
    }

    /** 단계별 집계 표를 쓰고 마지막 단계 행 번호를 돌려준다. 항목이 없으면 0. */
    private int writePhaseTable(Sheet sheet, Long projectId) {
        Row header = sheet.createRow(0);
        String[] headers = {"단계", "항목수", "제출", "검토완료", "제출률(%)", "완료율(% 검토완료 기준)"};
        for (int i = 0; i < headers.length; i++) {
            header.createCell(i).setCellValue(headers[i]);
        }
        List<ChecklistItem> allRows = statRowsOf(projectId);
        int rowNo = 1;
        for (Map.Entry<String, List<ChecklistItem>> entry : groupByPhase(allRows).entrySet()) {
            writePhaseRow(sheet.createRow(rowNo++), entry.getKey(), entry.getValue());
        }
        return rowNo - 1;
    }

    private void writePhaseRow(Row row, String phase, List<ChecklistItem> phaseRows) {
        long submitted = phaseRows.stream().filter(ChecklistItem::isReceived).count();
        long confirmed = countStatus(phaseRows, ChecklistItem.Status.CONFIRMED);
        row.createCell(0).setCellValue(phase);
        row.createCell(1).setCellValue(phaseRows.size());
        row.createCell(2).setCellValue((int) submitted);
        row.createCell(3).setCellValue((int) confirmed);
        row.createCell(4).setCellValue(percent(submitted, phaseRows.size()));
        row.createCell(5).setCellValue(percent(confirmed, phaseRows.size()));
    }

    /** 도넛 차트가 참조할 전체 완료율 데이터(검토완료/미완료 건수)를 쓰고 검토완료 행 번호를 돌려준다. */
    private int writeOverallData(Sheet sheet, int startRow, Long projectId) {
        List<ChecklistItem> statRows = statRowsOf(projectId);
        long total = statRows.size();
        long confirmed = countStatus(statRows, ChecklistItem.Status.CONFIRMED);
        writeOverallRow(sheet, startRow, "전체 항목수", total);
        writeOverallRow(sheet, startRow + 1, CONFIRMED_LABEL, confirmed);
        writeOverallRow(sheet, startRow + 2, REMAINING_LABEL, total - confirmed);
        writeOverallRow(sheet, startRow + 3, "전체 완료율(%)", percent(confirmed, total));
        return startRow + 1;
    }

    private void writeOverallRow(Sheet sheet, int rowNo, String label, long value) {
        Row row = sheet.createRow(rowNo);
        row.createCell(0).setCellValue(label);
        row.createCell(1).setCellValue((int) value);
    }

    private List<ChecklistItem> statRowsOf(Long projectId) {
        return items.findByProjectIdOrderByIdAsc(projectId).stream()
                .filter(row -> !row.accumulatesFiles())
                .toList();
    }

    private long countStatus(List<ChecklistItem> rows, ChecklistItem.Status status) {
        return rows.stream().filter(row -> row.getStatus() == status).count();
    }

    /** 대시보드 단계별 완료율 막대와 같은 모양의 가로 막대 차트를 데이터 옆에 그린다. */
    private void drawPhaseCompletionChart(Sheet sheet, int lastPhaseRow) {
        XSSFDrawing drawing = xssfSheet(sheet).createDrawingPatriarch();
        XSSFChart chart = drawing.createChart(chartAnchor(drawing, 0, PHASE_CHART_LAST_ROW));
        chart.setTitleText(PHASE_CHART_TITLE);
        chart.setTitleOverlay(false);
        chart.deleteLegend();
        XDDFBarChartData data = (XDDFBarChartData) chart.createData(ChartTypes.BAR,
                chart.createCategoryAxis(AxisPosition.BOTTOM), chart.createValueAxis(AxisPosition.LEFT));
        data.setBarDirection(BarDirection.BAR);
        data.addSeries(
                XDDFDataSourcesFactory.fromStringCellRange(xssfSheet(sheet), new CellRangeAddress(1, lastPhaseRow, 0, 0)),
                XDDFDataSourcesFactory.fromNumericCellRange(xssfSheet(sheet), new CellRangeAddress(1, lastPhaseRow, 5, 5)));
        // 값 축(index 1)만 0~100으로 고정한다. 분류 축(index 0)은 단계 이름이라 건드리지 않는다.
        chart.setValueRange(1, 0.0, 100.0, null, null);
        chart.plot(data);
        showValueLabels(chart);
        applyChartTextFont(chart.getFormattedTitle());
    }

    /** 대시보드 상단의 전체 완료율 도넛과 같은 모양의 도넛 차트를 그린다. */
    private void drawOverallCompletionChart(Sheet sheet, int confirmedRow) {
        XSSFDrawing drawing = xssfSheet(sheet).createDrawingPatriarch();
        XSSFChart chart = drawing.createChart(chartAnchor(drawing, OVERALL_CHART_FIRST_ROW, OVERALL_CHART_LAST_ROW));
        chart.setTitleText(OVERALL_CHART_TITLE);
        chart.setTitleOverlay(false);
        XDDFDoughnutChartData data = (XDDFDoughnutChartData) chart.createData(ChartTypes.DOUGHNUT, null, null);
        data.addSeries(
                XDDFDataSourcesFactory.fromStringCellRange(xssfSheet(sheet), new CellRangeAddress(confirmedRow, confirmedRow + 1, 0, 0)),
                XDDFDataSourcesFactory.fromNumericCellRange(xssfSheet(sheet), new CellRangeAddress(confirmedRow, confirmedRow + 1, 1, 1)));
        chart.plot(data);
        showPercentLabels(chart);
        applyChartTextFont(chart.getFormattedTitle());
        applyLegendFont(chart);
    }

    /** 도넛 차트 범례(검토완료·미완료)의 폰트를 바꾼다. 범례 서식 텍스트가 없으면 새로 만든다. */
    private void applyLegendFont(XSSFChart chart) {
        XDDFChartLegend legend = chart.getOrAddLegend();
        if (legend.getTextBody() == null) {
            legend.setTextBody(new XDDFTextBody(legend, CTTextBody.Factory.newInstance()));
        }
        // XMLBeans의 set은 전달받은 본문을 복사하므로, 설정된 뒤 반드시 범례가 가진 본문을 다시 감싸 서식을 적용한다
        applyChartTextFont(legend.getTextBody());
    }

    /** 통합 문서 기본 폰트(스타일 0)를 바꾼다. 스타일 지정 없는 모든 셀이 이 폰트를 따른다. */
    private void applyDefaultCellFont(XSSFWorkbook workbook) {
        XSSFFont defaultFont = workbook.getFontAt(0);
        defaultFont.setFontName(FONT_NAME);
    }

    /** 차트 텍스트(제목·범례·레이블)의 기본 폰트를 바꾼다. 빈 텍스트 본문은 문단을 채워 OOXML 스키마 위반(엑셀 복구 사유)을 막는다. */
    private void applyChartTextFont(XDDFTextBody body) {
        if (body == null) {
            return;
        }
        if (body.getParagraphs().isEmpty()) {
            body.initialize();
        }
        for (XDDFTextParagraph paragraph : body.getParagraphs()) {
            XDDFRunProperties properties = paragraph.addDefaultRunProperties();
            properties.setFonts(new XDDFFont[] {
                    new XDDFFont(FontGroup.LATIN, FONT_NAME, null, null, null),
                    new XDDFFont(FontGroup.EAST_ASIAN, FONT_NAME, null, null, null)});
        }
    }

    /** 막대 차트에 값(퍼센트) 데이터 레이블을 표시한다. */
    private void showValueLabels(XSSFChart chart) {
        CTDLbls labels = chart.getCTChart().getPlotArea().getBarChartArray(0).addNewDLbls();
        labels.addNewTxPr();
        writeLabelFlags(labels, false, true);
        applyChartTextFont(new XDDFTextBody(chart, labels.getTxPr()));
    }

    /** 도넛 차트에 비율(%) 데이터 레이블을 표시한다. */
    private void showPercentLabels(XSSFChart chart) {
        CTDLbls labels = chart.getCTChart().getPlotArea().getDoughnutChartArray(0).addNewDLbls();
        labels.addNewNumFmt().setFormatCode("0%");
        labels.addNewTxPr();
        writeLabelFlags(labels, true, false);
        applyChartTextFont(new XDDFTextBody(chart, labels.getTxPr()));
    }

    /** CTDLbls 스키마 순서대로 표시 옵션을 쓴다(showLegendKey→showVal→showCatName→showSerName→showPercent→showBubbleSize). */
    private void writeLabelFlags(CTDLbls labels, boolean showPercent, boolean showValue) {
        labels.addNewShowLegendKey().setVal(false);
        labels.addNewShowVal().setVal(showValue);
        labels.addNewShowCatName().setVal(false);
        labels.addNewShowSerName().setVal(false);
        labels.addNewShowPercent().setVal(showPercent);
        labels.addNewShowBubbleSize().setVal(false);
    }

    private XSSFClientAnchor chartAnchor(XSSFDrawing drawing, int firstRow, int lastRow) {
        return drawing.createAnchor(0, 0, 0, 0, CHART_FIRST_COLUMN, firstRow, CHART_LAST_COLUMN, lastRow);
    }

    private XSSFSheet xssfSheet(Sheet sheet) {
        return (XSSFSheet) sheet;
    }

    private void writeLedgerSheet(XSSFWorkbook workbook, Long projectId) {
        Sheet sheet = workbook.createSheet("산출물 대장");
        Row header = sheet.createRow(0);
        String[] headers = {"단계", "산출물 항목", "필수", "상태", "보완 사유", "최신 버전", "제출 파일명"};
        for (int i = 0; i < headers.length; i++) {
            header.createCell(i).setCellValue(headers[i]);
        }
        int rowNo = 1;
        for (ChecklistItem item : items.findByProjectIdOrderByIdAsc(projectId)) {
            Row row = sheet.createRow(rowNo++);
            row.createCell(0).setCellValue(item.getPhase());
            row.createCell(1).setCellValue(item.getItemName());
            row.createCell(2).setCellValue(item.isRequired() ? "예" : "아니오");
            row.createCell(3).setCellValue(item.getStatus().label());
            row.createCell(4).setCellValue(item.getSupplementReason() == null ? "" : item.getSupplementReason());
            Version latest = latestVersionOf(item.getId());
            row.createCell(5).setCellValue(latest == null ? "" : VersionNaming.format(latest.getVersionNo()));
            row.createCell(6).setCellValue(latest == null ? "" : latest.getSubmission().getFileName());
        }
    }

    private Map<String, List<ChecklistItem>> groupByPhase(List<ChecklistItem> rows) {
        Map<String, List<ChecklistItem>> grouped = new LinkedHashMap<>();
        for (ChecklistItem row : rows) {
            grouped.computeIfAbsent(row.getPhase(), phase -> new ArrayList<>()).add(row);
        }
        return grouped;
    }

    private Version latestVersionOf(Long itemId) {
        var deliverable = deliverables.findByItemId(itemId).orElse(null);
        if (deliverable == null) {
            return null;
        }
        return versions.findFirstByDeliverableIdAndOldFalseOrderByVersionNoDesc(deliverable.getId()).orElse(null);
    }

    private void addEntry(ZipOutputStream zipOut, Path file, String entryName) throws IOException {
        if (!Files.exists(file)) {
            return;
        }
        zipOut.putNextEntry(new ZipEntry(entryName));
        Files.copy(file, zipOut);
        zipOut.closeEntry();
    }

    private ResponseEntity<FileSystemResource> attachment(Path file, String fileName, String mediaType) {
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .contentType(MediaType.parseMediaType(mediaType))
                .body(new FileSystemResource(file));
    }

    private int percent(long part, long total) {
        return total == 0 ? 0 : (int) Math.round(part * 100.0 / total);
    }

    private String sanitize(String name) {
        return name == null ? "project" : name.replaceAll("[\\\\/:*?\"<>|]", "_");
    }
}
