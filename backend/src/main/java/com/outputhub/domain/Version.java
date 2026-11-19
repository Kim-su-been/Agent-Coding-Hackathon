package com.outputhub.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.springframework.data.jpa.repository.JpaRepository;

@Entity
@Table(name = "file_versions")
public class Version {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "deliverable_id")
    private Deliverable deliverable;

    private int versionNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "submission_id")
    private Submission submission;

    private boolean old;

    protected Version() {
    }

    public Version(Deliverable deliverable, int versionNo, Submission submission) {
        this.deliverable = deliverable;
        this.versionNo = versionNo;
        this.submission = submission;
        this.old = false;
    }

    public void markOld() {
        old = true;
    }

    public Long getId() {
        return id;
    }

    public Deliverable getDeliverable() {
        return deliverable;
    }

    public int getVersionNo() {
        return versionNo;
    }

    public Submission getSubmission() {
        return submission;
    }

    public boolean isOld() {
        return old;
    }

    public interface Repository extends JpaRepository<Version, Long> {
        java.util.List<Version> findByDeliverableIdOrderByVersionNoDesc(Long deliverableId);
        java.util.Optional<Version> findFirstByDeliverableIdAndOldFalseOrderByVersionNoDesc(Long deliverableId);

        /** 동일 파일명 재접수 대상 행 찾기: 이 접수가 등록된 버전을 찾는다. */
        java.util.Optional<Version> findFirstBySubmissionIdOrderByIdDesc(Long submissionId);

        /** REQ-outputhub-006: 프로젝트 대장 행에 등록된 모든 버전(중복 판정의 해시 비교 대상). */
        java.util.List<Version> findByDeliverable_Item_ProjectIdOrderByIdAsc(Long projectId);
    }
}
