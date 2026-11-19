package com.outputhub.api;

import com.outputhub.domain.ChecklistItem;
import com.outputhub.domain.Project;
import com.outputhub.service.ChecklistItemService;
import com.outputhub.service.DashboardService;
import com.outputhub.service.TemplateLoader;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    public record CreateRequest(String name, String vendor, String templateStage) {
    }

    public record ItemRequest(String phase, String itemName, boolean required) {
    }

    /** REQ-outputhub-023: 행마다 업로드된 파일 중 최신본 1개만 내려준다. 전체 버전은 상세 화면(REQ-outputhub-024)에서 본다. */
    public record RowView(Long id, String phase, String itemName, boolean required, String status,
                          String supplementReason, int latestVersionNo, String latestVersionLabel,
                          String latestFileName, String latestDownloadUrl, String evidence) {
    }

    /** 접수함 업로드 드롭다운용 산출물 항목 한 줄(REQ-outputhub-021). */
    public record ItemOptionView(Long id, String phase, String itemName, String status) {
    }

    public record PhaseView(String name, int total, int submitted, int confirmed, int pct, int submittedPct) {
    }

    public record DashboardView(Project project, int overallPct, int confirmedCount, int totalCount,
                                List<PhaseView> phases, List<String> badges, List<RowView> rows) {
    }

    private final Project.Repository projects;
    private final ChecklistItem.Repository items;
    private final TemplateLoader templates;
    private final ChecklistItemService itemService;
    private final DashboardService dashboardService;

    public ProjectController(Project.Repository projects, ChecklistItem.Repository items,
                             TemplateLoader templates, ChecklistItemService itemService,
                             DashboardService dashboardService) {
        this.projects = projects;
        this.items = items;
        this.templates = templates;
        this.itemService = itemService;
        this.dashboardService = dashboardService;
    }

    @PostMapping
    public Project create(@RequestBody CreateRequest request) {
        Project saved = projects.save(new Project(request.name(), request.vendor()));
        for (TemplateLoader.TemplateItem template : templates.load(request.templateStage())) {
            items.save(new ChecklistItem(saved, template.phase(), template.name(), template.required()));
        }
        return saved;
    }

    @GetMapping
    public List<Project> list() {
        return projects.findAll();
    }

    /** 대시보드: 전체·단계별 완료율을 모두 검토완료 기준으로 계산해 산출물 목록과 한 번에 내려준다(사용자 요구 2026-11-19). */
    @GetMapping("/{id}/dashboard")
    public ResponseEntity<DashboardView> dashboard(@PathVariable Long id) {
        return projects.findById(id)
                .<ResponseEntity<DashboardView>>map(project -> ResponseEntity.ok(dashboardService.load(project)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** 접수함 업로드 바의 산출물 항목 드롭다운 목록(REQ-outputhub-021). */
    @GetMapping("/{id}/items")
    public ResponseEntity<List<ItemOptionView>> items(@PathVariable Long id) {
        if (!projects.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        List<ItemOptionView> options = items.findByProjectIdOrderByIdAsc(id).stream()
                .map(row -> new ItemOptionView(row.getId(), row.getPhase(), row.getItemName(),
                        row.getStatus().label()))
                .toList();
        return ResponseEntity.ok(options);
    }

    /** 산출물 목록에 항목을 직접 추가한다. */
    @PostMapping("/{id}/items")
    public ResponseEntity<?> addItem(@PathVariable Long id, @RequestBody ItemRequest request) {
        if (request == null || request.itemName() == null || request.itemName().isBlank()) {
            return ResponseEntity.badRequest().body("산출물 이름을 입력하세요");
        }
        if (request.phase() == null || request.phase().isBlank()) {
            return ResponseEntity.badRequest().body("단계를 선택하세요");
        }
        return ResponseEntity.ok(itemService.add(id, request.phase(), request.itemName().trim(), request.required()));
    }

    /** 산출물 목록에서 항목을 삭제한다. */
    @DeleteMapping("/{id}/items/{itemId}")
    public ResponseEntity<?> removeItem(@PathVariable Long id, @PathVariable Long itemId) {
        itemService.remove(id, itemId);
        return ResponseEntity.noContent().build();
    }
}
