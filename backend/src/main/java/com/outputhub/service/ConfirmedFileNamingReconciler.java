package com.outputhub.service;

import com.outputhub.domain.ChecklistItem;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * REQ-outputhub-022: 서버 시작 때마다 검토완료 항목의 최신본 파일명이 규칙({항목}_v1.0.{확장자})을
 * 따르는지 점검한다. 낡은 빌드에서 확정돼 파일명이 바뀌지 않은 기록을 규칙 이름으로 바로잡아
 * 검토완료 기록이 항상 규칙 파일명을 갖게 유지한다.
 */
@Component
public class ConfirmedFileNamingReconciler implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ConfirmedFileNamingReconciler.class);

    private final ChecklistItem.Repository items;
    private final VersionRegistrationService versionRegistration;

    public ConfirmedFileNamingReconciler(ChecklistItem.Repository items,
                                         VersionRegistrationService versionRegistration) {
        this.items = items;
        this.versionRegistration = versionRegistration;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<String> healed = reconcileConfirmedItems();
        if (!healed.isEmpty()) {
            log.info("검토완료 파일명 정합화 {}건: {}", healed.size(), String.join(", ", healed));
        }
    }

    /** 모든 검토완료 항목의 최신본 파일명을 점검·바로잡고, 바꾼 규칙 파일명 목록을 돌려준다. 한 항목이 실패해도 나머지를 계속 점검한다. */
    public List<String> reconcileConfirmedItems() {
        List<String> healed = new ArrayList<>();
        for (ChecklistItem item : items.findAll()) {
            if (item.getStatus() != ChecklistItem.Status.CONFIRMED) {
                continue;
            }
            try {
                String confirmedName = versionRegistration.reconcileConfirmedFileName(item);
                if (confirmedName != null) {
                    healed.add(confirmedName);
                }
            } catch (RuntimeException e) {
                log.warn("검토완료 파일명 정합화 실패(item={}): {}", item.getId(), e.getMessage());
            }
        }
        return healed;
    }
}
