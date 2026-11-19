package com.outputhub.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;

@Entity
@Table(name = "submissions")
public class Submission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id")
    private Project project;

    private String fileName;
    /** 업로드 시점 원본 파일명. 수락으로 fileName 이 규칙 파일명으로 바뀌어도 재접수 판정 기준으로 유지한다. */
    private String originalFileName;
    private String storedPath;

    @Lob
    private String sha256;

    private LocalDateTime uploadedAt;

    protected Submission() {
    }

    public Submission(Project project, String fileName, String storedPath, String sha256, LocalDateTime uploadedAt) {
        this.project = project;
        this.fileName = fileName;
        this.originalFileName = fileName;
        this.storedPath = storedPath;
        this.sha256 = sha256;
        this.uploadedAt = uploadedAt;
    }

    public Long getId() {
        return id;
    }

    public Project getProject() {
        return project;
    }

    public String getFileName() {
        return fileName;
    }

    public String getOriginalFileName() {
        return originalFileName;
    }

    public String getStoredPath() {
        return storedPath;
    }

    /** REQ-outputhub-007: 이전 버전 원본이 old 폴더로 수집된 뒤 실제 위치를 갱신한다. */
    public void relocate(String newStoredPath) {
        this.storedPath = newStoredPath;
    }

    /** REQ-outputhub-022: 수락·검토완료 시점에 원본 파일을 규칙 파일명으로 바꾼 뒤 실제 위치를 함께 갱신한다. */
    public void renameTo(String newFileName, String newStoredPath) {
        this.fileName = newFileName;
        this.storedPath = newStoredPath;
    }

    public String getSha256() {
        return sha256;
    }

    public LocalDateTime getUploadedAt() {
        return uploadedAt;
    }

    public interface Repository extends JpaRepository<Submission, Long> {
        /** 동일 파일명 재접수 판정: 같은 프로젝트에서 같은 원본 파일명 접수를 최근 것부터 찾는다. */
        java.util.List<Submission> findByProjectIdAndOriginalFileNameOrderByIdDesc(Long projectId,
                String originalFileName);
    }
}
