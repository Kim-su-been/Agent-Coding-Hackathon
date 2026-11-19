package com.outputhub.service;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

/**
 * REQ-outputhub-014: 기본 산출물 목록을 설정 파일(deliverable-templates.yml)에서 읽는다.
 * 단계 순서는 파일 기술 순서를 따른다.
 */
@Service
public class TemplateLoader {

    private static final String TEMPLATE_FILE = "deliverable-templates.yml";

    public record TemplateItem(String phase, String name, boolean required, LocalDate dueDate) {
    }

    /** 단계 필터가 비었으면 전체 단계 목록을, 아니면 해당 단계만 돌려준다. */
    public List<TemplateItem> load(String phaseFilter) {
        try (InputStream in = new ClassPathResource(TEMPLATE_FILE).getInputStream()) {
            return parse(new Yaml().load(in), phaseFilter);
        } catch (Exception e) {
            throw new IllegalStateException("기본 산출물 목록 템플릿을 읽을 수 없습니다", e);
        }
    }

    @SuppressWarnings("unchecked")
    private List<TemplateItem> parse(Object root, String phaseFilter) {
        List<TemplateItem> result = new ArrayList<>();
        for (Object stageEntry : (List<Object>) ((Map<String, Object>) root).get("templates")) {
            Map<String, Object> stage = (Map<String, Object>) stageEntry;
            String phase = (String) stage.get("stage");
            if (phaseFilter != null && !phaseFilter.isBlank() && !phaseFilter.equals(phase)) {
                continue;
            }
            for (Object itemEntry : (List<Object>) stage.get("items")) {
                Map<String, Object> item = (Map<String, Object>) itemEntry;
                boolean required = Boolean.TRUE.equals(item.get("required"));
                result.add(new TemplateItem(phase, (String) item.get("name"), required,
                        parseDueDate(item.get("due"))));
            }
        }
        return result;
    }

    /** 기준 마감일(REQ-outputhub-028). due 키가 없거나 빈 값이면 마감일 미지정(null)이다. */
    private LocalDate parseDueDate(Object due) {
        if (due == null || due.toString().isBlank()) {
            return null;
        }
        return LocalDate.parse(due.toString().trim());
    }
}
