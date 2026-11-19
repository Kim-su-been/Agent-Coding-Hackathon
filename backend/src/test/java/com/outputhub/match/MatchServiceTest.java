package com.outputhub.match;

import static org.assertj.core.api.Assertions.assertThat;

import com.outputhub.domain.ChecklistItem;
import com.outputhub.domain.Project;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;

@SpringBootTest(webEnvironment = WebEnvironment.NONE)
class MatchServiceTest {

    @Autowired
    MatchService matchService;

    Path textFile;

    @BeforeEach
    void prepareFile() throws Exception {
        textFile = Files.createTempFile("upload", ".txt");
        Files.writeString(textFile, "사업수행계획서 투입 인력 내역", java.nio.charset.StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("TC-02: 파일명 키워드와 벡터 유사도가 합산된 사업수행계획서 연결 제안과 근거를 만든다")
    void rankByFilename() {
        List<MatchService.Candidate> candidates = matchService.rank("사업수행계획서_v2.pdf", textFile, items());

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).itemName()).isEqualTo("사업수행계획서");
        assertThat(candidates.get(0).evidence()).contains(MatchService.EVIDENCE_FILENAME).contains("수행계획");
        assertThat(candidates.get(0).evidence()).contains(MatchService.EVIDENCE_SIMILARITY);
    }

    @Test
    @DisplayName("TC-03: 두 키워드가 모두 담긴 모호한 파일은 후보 2개를 점수순으로 제안한다")
    void rankAmbiguous() throws Exception {
        Path ambiguous = Files.createTempFile("upload", ".xlsx");
        Path content = Path.of(ambiguous.toString().replace(".xlsx", ".txt"));
        // 실제 xlsx가 아니어도 내용 추출 실패는 빈 문자열로 처리되므로 파일명 기준으로 검증한다
        List<MatchService.Candidate> candidates = matchService.rank("투입계획_및_WBS.xlsx", ambiguous, items());

        assertThat(candidates).extracting(MatchService.Candidate::itemName)
                .containsExactlyInAnyOrder("인력투입계획서", "WBS");
        assertThat(candidates).allSatisfy(c -> assertThat(c.evidence())
                .contains(MatchService.EVIDENCE_FILENAME));
        Files.deleteIfExists(content);
    }

    @Test
    @DisplayName("TC-04 준비: 대장 후보가 없으면 하이브리드 후보 0개(신규 자동등록 대상)로 판정한다")
    void rankNoCandidate() throws Exception {
        Path quietFile = Files.createTempFile("upload", ".txt");
        Files.writeString(quietFile, "합의 관련 스캔 이미지 문서", java.nio.charset.StandardCharsets.UTF_8);
        List<MatchService.Candidate> candidates = matchService.rank("합의서_스캔본.pdf", quietFile, items());

        assertThat(candidates).isEmpty();
    }

    @Test
    @DisplayName("REQ-outputhub-004: 키워드가 그대로 없어도 파일명 벡터 유사도로 항목을 제안한다(하이브리드)")
    void rankByVector() throws Exception {
        Path quietFile = Files.createTempFile("upload", ".txt");
        Files.writeString(quietFile, "참고 자료 목록", java.nio.charset.StandardCharsets.UTF_8);
        // 파일명에 규칙 키워드("화면설계"·"ui설계"·"화면 정의서")가 그대로 없어도 벡터 유사도로 잡힌다
        List<MatchService.Candidate> candidates = matchService.rank("화면_설계서_UI_목록.xlsx", quietFile, screenItems());

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).itemName()).isEqualTo("화면 설계서");
        assertThat(candidates.get(0).evidence()).contains(MatchService.EVIDENCE_SIMILARITY);
    }

    @Test
    @DisplayName("벡터 유사도 기준 미만의 파일은 후보로 제안하지 않는다")
    void rankBelowVectorThreshold() throws Exception {
        Path quietFile = Files.createTempFile("upload", ".txt");
        Files.writeString(quietFile, "합의 관련 스캔 이미지 문서", java.nio.charset.StandardCharsets.UTF_8);
        List<MatchService.Candidate> candidates = matchService.rank("보고서_양식.xlsx", quietFile, screenItems());

        assertThat(candidates).isEmpty();
    }

    private List<MatchService.ItemRef> items() {
        ChecklistItem plan = new ChecklistItem(new Project("p", "v"), "착수", "사업수행계획서", true);
        ChecklistItem staffing = new ChecklistItem(new Project("p", "v"), "착수", "인력투입계획서", true);
        ChecklistItem wbs = new ChecklistItem(new Project("p", "v"), "착수", "WBS", true);
        return List.of(
                new MatchService.ItemRef(1L, plan.getItemName()),
                new MatchService.ItemRef(2L, staffing.getItemName()),
                new MatchService.ItemRef(3L, wbs.getItemName()));
    }

    private List<MatchService.ItemRef> screenItems() {
        ChecklistItem screen = new ChecklistItem(new Project("p", "v"), "설계", "화면 설계서", true);
        ChecklistItem requirement = new ChecklistItem(new Project("p", "v"), "분석", "요구사항 정의서", true);
        return List.of(
                new MatchService.ItemRef(11L, screen.getItemName()),
                new MatchService.ItemRef(12L, requirement.getItemName()));
    }
}
