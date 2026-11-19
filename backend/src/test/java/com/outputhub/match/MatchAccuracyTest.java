package com.outputhub.match;

import static org.assertj.core.api.Assertions.assertThat;

import com.outputhub.service.TemplateLoader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * REQ-outputhub-004: 임의 파일 100개에 대해 기대 매핑과 서비스 판정의 일치 여부를 샘다.
 * 서비스 판정은 업로드 제안과 같다: 첫 후보, 후보가 없으면 신규(자동등록).
 * 기대효과 "검증 가능성"이 인용하는 매핑 일치율 수치의 재현 근거다.
 */
class MatchAccuracyTest {

    private static final String NEW_REGISTRATION = "신규(자동등록)";
    private static final double MATCH_FLOOR_PERCENT = 90.0;
    private static final int EXPECTED_CASE_COUNT = 100;

    private final MatchService matchService = new MatchService();
    private final List<MatchService.ItemRef> registry = registry();

    @Test
    @DisplayName("임의 파일 100개에서 기대 매핑과 서비스 판정 일치율이 실측 기준(90%) 이상이다")
    void measureMappingAgreement() throws IOException {
        List<MappingCase> cases = allCases();
        List<Verdict> verdicts = new ArrayList<>();
        for (MappingCase mappingCase : cases) {
            verdicts.add(decide(mappingCase));
        }
        Accuracy accuracy = summarize(verdicts);
        printCases(verdicts);
        printReport(accuracy);

        assertThat(verdicts).hasSize(EXPECTED_CASE_COUNT);
        assertThat(accuracy.matchPercent())
                .as("기대 매핑과 판정 일치율 실측: %.1f%% (%d/%d)", accuracy.matchPercent(),
                        accuracy.matchedCount(), accuracy.total())
                .isGreaterThanOrEqualTo(MATCH_FLOOR_PERCENT);
    }

    /** 접수 판정과 같은 규칙을 적용한다: 후보 없으면 신규, 있으면 첫 후보가 서비스 판정이다. */
    private Verdict decide(MappingCase mappingCase) throws IOException {
        Path stored = writeStoredFile(mappingCase);
        try {
            List<MatchService.Candidate> candidates =
                    matchService.rank(mappingCase.fileName(), stored, registry);
            return judge(mappingCase, candidates);
        } finally {
            Files.deleteIfExists(stored);
        }
    }

    private Verdict judge(MappingCase mappingCase, List<MatchService.Candidate> candidates) {
        if (candidates.isEmpty()) {
            return new Verdict(mappingCase, NEW_REGISTRATION, "대장 후보 없음");
        }
        MatchService.Candidate top = candidates.get(0);
        return new Verdict(mappingCase, top.itemName(), top.evidence());
    }

    private Accuracy summarize(List<Verdict> verdicts) {
        long matchedCount = verdicts.stream().filter(Verdict::matchesExpected).count();
        List<Verdict> unrelatedFiles = verdicts.stream()
                .filter(verdict -> verdict.mappingCase().expectedItem().isEmpty()).toList();
        long newHits = unrelatedFiles.stream()
                .filter(verdict -> verdict.decidedItem().equals(NEW_REGISTRATION)).count();
        return new Accuracy(verdicts.size(), matchedCount, newHits, unrelatedFiles.size());
    }

    private void printReport(Accuracy accuracy) {
        System.out.printf("매핑 판정 실측: 전체 %d · 기대 매핑과 판정 일치 %d(%.1f%%) · 무관 문서 신규 판정 %d/%d%n",
                accuracy.total(), accuracy.matchedCount(), accuracy.matchPercent(),
                accuracy.newHits(), accuracy.newCount());
    }

    /** 측정 근거를 파일명 단위로 남긴다: 판정|파일명|기대|판정 결과|근거. */
    private void printCases(List<Verdict> verdicts) {
        for (Verdict verdict : verdicts) {
            System.out.printf("CASE|%s|%s|%s|%s|%s%n", verdict.matchesExpected() ? "일치" : "불일치",
                    verdict.mappingCase().fileName(), verdict.expectedLabel(),
                    verdict.decidedItem(), verdict.evidence());
        }
    }

    /** xlsx 는 시트 헤더를 실제로 쓰고(내용 키워드 경로), 나머지 확장자는 내용 추출이 빈 값이다. */
    private Path writeStoredFile(MappingCase mappingCase) throws IOException {
        Path stored = Files.createTempFile("accuracy-sample", mappingCase.fileName()
                .substring(mappingCase.fileName().lastIndexOf('.')));
        if (mappingCase.fileName().endsWith(".xlsx")) {
            writeWorkbook(stored, mappingCase.headers());
            return stored;
        }
        Files.write(stored, new byte[0]);
        return stored;
    }

    private void writeWorkbook(Path stored, String headers) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             FileOutputStream out = new FileOutputStream(stored.toFile())) {
            Row header = workbook.createSheet("산출물").createRow(0);
            String[] columns = headers.split(",");
            for (int index = 0; index < columns.length; index++) {
                header.createCell(index).setCellValue(columns[index].trim());
            }
            workbook.write(out);
        }
    }

    private List<MappingCase> allCases() {
        List<MappingCase> cases = new ArrayList<>();
        for (String[] row : POSITIVE_DATA) {
            cases.add(new MappingCase(row[0], row[1], row[2]));
        }
        for (String[] row : NEGATIVE_DATA) {
            cases.add(new MappingCase("", row[0], row[1]));
        }
        return cases;
    }

    private List<MatchService.ItemRef> registry() {
        List<MatchService.ItemRef> refs = new ArrayList<>();
        long id = 1;
        for (TemplateLoader.TemplateItem template : new TemplateLoader().load(null)) {
            refs.add(new MatchService.ItemRef(id++, template.name()));
        }
        return refs;
    }

    /** 임의 생성 파일명 집합. {기대 항목, 파일명, xlsx 헤더(빈 문자열=내용 추출 불가 형식)}. */
    private static final String[][] POSITIVE_DATA = {
            {"사업수행계획서", "AI.NSP_사업수행계획서_v1.0.xlsx", "부문, 역할, 월별 투입 인력, 산출물"},
            {"사업수행계획서", "[3차수] 사업수행계획서(수정본).hwp", ""},
            {"사업수행계획서", "사업 수행 계획서_초안.pdf", ""},
            {"사업수행계획서", "과제추진_수행계획_기술서.docx", ""},
            {"WBS", "WBS_3차수_v1.2.xlsx", "WBS코드, 작업명, 담당자, 시작일, 종료일"},
            {"WBS", "프로젝트_WBS_전체.xlsx", ""},
            {"WBS", "W.B.S_기준일정.xlsx", ""},
            {"WBS", "업무분해_일정표.xlsx", ""},
            {"인력투입계획서", "인력투입계획서_확정.xlsx", "투입 인력, 직무, MM, 투입시작, 투입종료"},
            {"인력투입계획서", "투입계획_인력현황.xlsx", ""},
            {"인력투입계획서", "투입 인력 내역.xlsx", ""},
            {"인력투입계획서", "투입인력계획표.xlsx", ""},
            {"착수보고서", "착수보고서_발표자료.pptx", ""},
            {"착수보고서", "[AI.NP] 착수보고_최종.pdf", ""},
            {"착수보고서", "착수미팅_보고자료.pptx", ""},
            {"착수보고서", "킥오프보고서.hwp", ""},
            {"요구사항 정의서", "요구사항정의서_v1.0.docx", ""},
            {"요구사항 정의서", "요구사항 정의서(기능비교).xlsx", "요구사항ID, 구분, 상세설명, 우선순위"},
            {"요구사항 정의서", "요구사항_정의_내역서.docx", ""},
            {"요구사항 정의서", "요구사항정의서_최종본.hwp", ""},
            {"요구사항 추적표", "요구사항추적표_v1.xlsx", "요구사항ID, 설계서, 테스트케이스, 검증여부"},
            {"요구사항 추적표", "요구사항_추적표(수정).xlsx", ""},
            {"요구사항 추적표", "요구사항_추적_관리대장.xlsx", ""},
            {"요구사항 추적표", "추적표_체계화_내역.pdf", ""},
            {"시스템 구성도", "시스템구성도_전체.pdf", ""},
            {"시스템 구성도", "인프라_구성도_v2.pptx", ""},
            {"시스템 구성도", "시스템_구성_참고그림.png", ""},
            {"시스템 구성도", "시스템구성도_상세내역.xlsx", "영역, 구성요소, 위치, 비고"},
            {"기능 설계서", "기능설계서_v1.0.hwp", ""},
            {"기능 설계서", "기능설계_상세내역.xlsx", "기능ID, 기능명, 처리흐름, 검증기준"},
            {"기능 설계서", "기능 정의서(화면별).xlsx", "기능ID, 기능명, 설명"},
            {"기능 설계서", "기능별_설계서_목록.pdf", ""},
            {"화면 설계서", "화면설계서_v2.0.xlsx", "화면ID, 화면명, UI구성, 이벤트"},
            {"화면 설계서", "UI설계서_목록.xlsx", ""},
            {"화면 설계서", "화면 정의서_초안.hwp", ""},
            {"화면 설계서", "화면흐름_및_설계내역.pdf", ""},
            {"DB 설계서(ERD)", "DB설계서_ERD_v1.xlsx", "테이블명, 컬럼명, 데이터타입, PK여부"},
            {"DB 설계서(ERD)", "데이터베이스설계서.hwp", ""},
            {"DB 설계서(ERD)", "ER-D_개념설계.pdf", ""},
            {"DB 설계서(ERD)", "DB설계_테이블내역.xlsx", "테이블, 컬럼, 인덱스"},
            {"인터페이스 정의서", "인터페이스정의서_IF목록.xlsx", "IF ID, 전송주기, 송신시스템, 수신시스템"},
            {"인터페이스 정의서", "인터페이스_정의서(대외연계).docx", ""},
            {"인터페이스 정의서", "대외_인터페이스_현황.pdf", ""},
            {"인터페이스 정의서", "인터페이스IF정의서_전송명세.xlsx", "IF ID, 연계방식, 주기"},
            {"단위테스트 결과서", "단위테스트결과서_v1.xlsx", "테스트ID, 입력값, 기대값, 판정"},
            {"단위테스트 결과서", "단위 시험 결과표.hwp", ""},
            {"단위테스트 결과서", "단위-테스트_결과.pdf", ""},
            {"단위테스트 결과서", "클래스별_단위테스트_수행내역.xlsx", "클래스명, 메서드, 결과"},
            {"소스/빌드 안내서", "소스빌드안내서_v1.docx", ""},
            {"소스/빌드 안내서", "빌드_및_배포안내.pptx", ""},
            {"소스/빌드 안내서", "소스_구조_및_빌드가이드.pdf", ""},
            {"소스/빌드 안내서", "리포지터리_안내서.pdf", ""},
            {"테스트 계획서", "테스트계획서_v1.xlsx", "테스트항목, 범위, 일정, 담당자"},
            {"테스트 계획서", "시험계획_수립내역.hwp", ""},
            {"테스트 계획서", "테스트-계획서_기본.pdf", ""},
            {"테스트 계획서", "통합_시험_계획서.docx", ""},
            {"통합테스트 시나리오", "통합테스트시나리오_v1.xlsx", "시나리오ID, 사전조건, 절차, 기대결과"},
            {"통합테스트 시나리오", "시나리오_작성표.hwp", ""},
            {"통합테스트 시나리오", "통합-테스트_시나리오.pdf", ""},
            {"통합테스트 시나리오", "업무별_시나리오_모음.docx", ""},
            {"통합테스트 결과서", "통합테스트결과서_v1.xlsx", "테스트ID, 항목, 결과, 비고"},
            {"통합테스트 결과서", "테스트결과보고서.hwp", ""},
            {"통합테스트 결과서", "통합-테스트_결과보고.pdf", ""},
            {"통합테스트 결과서", "통합테스트결과_요약.xlsx", ""},
            {"보안/점검 조치 결과서", "보안점검조치결과서.xlsx", "점검항목, 취약점, 조치내역, 조치일"},
            {"보안/점검 조치 결과서", "취약점_조치내역.pdf", ""},
            {"보안/점검 조치 결과서", "모의침투_점검_결과.xlsx", "대상, 발견사항, 조치"},
            {"보안/점검 조치 결과서", "보안-점검_조치결과.hwp", ""},
            {"사용자 매뉴얼", "사용자매뉴얼_v1.0.pdf", ""},
            {"사용자 매뉴얼", "사용자 안내서_화면중심.hwp", ""},
            {"사용자 매뉴얼", "사용자-메뉴얼_온보딩.pdf", ""},
            {"사용자 매뉴얼", "최종사용자_교재.docx", ""},
            {"운영자 매뉴얼", "운영자매뉴얼_v1.pdf", ""},
            {"운영자 매뉴얼", "운영 매뉴얼_서버.docx", ""},
            {"운영자 매뉴얼", "운영자-안내서.pdf", ""},
            {"운영자 매뉴얼", "운영자메뉴얼_장애대응.pdf", ""},
            {"운영 인수인계서", "운영인수인계서_최종.hwp", ""},
            {"운영 인수인계서", "인수인계_체크리스트.xlsx", "인수인계항목, 인수자료, 담당자, 완료여부"},
            {"운영 인수인계서", "인수-인계_자료목록.pdf", ""},
            {"운영 인수인계서", "시스템_인계서_양식.hwp", ""},
            {"종료 보고서", "종료보고서_최종.pptx", ""},
            {"종료 보고서", "프로젝트_종료_보고.pdf", ""},
            {"종료 보고서", "완료보고서.pdf", ""},
            {"종료 보고서", "프로젝트종료보고자료.pptx", ""},
            {"검수 확인서", "검수확인서_서명본.pdf", ""},
            {"검수 확인서", "검수_확인서_2차.hwp", ""},
            {"검수 확인서", "검수-확인서(최종).docx", ""},
            {"검수 확인서", "납품검수_확인서류.pdf", ""},
            {"주간보고서", "주간보고서_46주차.xlsx", "금주이슈, 차주계획, 리스크"},
            {"주간보고서", "주간 보고_1103.pptx", ""},
            {"주간보고서", "주간-활동보고_사진포함.pdf", ""},
            {"주간보고서", "46주차_주간활동보고서.docx", ""},
    };

    /** 대장 후보가 없어야 할 임의 파일. {파일명, xlsx 헤더}. */
    private static final String[][] NEGATIVE_DATA = {
            {"회의록_20260112.docx", ""},
            {"연차휴가계획_2026.xlsx", "휴가일, 인원, 비고"},
            {"식대_정산_양식.xlsx", "사번, 금액"},
            {"출퇴근_집계_11월.xlsx", ""},
            {"비품_구매요청서.docx", ""},
            {"명절_선물_수령확인.pdf", ""},
            {"계약서_초안.hwp", ""},
            {"프로젝트_폴더구조.png", ""},
    };

    private record MappingCase(String expectedItem, String fileName, String headers) {
    }

    private record Verdict(MappingCase mappingCase, String decidedItem, String evidence) {
        boolean matchesExpected() {
            return decidedItem.equals(expectedLabel());
        }

        private String expectedLabel() {
            String expected = mappingCase.expectedItem();
            return expected.isEmpty() ? NEW_REGISTRATION : expected;
        }
    }

    private record Accuracy(long total, long matchedCount, long newHits, long newCount) {
        double matchPercent() {
            return total == 0 ? 0.0 : matchedCount * 100.0 / total;
        }
    }
}
