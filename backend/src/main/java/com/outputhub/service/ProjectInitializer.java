package com.outputhub.service;

import com.outputhub.domain.ChecklistItem;
import com.outputhub.domain.Project;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 대시보드 기본 세팅: 프로젝트가 하나도 없으면 기본 3개(AI.NSP, AI.NP, e-HR)를
 * 템플릿 파일의 단계별 산출물 목록으로 등록한다(REQ-outputhub-014).
 * 마감일이 비어 있는 기존 항목도 템플릿 기준 마감일로 채운다(REQ-outputhub-028, REQ-outputhub-022 정합화 선례).
 */
@Component
public class ProjectInitializer implements ApplicationRunner {

    static final List<String> DEFAULT_PROJECTS = List.of("AI.NSP", "AI.NP", "e-HR");
    static final String DEFAULT_VENDOR = "미지정";

    private final Project.Repository projects;
    private final ChecklistItem.Repository items;
    private final TemplateLoader templates;

    public ProjectInitializer(Project.Repository projects, ChecklistItem.Repository items,
                              TemplateLoader templates) {
        this.projects = projects;
        this.items = items;
        this.templates = templates;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (String projectName : DEFAULT_PROJECTS) {
            if (!projects.existsByName(projectName)) {
                registerProject(projectName);
            }
        }
        fillMissingDueDates();
    }

    private void registerProject(String projectName) {
        Project saved = projects.save(new Project(projectName, DEFAULT_VENDOR));
        for (TemplateLoader.TemplateItem template : templates.load(null)) {
            items.save(new ChecklistItem(saved, template.phase(), template.name(), template.required(),
                    template.dueDate()));
        }
    }

    /** REQ-outputhub-028: 마감일이 비어 있는 기존 항목을 템플릿 기준 마감일로 채운다(단계·항목명으로 대응). */
    private void fillMissingDueDates() {
        Map<String, LocalDate> dueByItem = new HashMap<>();
        for (TemplateLoader.TemplateItem template : templates.load(null)) {
            if (template.dueDate() != null) {
                dueByItem.put(template.phase() + "\n" + template.name(), template.dueDate());
            }
        }
        for (ChecklistItem item : items.findAll()) {
            if (item.getDueDate() == null) {
                LocalDate due = dueByItem.get(item.getPhase() + "\n" + item.getItemName());
                if (due != null) {
                    item.changeDueDate(due);
                    items.save(item);
                }
            }
        }
    }
}
