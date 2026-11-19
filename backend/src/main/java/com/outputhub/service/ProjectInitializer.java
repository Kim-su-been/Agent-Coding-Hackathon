package com.outputhub.service;

import com.outputhub.domain.ChecklistItem;
import com.outputhub.domain.Project;
import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 대시보드 기본 세팅: 프로젝트가 하나도 없으면 기본 3개(AI.NSP, AI.NP, e-HR)를
 * 템플릿 파일의 단계별 산출물 목록으로 등록한다(REQ-outputhub-014).
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
    }

    private void registerProject(String projectName) {
        Project saved = projects.save(new Project(projectName, DEFAULT_VENDOR));
        for (TemplateLoader.TemplateItem template : templates.load(null)) {
            items.save(new ChecklistItem(saved, template.phase(), template.name(), template.required()));
        }
    }
}
