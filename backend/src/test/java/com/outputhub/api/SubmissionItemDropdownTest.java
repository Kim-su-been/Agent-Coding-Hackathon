package com.outputhub.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * REQ-outputhub-021(항목 드롭다운 지정 접수)·REQ-outputhub-023(검토대기 카드 드롭다운)과
 * REQ-outputhub-022(수락·검토완료 파일명 자동 부여) 흐름 시험.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SubmissionItemDropdownTest {

    private static final String PLAN_ITEM_NAME = "사업수행계획서";
    private static final String PLAN_FILE_NAME = "사업수행계획서_제출본.pdf";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    @DisplayName("TC-13: 항목 드롭다운으로 지정해 업로드하면 그 행으로 연결 제안되고, 수락 시 {항목}_v0.1_{날짜} 파일명으로 등록된다")
    void uploadWithItemDropdownAcceptsToChosenRow() throws Exception {
        // Given: 착수 템플릿 프로젝트와 사업수행계획서 행
        long projectId = createProject();
        long planItemId = findItemIdByName(projectId, PLAN_ITEM_NAME);

        // When: 드롭다운으로 사업수행계획서를 지정해 파일을 업로드한다
        JsonNode suggestion = uploadWithTarget(projectId, planItemId);

        // Then: 규칙 매칭 대신 지정한 행으로 연결 제안된다(근거에 직접 선택 표시)
        assertThat(suggestion.get("verdict").asText()).isEqualTo("행 연결");
        assertThat(suggestion.get("candidates").get(0).get("itemId").asLong()).isEqualTo(planItemId);
        assertThat(suggestion.get("evidence").asText()).contains("직접 선택");

        // When: 제안을 수락한다
        mockMvc.perform(post("/api/suggestions/" + suggestion.get("suggestionId").asLong() + "/accept")
                        .queryParam("itemId", String.valueOf(planItemId)))
                .andExpect(status().isOk());

        // Then: 최신 버전 표기 0.1, 파일명 {항목}_v0.1_{오늘 날짜}.pdf 다
        JsonNode latestVersion = getItemDetail(planItemId).get("versions").get(0);
        assertThat(latestVersion.get("versionLabel").asText()).isEqualTo("0.1");
        assertThat(latestVersion.get("fileName").asText())
                .startsWith(PLAN_ITEM_NAME + "_v0.1_").endsWith(".pdf");
    }

    @Test
    @DisplayName("TC-14: 검토완료 처리 시 최신본 파일명이 {항목}_v1.0 으로 바뀌고 감사 로그에 기록된다")
    void confirmRenamesLatestToV1() throws Exception {
        // Given: 드롭다운 지정 업로드 → 수락까지 완료된 행
        long projectId = createProject();
        long planItemId = findItemIdByName(projectId, PLAN_ITEM_NAME);
        JsonNode suggestion = uploadWithTarget(projectId, planItemId);
        mockMvc.perform(post("/api/suggestions/" + suggestion.get("suggestionId").asLong() + "/accept")
                        .queryParam("itemId", String.valueOf(planItemId)))
                .andExpect(status().isOk());

        // When: 검토완료를 처리한다
        mockMvc.perform(post("/api/deliverables/" + planItemId + "/confirm"))
                .andExpect(status().isOk());

        // Then: 최신본 파일명이 {항목}_v1.0.pdf 로 바뀌고 확정 감사 로그에도 남는다
        JsonNode detail = getItemDetail(planItemId);
        assertThat(detail.get("versions").get(0).get("fileName").asText())
                .isEqualTo(PLAN_ITEM_NAME + "_v1.0.pdf");
        assertThat(hasConfirmAuditWithV1Name(detail)).isTrue();
    }

    @Test
    @DisplayName("TC-17: 검토대기 카드 드롭다운에서 다른 행을 골라 수락하면 그 행으로 접수된다(REQ-outputhub-025)")
    void acceptWithRetargetedRowFromCardDropdown() throws Exception {
        // Given: 착수 템플릿 프로젝트, 사업수행계획서 파일 업로드 → 사업수행계획서 행 연결 제안
        long projectId = createProject();
        long planItemId = findItemIdByName(projectId, PLAN_ITEM_NAME);
        long wbsItemId = findItemIdByName(projectId, "WBS");
        JsonNode suggestion = uploadWithoutTarget(projectId);

        // When: 카드 드롭다운에서 WBS 행을 골라 수락한다(제안된 행 대신)
        mockMvc.perform(post("/api/suggestions/" + suggestion.get("suggestionId").asLong() + "/accept")
                        .queryParam("itemId", String.valueOf(wbsItemId)))
                .andExpect(status().isOk());

        // Then: WBS 행만 접수되고 파일명이 WBS 규칙으로 부여된다. 사업수행계획서 행은 그대로다
        JsonNode wbsDetail = getItemDetail(wbsItemId);
        assertThat(wbsDetail.get("status").asText()).isEqualTo("검토 필요");
        assertThat(wbsDetail.get("versions").get(0).get("fileName").asText())
                .startsWith("WBS_v0.1_").endsWith(".pdf");
        JsonNode planDetail = getItemDetail(planItemId);
        assertThat(planDetail.get("status").asText()).isEqualTo("미완료");
        assertThat(planDetail.get("versions").size()).isEqualTo(0);
        assertThat(wbsAuditsIncludeManualPick(wbsDetail)).isTrue();
    }

    @Test
    @DisplayName("TC-18: 신규(자동등록) 카드에서 항목을 골라 수락하면 그 행으로 연결된다(REQ-outputhub-025)")
    void acceptNewCardWithChosenRow() throws Exception {
        // Given: 착수 템플릿 프로젝트, 후보 없는 파일 업로드 → 신규(자동등록) 카드
        long projectId = createProject();
        long planItemId = findItemIdByName(projectId, PLAN_ITEM_NAME);
        JsonNode suggestion = uploadWithoutTargetWith(projectId, "합의서_스캔본.pdf", "합의 관련 스캔 이미지 문서");
        assertThat(suggestion.get("verdict").asText()).isEqualTo("신규(자동등록)");

        // When: 카드 드롭다운에서 사업수행계획서를 골라 수락한다
        mockMvc.perform(post("/api/suggestions/" + suggestion.get("suggestionId").asLong() + "/accept")
                        .queryParam("itemId", String.valueOf(planItemId)))
                .andExpect(status().isOk());

        // Then: 사업수행계획서 행이 접수되고 새 버전으로 등록된다
        JsonNode detail = getItemDetail(planItemId);
        assertThat(detail.get("status").asText()).isEqualTo("검토 필요");
        assertThat(detail.get("versions").get(0).get("fileName").asText())
                .startsWith(PLAN_ITEM_NAME + "_v0.1_").endsWith(".pdf");
    }

    @Test
    @DisplayName("TC-19: 신규(자동등록) 카드를 검토완료 행으로 수락하면 재업로드 승인으로 v1.01이 등록된다")
    void acceptNewCardOnConfirmedRowThroughReplace() throws Exception {
        // Given: 사업수행계획서 행을 접수·검토완료까지 진행하고, 후보 없는 파일을 신규 카드로 받는다
        long projectId = createProject();
        long planItemId = findItemIdByName(projectId, PLAN_ITEM_NAME);
        JsonNode first = uploadWithoutTargetWith(projectId, PLAN_FILE_NAME, "사업수행계획서 (검증용 본문)");
        mockMvc.perform(post("/api/suggestions/" + first.get("suggestionId").asLong() + "/accept")
                        .queryParam("itemId", String.valueOf(planItemId)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/deliverables/" + planItemId + "/confirm"))
                .andExpect(status().isOk());

        JsonNode second = uploadWithoutTargetWith(projectId, "합의서_스캔본.pdf", "합의 관련 스캔 이미지 문서");
        assertThat(second.get("verdict").asText()).isEqualTo("신규(자동등록)");

        // When: 카드 드롭다운에서 검토완료된 사업수행계획서 행을 골라 수락한다(팝업 확인 대신 replace-confirmed)
        mockMvc.perform(post("/api/deliverables/" + planItemId + "/replace-confirmed")
                        .contentType("application/json")
                        .content("{\"reason\":\"" + second.get("suggestionId").asLong() + "\"}"))
                .andExpect(status().isOk());

        // Then: 상태가 검토 필요로 돌아가고 v1.01 버전이 등록된다(REQ-outputhub-022)
        JsonNode detail = getItemDetail(planItemId);
        assertThat(detail.get("status").asText()).isEqualTo("검토 필요");
        assertThat(detail.get("versions").get(0).get("versionLabel").asText()).isEqualTo("1.01");
        assertThat(detail.get("versions").get(0).get("fileName").asText())
                .startsWith(PLAN_ITEM_NAME + "_v1.01_").endsWith(".pdf");
    }

    private boolean wbsAuditsIncludeManualPick(JsonNode detail) {
        for (JsonNode audit : detail.get("audits")) {
            if ("접수".equals(audit.get("action").asText())
                    && audit.get("detail").asText().contains("검토대기에서 산출물 항목을 직접 선택")) {
                return true;
            }
        }
        return false;
    }

    /** 항목을 지정하지 않은 업로드(규칙 매칭 흐름). */
    private JsonNode uploadWithoutTarget(long projectId) throws Exception {
        return uploadWithoutTargetWith(projectId, PLAN_FILE_NAME, "사업수행계획서 (검증용 본문)");
    }

    private JsonNode uploadWithoutTargetWith(long projectId, String fileName, String body) throws Exception {
        MvcResult result = mockMvc.perform(multipart("/api/projects/" + projectId + "/submissions")
                        .file(new MockMultipartFile("files", fileName, "application/pdf",
                                body.getBytes(StandardCharsets.UTF_8))))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode list = objectMapper.readTree(result.getResponse().getContentAsString());
        return list.get(list.size() - 1);
    }

    private boolean hasConfirmAuditWithV1Name(JsonNode detail) {
        for (JsonNode audit : detail.get("audits")) {
            boolean confirmAction = "확정".equals(audit.get("action").asText());
            boolean mentionsV1Name = audit.get("detail").asText().contains(PLAN_ITEM_NAME + "_v1.0.pdf");
            if (confirmAction && mentionsV1Name) {
                return true;
            }
        }
        return false;
    }

    private long createProject() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/projects")
                        .contentType("application/json")
                        .content("{\"name\":\"드롭다운접수검증-" + System.nanoTime()
                                + "\",\"vendor\":\"검증\",\"templateStage\":\"착수\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();
    }

    private long findItemIdByName(long projectId, String itemName) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/projects/" + projectId + "/items"))
                .andExpect(status().isOk())
                .andReturn();
        for (JsonNode option : objectMapper.readTree(result.getResponse().getContentAsString())) {
            if (itemName.equals(option.get("itemName").asText())) {
                return option.get("id").asLong();
            }
        }
        throw new IllegalStateException("항목을 찾을 수 없습니다: " + itemName);
    }

    private JsonNode uploadWithTarget(long projectId, long targetItemId) throws Exception {
        MvcResult result = mockMvc.perform(multipart("/api/projects/" + projectId + "/submissions")
                        .file(new MockMultipartFile("files", PLAN_FILE_NAME, "application/pdf",
                                "사업수행계획서 (검증용 본문)".getBytes(StandardCharsets.UTF_8)))
                        .param("targetItemId", String.valueOf(targetItemId)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode list = objectMapper.readTree(result.getResponse().getContentAsString());
        return list.get(list.size() - 1);
    }

    private JsonNode getItemDetail(long itemId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/deliverables/" + itemId))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
