package com.outputhub.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 프로젝트 추가 기능 시험(REQ-outputhub-014).
 * 추가하면 기본 세팅과 같은 템플릿 전체 단계 산출물 목록이 자동 등록되고,
 * 같은 이름·빈 이름 추가는 거부된다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ProjectCreateTest {

    private static final int DEFAULT_TEMPLATE_ITEM_COUNT = 23;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    @DisplayName("프로젝트 추가: 템플릿 전체 단계 산출물 목록이 자동 등록되고 대시보드가 만들어진다")
    void createProjectSeedsDefaultDeliverables() throws Exception {
        // Given: 추가할 프로젝트 이름이 준비되어 있다
        String projectName = "추가기본세팅검증-" + System.nanoTime();

        // When: 프로젝트를 추가한다
        MvcResult created = mockMvc.perform(post("/api/projects")
                        .contentType("application/json")
                        .content("{\"name\":\"" + projectName + "\",\"vendor\":\"기본세팅수행사\"}"))
                .andExpect(status().isOk())
                .andReturn();
        long projectId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        // Then: 착수부터 PMO 까지 템플릿 전체 단계의 산출물 행이 등록되어 있다
        MvcResult itemsResult = mockMvc.perform(get("/api/projects/" + projectId + "/items"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode items = objectMapper.readTree(itemsResult.getResponse().getContentAsString());
        assertThat(items.size()).isEqualTo(DEFAULT_TEMPLATE_ITEM_COUNT);
        assertThat(items.get(0).get("phase").asText()).isEqualTo("착수");
        assertThat(items.get(items.size() - 1).get("phase").asText()).isEqualTo("PMO");

        // Then: 대시보드도 같은 프로젝트로 바로 조회된다
        MvcResult dashboardResult = mockMvc.perform(get("/api/projects/" + projectId + "/dashboard"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode dashboard = objectMapper.readTree(dashboardResult.getResponse().getContentAsString());
        assertThat(dashboard.get("project").get("name").asText()).isEqualTo(projectName);
        assertThat(dashboard.get("overallPct").asInt()).isZero();
    }

    @Test
    @DisplayName("프로젝트 추가: 같은 이름은 거부된다")
    void duplicateProjectNameIsRejected() throws Exception {
        // Given: 이미 등록된 프로젝트 이름이 있다
        String projectName = "중복이름검증-" + System.nanoTime();
        mockMvc.perform(post("/api/projects")
                        .contentType("application/json")
                        .content("{\"name\":\"" + projectName + "\",\"vendor\":\"검증\"}"))
                .andExpect(status().isOk());

        // When: 같은 이름으로 다시 추가한다
        // Then: 추가가 거부되고 안내 메시지가 돌아온다
        MvcResult rejected = mockMvc.perform(post("/api/projects")
                        .contentType("application/json")
                        .content("{\"name\":\" " + projectName + " \",\"vendor\":\"검증\"}"))
                .andExpect(status().isBadRequest())
                .andReturn();
        assertThat(rejected.getResponse().getContentAsString()).contains("같은 이름의 프로젝트");
    }

    @Test
    @DisplayName("프로젝트 추가: 이름이 비면 거부된다")
    void blankProjectNameIsRejected() throws Exception {
        // When: 이름을 비우고 추가한다
        // Then: 추가가 거부된다
        mockMvc.perform(post("/api/projects")
                        .contentType("application/json")
                        .content("{\"name\":\"   \",\"vendor\":\"검증\"}"))
                .andExpect(status().isBadRequest());
    }
}
