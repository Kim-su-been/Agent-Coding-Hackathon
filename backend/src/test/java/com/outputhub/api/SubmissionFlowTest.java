package com.outputhub.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 접수 → 수락 → 재업로드 흐름 시험.
 * REQ-outputhub-006(사용자 요구 2026-11-19): 파일명과 관계없이 해시(내용)를 비교한다.
 * 등록된 내용과 같으면 중복으로 무효 처리하고, 내용이 바뀌면 파일명이 같아도 새 파일로 인식해
 * 새 버전 등록 제안을 만든다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SubmissionFlowTest {

    private static final String PLAN_FILE_NAME = "인력투입계획_v0.9.txt";
    private static final String PLAN_FILE_BODY = "인력투입계획 (v0.9) - 검증용 본문";
    private static final String PLAN_FILE_BODY_V2 = "인력투입계획 (v0.9) - 검증용 본문 (수정)";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    @DisplayName("TC-02·TC-05 연장: 같은 파일명을 다시 올리면 새 버전 등록 제안이 만들어지고, 수락하면 v0.11로 최신본이 갱신된다")
    void sameFileNameReuploadRegistersNewVersion() throws Exception {
        // Given: 착수 템플릿으로 프로젝트와 인력투입계획서 행을 만든다
        MvcResult created = mockMvc.perform(post("/api/projects")
                        .contentType("application/json")
                        .content("{\"name\":\"제안흐름검증-" + System.nanoTime() + "\",\"vendor\":\"검증\",\"templateStage\":\"착수\"}"))
                .andExpect(status().isOk())
                .andReturn();
        long projectId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        // When: 인력투입계획 파일을 업로드한다
        // Then: 행 연결 제안이 만들어진다
        JsonNode first = uploadAndParse(projectId, PLAN_FILE_NAME, PLAN_FILE_BODY);
        assertThat(first.get("verdict").asText()).isEqualTo("행 연결");
        long suggestionId = first.get("suggestionId").asLong();

        // When: 같은 내용 파일을 다시 업로드한다(수락 전)
        // Then: 새 중복 카드가 아니라 대기 중 제안이 그대로 돌아온다(새 접수 행도 만들지 않는다)
        JsonNode second = uploadAndParse(projectId, PLAN_FILE_NAME, PLAN_FILE_BODY);
        assertThat(second.get("verdict").asText()).isEqualTo("행 연결");
        assertThat(second.get("suggestionId").asLong()).isEqualTo(suggestionId);

        // When: 접수함을 다시 열어 대기 제안 목록을 조회한다
        // Then: 방금 제안이 목록에 있다(화면을 벗어나도 사라지지 않는다)
        JsonNode pending = getPendingSuggestions(projectId);
        assertThat(idsOf(pending)).containsExactly(suggestionId);

        // When: 제안을 수락한다
        // Then: 대상 행이 접수 처리된다
        long itemId = first.get("candidates").get(0).get("itemId").asLong();
        mockMvc.perform(post("/api/suggestions/" + suggestionId + "/accept")
                        .queryParam("itemId", String.valueOf(itemId)))
                .andExpect(status().isOk());

        // When: 수락 뒤 같은 내용을 다시 올린다(파일명 무관)
        // Then: 등록된 해시와 같으므로 중복으로 판정된다(REQ-outputhub-006)
        JsonNode duplicate = uploadAndParse(projectId, PLAN_FILE_NAME, PLAN_FILE_BODY);
        assertThat(duplicate.get("verdict").asText()).isEqualTo("중복");
        assertThat(duplicate.hasNonNull("suggestionId")).isFalse();

        // When: 수락 뒤 같은 파일명에 내용을 바꿔 다시 올린다
        // Then: 해시가 다르므로 새 파일로 인식해 새 버전 등록 제안이 만들어진다(다음 버전 v0.11 표기)
        JsonNode third = uploadAndParse(projectId, PLAN_FILE_NAME, PLAN_FILE_BODY_V2);
        assertThat(third.get("verdict").asText()).isEqualTo("행 연결");
        assertThat(third.get("suggestionId").asLong()).isNotEqualTo(suggestionId);
        assertThat(third.get("evidence").asText()).contains("동일 파일명 재접수").contains("v0.11");

        // When: 재접수 제안을 수락한다
        // Then: 같은 행에 새 버전(v0.11)이 등록되고 파일명이 규칙에 맞게 바뀐다(REQ-outputhub-022)
        long reuploadItemId = third.get("candidates").get(0).get("itemId").asLong();
        assertThat(reuploadItemId).isEqualTo(itemId);
        mockMvc.perform(post("/api/suggestions/" + third.get("suggestionId").asLong() + "/accept")
                        .queryParam("itemId", String.valueOf(reuploadItemId)))
                .andExpect(status().isOk());

        // When: v0.11 내용을 다른 파일명으로 다시 올린다
        // Then: 파일명이 달라도 등록된 해시와 같으므로 중복으로 판정된다(REQ-outputhub-006)
        JsonNode duplicateV2 = uploadAndParse(projectId, "인력투입계획_사본.txt", PLAN_FILE_BODY_V2);
        assertThat(duplicateV2.get("verdict").asText()).isEqualTo("중복");

        // When: 대기 제안 목록을 다시 조회한다
        // Then: 수락된 제안은 빠진다
        JsonNode pendingAfter = getPendingSuggestions(projectId);
        assertThat(idsOf(pendingAfter)).doesNotContain(suggestionId);
        assertThat(idsOf(pendingAfter)).doesNotContain(third.get("suggestionId").asLong());
    }

    @Test
    @DisplayName("다른 파일명으로 같은 내용을 올리면 REQ-outputhub-006대로 중복으로 판정한다")
    void sameHashDifferentNameIsDuplicate() throws Exception {
        // Given: 착수 템플릿 프로젝트에 인력투입계획 파일을 접수한다
        MvcResult created = mockMvc.perform(post("/api/projects")
                        .contentType("application/json")
                        .content("{\"name\":\"중복판정검증-" + System.nanoTime() + "\",\"vendor\":\"검증\",\"templateStage\":\"착수\"}"))
                .andExpect(status().isOk())
                .andReturn();
        long projectId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();
        JsonNode first = uploadAndParse(projectId, PLAN_FILE_NAME, PLAN_FILE_BODY);

        // When: 첫 제안을 수락한 뒤 같은 내용을 다른 파일명으로 올린다
        // Then: 중복으로 판정된다(집계 이중 계산 방지). 수락 전이라면 대기 제안 재표시(AC-006-1)가 우선이다
        mockMvc.perform(post("/api/suggestions/" + first.get("suggestionId").asLong() + "/accept")
                        .queryParam("itemId", String.valueOf(
                                first.get("candidates").get(0).get("itemId").asLong())))
                .andExpect(status().isOk());
        JsonNode duplicate = uploadAndParse(projectId, "인력투입계획_사본.txt", PLAN_FILE_BODY);
        assertThat(duplicate.get("verdict").asText()).isEqualTo("중복");

        // Then: 중복 안내는 응답 전용이라 제안 카드로 저장되지 않는다(사용자 요구 2026-11-19)
        assertThat(duplicate.hasNonNull("suggestionId")).isFalse();

        // When: 접수함을 다시 열어 대기 제안 목록을 조회한다
        // Then: 중복 안내는 목록에 나오지 않는다(나갔다가 들어오면 사라진다)
        JsonNode pendingAfterDuplicate = getPendingSuggestions(projectId);
        assertThat(verdictsOf(pendingAfterDuplicate)).doesNotContain("중복");
        assertThat(pendingAfterDuplicate.size()).isZero();
    }

    @Test
    @DisplayName("수락 전 같은 파일명을 다시 올리면 대기 제안을 재사용하고 새 접수를 만들지 않는다(AC-006-1)")
    void reusePendingSuggestionBeforeAccept() throws Exception {
        // Given: 착수 템플릿 프로젝트와 첫 접수
        MvcResult created = mockMvc.perform(post("/api/projects")
                        .contentType("application/json")
                        .content("{\"name\":\"재사용검증-" + System.nanoTime() + "\",\"vendor\":\"검증\",\"templateStage\":\"착수\"}"))
                .andExpect(status().isOk())
                .andReturn();
        long projectId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();
        JsonNode first = uploadAndParse(projectId, PLAN_FILE_NAME, PLAN_FILE_BODY);

        // When: 같은 파일명을 다시 올린다(수락 전)
        // Then: 새 중복 카드가 아니라 대기 중 제안이 그대로 돌아온다
        JsonNode second = uploadAndParse(projectId, PLAN_FILE_NAME, PLAN_FILE_BODY);
        assertThat(second.get("verdict").asText()).isEqualTo("행 연결");
        assertThat(second.get("suggestionId").asLong()).isEqualTo(first.get("suggestionId").asLong());

        // When: 대기 제안 목록을 조회한다
        // Then: 제안이 하나뿐이다(새 접수·제안을 만들지 않는다)
        JsonNode pending = getPendingSuggestions(projectId);
        assertThat(idsOf(pending)).containsExactly(first.get("suggestionId").asLong());
    }

    @Test
    @DisplayName("수락 시점에 등록된 해시와 같은 접수는 등록을 막는다(REQ-outputhub-006)")
    void acceptTimeSameHashRegistrationIsBlocked() throws Exception {
        // Given: 같은 내용(해시)을 다른 파일명으로 두 개 접수해 대기 제안 2개를 만든다
        MvcResult created = mockMvc.perform(post("/api/projects")
                        .contentType("application/json")
                        .content("{\"name\":\"수락차단검증-" + System.nanoTime() + "\",\"vendor\":\"검증\",\"templateStage\":\"착수\"}"))
                .andExpect(status().isOk())
                .andReturn();
        long projectId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        JsonNode first = uploadAndParse(projectId, PLAN_FILE_NAME, PLAN_FILE_BODY);
        JsonNode second = uploadAndParse(projectId, "인력투입계획_사본.txt", PLAN_FILE_BODY);
        assertThat(first.get("verdict").asText()).isEqualTo("행 연결");
        assertThat(second.get("verdict").asText()).isEqualTo("행 연결");

        // When: 첫 제안을 수락해 등록한 뒤 두 번째 제안을 수락한다
        // Then: 두 번째 접수는 등록된 해시와 같으므로 409로 막힌다(등록 이후 내용 조작·경쟁 수락 방지)
        long firstItemId = first.get("candidates").get(0).get("itemId").asLong();
        mockMvc.perform(post("/api/suggestions/" + first.get("suggestionId").asLong() + "/accept")
                        .queryParam("itemId", String.valueOf(firstItemId)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/suggestions/" + second.get("suggestionId").asLong() + "/accept")
                        .queryParam("itemId", String.valueOf(firstItemId)))
                .andExpect(status().isConflict())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                        .contains("동일 해시 파일이 이미 등록되어 있습니다"));

        // Then: 막힌 제안은 여전히 대기 중이고 등록(버전)되지 않았다
        JsonNode pending = getPendingSuggestions(projectId);
        assertThat(idsOf(pending)).containsExactly(second.get("suggestionId").asLong());
    }

    @Test
    @DisplayName("다중 파일 접수 중 하나를 수락해도 나머지 대기 제안은 목록에 남는다")
    void keepRemainingSuggestionsAfterAcceptingOne() throws Exception {
        // Given: 착수 템플릿 프로젝트와 서로 다른 파일 2개 접수
        MvcResult created = mockMvc.perform(post("/api/projects")
                        .contentType("application/json")
                        .content("{\"name\":\"다중수락검증-" + System.nanoTime() + "\",\"vendor\":\"검증\",\"templateStage\":\"착수\"}"))
                .andExpect(status().isOk())
                .andReturn();
        long projectId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(multipart("/api/projects/" + projectId + "/submissions")
                        .file(file("WBS_v1.txt", "WBS (v1) - 검증용 본문"))
                        .file(file("착수보고서_v1.txt", "착수보고서 (v1) - 검증용 본문")))
                .andExpect(status().isOk());

        // When: 대기 제안 목록을 조회한다
        // Then: 두 제안 모두 대기 중이다
        JsonNode pending = getPendingSuggestions(projectId);
        assertThat(pending.size()).isEqualTo(2);

        // When: 첫 제안만 수락한다
        // Then: 수락이 성공한다
        long firstSuggestionId = pending.get(0).get("suggestionId").asLong();
        long firstItemId = pending.get(0).get("candidates").get(0).get("itemId").asLong();
        mockMvc.perform(post("/api/suggestions/" + firstSuggestionId + "/accept")
                        .queryParam("itemId", String.valueOf(firstItemId)))
                .andExpect(status().isOk());

        // When: 대기 제안 목록을 다시 조회한다
        // Then: 수락한 제안은 빠지고 나머지 한 건이 남아 다음 수락을 이어서 할 수 있다
        JsonNode remaining = getPendingSuggestions(projectId);
        assertThat(idsOf(remaining)).hasSize(1);
        assertThat(idsOf(remaining)).doesNotContain(firstSuggestionId);
    }

    private JsonNode uploadAndParse(long projectId, String fileName, String body) throws Exception {
        MvcResult result = mockMvc.perform(multipart("/api/projects/" + projectId + "/submissions")
                        .file(file(fileName, body)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode list = objectMapper.readTree(result.getResponse().getContentAsString());
        return list.get(list.size() - 1);
    }

    private JsonNode getPendingSuggestions(long projectId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/projects/" + projectId + "/suggestions"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private List<Long> idsOf(JsonNode suggestions) {
        List<Long> ids = new ArrayList<>();
        for (JsonNode node : suggestions) {
            ids.add(node.get("suggestionId").asLong());
        }
        return ids;
    }

    private List<String> verdictsOf(JsonNode suggestions) {
        List<String> verdicts = new ArrayList<>();
        for (JsonNode node : suggestions) {
            verdicts.add(node.get("verdict").asText());
        }
        return verdicts;
    }

    private MockMultipartFile file(String fileName, String body) {
        return new MockMultipartFile("files", fileName, "text/plain", body.getBytes(StandardCharsets.UTF_8));
    }
}
