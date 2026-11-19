package com.outputhub.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.outputhub.domain.ChecklistItem;
import com.outputhub.domain.Project;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.xmlbeans.XmlException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.ResponseEntity;

/** 엑셀 내보내기 시험: 대시보드와 같은 완료율 차트(REQ-outputhub-012)와 최종산출물 목록 시트를 확인한다. */
@SpringBootTest(webEnvironment = WebEnvironment.NONE)
class ExportDashboardTest {

    @Autowired
    Project.Repository projects;
    @Autowired
    ChecklistItem.Repository items;
    @Autowired
    ExportController exportController;

    @Test
    @DisplayName("엑셀 내보내기: 전체 완료율 도넛·단계별 완료율 막대 차트와 산출물 대장이 담긴다")
    void exportsChartsAndLedger() throws IOException {
        // Given: 검토완료 1종(착수)과 검토 필요 1종(분석)이 있는 차수
        Project project = projects.save(new Project("엑셀 내보내기 검증", "A수행사"));
        ChecklistItem confirmed = items.save(new ChecklistItem(project, "착수", "사업수행계획서", true));
        confirmed.transitionTo(ChecklistItem.Status.SUBMITTED);
        confirmed.transitionTo(ChecklistItem.Status.CONFIRMED);
        items.save(confirmed);
        ChecklistItem pending = items.save(new ChecklistItem(project, "분석", "요구사항 정의서", true));
        pending.transitionTo(ChecklistItem.Status.SUBMITTED);
        items.save(pending);

        // When: 대시보드 엑셀 내보내기를 실행한다
        ResponseEntity<FileSystemResource> response = exportController.exportDashboard(project.getId());

        // Then: 차트 2개가 들어있고, 제출현황·전체 완료율 데이터·산출물 대장이 모두 채워진다
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        List<String> entries = readZipEntries(response.getBody());
        long chartCount = entries.stream()
                .filter(name -> name.matches("xl/charts/chart\\d+\\.xml"))
                .count();
        assertThat(chartCount).isEqualTo(2);

        try (XSSFWorkbook workbook = new XSSFWorkbook(
                new FileInputStream(response.getBody().getFile()))) {
            // 폰트: 통합 문서 기본 폰트가 맑은 고딕인지 확인한다(사용자 요구 2026-11-19)
            assertThat(workbook.getFontAt(0).getFontName()).isEqualTo("맑은 고딕");

            Sheet summary = workbook.getSheet("제출현황");
            assertThat(summary.getRow(0).getCell(3).getStringCellValue()).isEqualTo("검토완료");
            assertThat(summary.getRow(0).getCell(5).getStringCellValue()).contains("완료율");
            assertThat(summary.getRow(5).getCell(0).getStringCellValue()).isEqualTo("검토완료");
            assertThat(summary.getRow(5).getCell(1).getNumericCellValue()).isEqualTo(1);
            assertThat(summary.getRow(6).getCell(0).getStringCellValue()).isEqualTo("미완료");
            assertThat(summary.getRow(7).getCell(1).getNumericCellValue()).isEqualTo(50);
            Sheet ledger = workbook.getSheet("산출물 대장");
            assertThat(ledger).isNotNull();
            assertThat(ledger.getRow(1).getCell(1).getStringCellValue()).isEqualTo("사업수행계획서");
            assertThat(ledger.getRow(2).getCell(1).getStringCellValue()).isEqualTo("요구사항 정의서");
        }

        // 퍼센트 표시: 막대 차트는 값, 도넛 차트는 비율(%) 레이블이 켜져 있는지 확인한다
        List<String> chartXmls = readChartXml(response.getBody());
        assertThat(chartXmls).anySatisfy(xml -> assertThat(xml).contains("showVal val=\"true\""));
        assertThat(chartXmls).anySatisfy(xml -> assertThat(xml).contains("showPercent val=\"true\""));

        // 스키마 검증: 빈 <c:txPr/> 같은 위반이 있으면 엑셀이 복구하면서 차트를 지우므로 모든 텍스트 본문이 bodyPr·문단을 갖는다
        for (String xml : chartXmls) {
            for (String textBody : textBodiesOf(xml)) {
                assertThat(textBody)
                        .as("차트 텍스트 본문이 bodyPr·문단을 갖는지")
                        .contains("bodyPr")
                        .contains("<a:p");
            }
        }
    }

    private List<String> textBodiesOf(String chartXml) {
        List<String> bodies = new ArrayList<>();
        Matcher matcher = Pattern.compile("<c:txPr(?:/>|>.*?</c:txPr>)", Pattern.DOTALL)
                .matcher(chartXml);
        while (matcher.find()) {
            bodies.add(matcher.group());
        }
        return bodies;
    }

    private List<String> readZipEntries(FileSystemResource body) throws IOException {
        List<String> entries = new ArrayList<>();
        try (ZipInputStream zip = new ZipInputStream(new FileInputStream(body.getFile()))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entries.add(entry.getName());
            }
        }
        return entries;
    }

    private List<String> readChartXml(FileSystemResource body) throws IOException {
        List<String> chartXmls = new ArrayList<>();
        try (ZipInputStream zip = new ZipInputStream(new FileInputStream(body.getFile()))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.getName().matches("xl/charts/chart\\d+\\.xml")) {
                    chartXmls.add(new String(zip.readAllBytes(), StandardCharsets.UTF_8));
                }
            }
        }
        return chartXmls;
    }
}
