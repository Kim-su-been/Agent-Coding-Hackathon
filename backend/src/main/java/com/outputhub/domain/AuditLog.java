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
@Table(name = "audit_logs")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id")
    private ChecklistItem item;

    private String action;
    private String detail;
    private String actor;
    private LocalDateTime at;

    protected AuditLog() {
    }

    public AuditLog(ChecklistItem item, String action, String detail, String actor, LocalDateTime at) {
        this.item = item;
        this.action = action;
        this.detail = detail;
        this.actor = actor;
        this.at = at;
    }

    public Long getId() {
        return id;
    }

    public ChecklistItem getItem() {
        return item;
    }

    public String getAction() {
        return action;
    }

    public String getDetail() {
        return detail;
    }

    public String getActor() {
        return actor;
    }

    public LocalDateTime getAt() {
        return at;
    }

    public interface Repository extends JpaRepository<AuditLog, Long> {
        java.util.List<AuditLog> findByItemIdOrderByIdAsc(Long itemId);
    }
}
