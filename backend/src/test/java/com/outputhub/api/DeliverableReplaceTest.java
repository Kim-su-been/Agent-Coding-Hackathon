package com.outputhub.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.outputhub.domain.ChecklistItem;
import com.outputhub.domain.Deliverable;
import com.outputhub.domain.MatchSuggestion;
import com.outputhub.domain.Project;
import com.outputhub.domain.Submission;
import com.outputhub.domain.Version;
import com.outputhub.service.VersionRegistrationService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;

/** REQ-outputhub-008: 검토완료 항목 재업로드 승인(AC-008-1)과 검토완료가 아닌 행의 거부(AC-008-2 보완). */
@SpringBootTest(webEnvironment = WebEnvironment.NONE)
@Transactional
class DeliverableReplaceTest {

    @Autowired
    DeliverableController deliverableController;
    @Autowired
    VersionRegistrationService versionRegistration;
    @Autowired
    Project.Repository projects;
    @Autowired
    ChecklistItem.Repository items;
    @Autowired
    Submission.Repository submissions;
    @Autowired
    MatchSuggestion.Repository suggestions;
    @Autowired
    Deliverable.Repository deliverables;
    @Autowired
    Version.Repository versions;

    @Test
    @DisplayName("AC-008-1: 검토완료 행에 새 파일을 재업로드 승인하면 상태가 검토 필요(검토전)로 돌아가고 새 버전이 등록된다")
    void replaceConfirmedRevertsStatusAndAddsNewVersion() throws IOException {
        // Given: 검토완료(확정)된 행과 새 파일의 행 연결 제안
        Project project = projects.save(new Project("재업로드 승인 검증", "A수행사"));
        ChecklistItem item = items.save(new ChecklistItem(project, "분석", "요구사항 정의서", true));
        item.transitionTo(ChecklistItem.Status.SUBMITTED);
        items.save(item);
        item.transitionTo(ChecklistItem.Status.CONFIRMED);
        items.save(item);

        Submission v1 = storeSubmission(project, "요구사항 정의서_v1.txt", "v1 본문");
        versionRegistration.register(item, v1, "최초 접수");

        Submission v2 = storeSubmission(project, "요구사항 정의서_v2.txt", "v2 본문");
        MatchSuggestion suggestion = suggestions.save(
                new MatchSuggestion(project, v2, MatchSuggestion.Verdict.LINK, item, "파일명 키워드 \"정의서\""));

        // When: 팝업에서 확인을 눌러 재업로드 승인을 호출한다
        ResponseEntity<?> response = deliverableController.replaceConfirmed(item.getId(),
                new DeliverableController.SupplementRequest(String.valueOf(suggestion.getId())));

        // Then: 상태가 검토 필요(검토전)로 돌아가고 새 파일이 새 버전으로 등록되며 이전 검토완료본은 old로 보존된다
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(new DeliverableController.StatusResponse("검토 필요"));
        ChecklistItem updated = items.findById(item.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(ChecklistItem.Status.SUBMITTED);

        Deliverable deliverable = deliverables.findByItemId(item.getId()).orElseThrow();
        Version latest = versions.findFirstByDeliverableIdAndOldFalseOrderByVersionNoDesc(deliverable.getId())
                .orElseThrow();
        assertThat(latest.getVersionNo()).isEqualTo(11);
        assertThat(latest.getSubmission().getFileName())
                .startsWith("요구사항 정의서_v0.11_").endsWith(".txt");

        Version oldVersion = versions.findByDeliverableIdOrderByVersionNoDesc(deliverable.getId()).stream()
                .filter(Version::isOld)
                .findFirst()
                .orElseThrow();
        assertThat(oldVersion.getSubmission().getFileName())
                .startsWith("요구사항 정의서_v0.1_").endsWith(".txt");
        assertThat(oldVersion.getSubmission().getStoredPath()).contains("old");
        assertThat(suggestions.findById(suggestion.getId()).orElseThrow().getStatus())
                .isEqualTo(MatchSuggestion.Status.ACCEPTED);
    }

    @Test
    @DisplayName("REQ-outputhub-006: 등록된 해시와 같은 접수의 제안은 교체 승인으로도 등록을 막는다")
    void replaceRejectsSameHashAsRegistered() throws IOException {
        // Given: 검토완료(확정) 행에 등록된 버전(해시 sha-dup)과, 같은 해시를 가진 대기 제안
        Project project = projects.save(new Project("동일 해시 교체 거부 검증", "A수행사"));
        ChecklistItem item = items.save(new ChecklistItem(project, "분석", "요구사항 정의서", true));
        item.transitionTo(ChecklistItem.Status.SUBMITTED);
        items.save(item);
        item.transitionTo(ChecklistItem.Status.CONFIRMED);
        items.save(item);

        Submission v1 = storeSubmission(project, "요구사항 정의서_v1.txt", "v1 본문");
        versionRegistration.register(item, v1, "최초 접수");

        // 수락 전에 만들어진 대기 제안이 등록 이후에도 남아 있는 경쟁 상황을 흉내 낸다(같은 해시·다른 파일명)
        Path folder = Files.createTempDirectory("outputhub-replace");
        Path file = Files.createTempFile(folder, "submission-", ".txt");
        Files.writeString(file, "v1 본문");
        Submission dup = submissions.save(new Submission(project, "요구사항 정의서_사본.txt", file.toString(),
                v1.getSha256(), LocalDateTime.now()));
        MatchSuggestion suggestion = suggestions.save(
                new MatchSuggestion(project, dup, MatchSuggestion.Verdict.LINK, item, "파일명 키워드 \"정의서\""));

        // When: 검토완료 행에 교체 승인을 호출한다
        ResponseEntity<?> response = deliverableController.replaceConfirmed(item.getId(),
                new DeliverableController.SupplementRequest(String.valueOf(suggestion.getId())));

        // Then: 409로 막히고 행 상태·최신본·제안은 그대로다
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isEqualTo("동일 해시 파일이 이미 등록되어 있습니다(REQ-outputhub-006 중복)");
        assertThat(items.findById(item.getId()).orElseThrow().getStatus())
                .isEqualTo(ChecklistItem.Status.CONFIRMED);
        assertThat(suggestions.findById(suggestion.getId()).orElseThrow().getStatus())
                .isEqualTo(MatchSuggestion.Status.PENDING);
    }

    @Test
    @DisplayName("AC-008-2: 검토완료가 아닌 행은 재업로드 승인 대상이 아니므로 거부된다")
    void replaceRejectsNonConfirmedItem() throws IOException {
        // Given: 검토 필요(접수) 상태 행과 행 연결 제안
        Project project = projects.save(new Project("재업로드 거부 검증", "A수행사"));
        ChecklistItem item = items.save(new ChecklistItem(project, "분석", "요구사항 정의서", true));
        item.transitionTo(ChecklistItem.Status.SUBMITTED);
        items.save(item);

        Submission v1 = storeSubmission(project, "요구사항 정의서_v1.txt", "v1 본문");
        versionRegistration.register(item, v1, "최초 접수");

        Submission v2 = storeSubmission(project, "요구사항 정의서_v2.txt", "v2 본문");
        MatchSuggestion suggestion = suggestions.save(
                new MatchSuggestion(project, v2, MatchSuggestion.Verdict.LINK, item, "파일명 키워드 \"정의서\""));

        // When: 검토완료가 아닌 행에 재업로드 승인을 호출한다
        ResponseEntity<?> response = deliverableController.replaceConfirmed(item.getId(),
                new DeliverableController.SupplementRequest(String.valueOf(suggestion.getId())));

        // Then: 거부(409)되고 행 상태·최신본은 그대로다
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(items.findById(item.getId()).orElseThrow().getStatus())
                .isEqualTo(ChecklistItem.Status.SUBMITTED);
        Deliverable deliverable = deliverables.findByItemId(item.getId()).orElseThrow();
        assertThat(versions.findFirstByDeliverableIdAndOldFalseOrderByVersionNoDesc(deliverable.getId())
                .orElseThrow().getVersionNo()).isEqualTo(10);
        assertThat(suggestions.findById(suggestion.getId()).orElseThrow().getStatus())
                .isEqualTo(MatchSuggestion.Status.PENDING);
    }

    private Submission storeSubmission(Project project, String fileName, String content) throws IOException {
        Path folder = Files.createTempDirectory("outputhub-replace");
        Path file = Files.createTempFile(folder, "submission-", ".txt");
        Files.writeString(file, content);
        return submissions.save(new Submission(project, fileName, file.toString(), "sha-" + fileName,
                LocalDateTime.now()));
    }
}
