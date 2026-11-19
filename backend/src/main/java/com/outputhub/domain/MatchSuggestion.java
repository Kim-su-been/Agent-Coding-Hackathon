package com.outputhub.domain;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.springframework.data.jpa.repository.JpaRepository;

@Entity
@Table(name = "match_suggestions")
public class MatchSuggestion {

    public enum Verdict {
        LINK("행 연결"),
        NEW("신규(자동등록)"),
        DUPLICATE("중복");

        private final String label;

        Verdict(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public enum Status {
        PENDING, ACCEPTED, REJECTED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id")
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "submission_id")
    private Submission submission;

    @Enumerated(EnumType.STRING)
    private Verdict verdict;

    @Enumerated(EnumType.STRING)
    private Status status = Status.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_item_id")
    private ChecklistItem targetItem;

    @Lob
    private String evidence;

    protected MatchSuggestion() {
    }

    public MatchSuggestion(Project project, Submission submission, Verdict verdict, ChecklistItem targetItem, String evidence) {
        this.project = project;
        this.submission = submission;
        this.verdict = verdict;
        this.targetItem = targetItem;
        this.evidence = evidence;
    }

    public void accept() {
        status = Status.ACCEPTED;
    }

    public void reject() {
        status = Status.REJECTED;
    }

    public Long getId() {
        return id;
    }

    public Project getProject() {
        return project;
    }

    public Submission getSubmission() {
        return submission;
    }

    public Verdict getVerdict() {
        return verdict;
    }

    public Status getStatus() {
        return status;
    }

    public ChecklistItem getTargetItem() {
        return targetItem;
    }

    public String getEvidence() {
        return evidence;
    }

    public interface Repository extends JpaRepository<MatchSuggestion, Long> {
        List<MatchSuggestion> findByProjectIdAndStatusOrderByIdAsc(Long projectId, Status status);

        Optional<MatchSuggestion> findFirstBySubmissionIdAndStatusOrderByIdAsc(Long submissionId, Status status);
    }
}
