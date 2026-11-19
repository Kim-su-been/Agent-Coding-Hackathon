package com.outputhub.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import org.springframework.data.jpa.repository.JpaRepository;

@Entity
@Table(name = "deliverables")
public class Deliverable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id")
    private ChecklistItem item;

    /** REQ-outputhub-022: 최초 수락 버전은 v0.1(백분위 10)이다. */
    public static final int FIRST_VERSION_NO = 10;

    /** REQ-outputhub-022: 검토완료 표기 버전은 v1.0(백분위 100)이다. */
    public static final int CONFIRMED_VERSION_NO = 100;

    private int latestVersionNo;

    protected Deliverable() {
    }

    public Deliverable(ChecklistItem item) {
        this.item = item;
        this.latestVersionNo = 0;
    }

    /**
     * 새 수락 버전 번호를 발급한다(REQ-outputhub-007).
     * REQ-outputhub-022: v0.1(10)부터 0.01(백분위 1)씩 올리고, 이전 체계의 낡은 값은 v0.1부터 다시 센다.
     */
    public int nextVersionNo() {
        latestVersionNo = peekNextVersionNo();
        return latestVersionNo;
    }

    /** 카운터를 바꾸지 않고 다음 버전 번호를 계산만 한다(제안 근거 표시용). */
    public int peekNextVersionNo() {
        return Math.max(latestVersionNo, FIRST_VERSION_NO - 1) + 1;
    }

    /** REQ-outputhub-022: 검토완료 뒤 재업로드 버전이 v1.0 다음 번호(v1.01…)가 되도록 카운터를 맞춘다. */
    public void applyConfirmedVersion() {
        if (latestVersionNo < CONFIRMED_VERSION_NO) {
            latestVersionNo = CONFIRMED_VERSION_NO;
        }
    }

    public Long getId() {
        return id;
    }

    public ChecklistItem getItem() {
        return item;
    }

    public int getLatestVersionNo() {
        return latestVersionNo;
    }

    public interface Repository extends JpaRepository<Deliverable, Long> {
        java.util.Optional<Deliverable> findByItemId(Long itemId);
    }
}
