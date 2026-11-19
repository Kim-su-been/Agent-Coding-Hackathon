package com.outputhub.domain;

import jakarta.persistence.Entity;
import java.time.LocalDate;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.springframework.data.jpa.repository.JpaRepository;

/** 프로젝트가 받아야 할 산출물 항목 한 행(기준대장). 단계(phase)별로 묶인다. */
@Entity
@Table(name = "checklist_items")
public class ChecklistItem {

    /** 주기 접수 산출물 중 이전 파일을 치우지 않고 누적 보관하는 항목 이름 키워드(PMO 주간보고). */
    public static final String CUMULATIVE_ITEM_KEYWORD = "주간보고";

    /** 주간보고서 전용 단계. 주기 접수로만 채워지므로 항목 추가·삭제 대상이 아니다. */
    public static final String PMO_PHASE = "PMO";

    /** 대시보드 검토 상태(미완료 → 검토 필요 → 검토완료/보완 필요). */
    public enum Status {
        NOT_SUBMITTED("미완료"),
        SUBMITTED("검토 필요"),
        SUPPLEMENT_REQUIRED("보완 필요"),
        CONFIRMED("검토완료");

        private final String label;

        Status(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        public static Status fromLabel(String label) {
            for (Status s : values()) {
                if (s.label.equals(label)) {
                    return s;
                }
            }
            throw new IllegalArgumentException("알 수 없는 상태: " + label);
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id")
    private Project project;

    private String phase;
    private String itemName;
    private boolean required;

    @Enumerated(EnumType.STRING)
    private Status status = Status.NOT_SUBMITTED;

    private String supplementReason;

    /** 마감일(REQ-outputhub-028). 미지정(null)이면 지연 판정에서 제외한다(주간보고처럼 주기 접수 항목). */
    private LocalDate dueDate;

    protected ChecklistItem() {
    }

    public ChecklistItem(Project project, String phase, String itemName, boolean required) {
        this(project, phase, itemName, required, null);
    }

    public ChecklistItem(Project project, String phase, String itemName, boolean required, LocalDate dueDate) {
        this.project = project;
        this.phase = phase;
        this.itemName = itemName;
        this.required = required;
        this.dueDate = dueDate;
    }

    /** BD-outputhub-P01 §3(L-outputhub-006): 허용 전환 외에는 거부한다. */
    public void transitionTo(Status target) {
        if (!canTransitionTo(target)) {
            throw new IllegalStateException("허용되지 않는 상태 전환: " + status.label() + " → " + target.label());
        }
        if (target != Status.SUPPLEMENT_REQUIRED) {
            supplementReason = null;
        }
        status = target;
    }

    public boolean canTransitionTo(Status target) {
        return switch (status) {
            case NOT_SUBMITTED -> target == Status.SUBMITTED;
            case SUBMITTED -> target == Status.CONFIRMED || target == Status.SUPPLEMENT_REQUIRED || target == Status.SUBMITTED;
            case SUPPLEMENT_REQUIRED -> target == Status.SUBMITTED;
            case CONFIRMED -> target == Status.SUBMITTED;
        };
    }

    public boolean isReceived() {
        return status != Status.NOT_SUBMITTED;
    }

    /** 주간보고 같은 주기 접수 항목은 이전 버전을 old로 만들지 않고 파일을 누적 보관한다. */
    public boolean accumulatesFiles() {
        return itemName != null && itemName.contains(CUMULATIVE_ITEM_KEYWORD);
    }

    public void requestSupplement(String reason) {
        transitionTo(Status.SUPPLEMENT_REQUIRED);
        supplementReason = reason;
    }

    public Long getId() {
        return id;
    }

    public Project getProject() {
        return project;
    }

    public String getPhase() {
        return phase;
    }

    public String getItemName() {
        return itemName;
    }

    public boolean isRequired() {
        return required;
    }

    public void changeRequired(boolean required) {
        this.required = required;
    }

    public Status getStatus() {
        return status;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    /** 마감일을 바꾼다(REQ-outputhub-028). null 이면 미지정으로 돌아간다. */
    public void changeDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    public String getSupplementReason() {
        return supplementReason;
    }

    public interface Repository extends JpaRepository<ChecklistItem, Long> {
        java.util.List<ChecklistItem> findByProjectIdOrderByIdAsc(Long projectId);
    }
}
