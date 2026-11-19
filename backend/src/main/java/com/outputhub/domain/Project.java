package com.outputhub.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.springframework.data.jpa.repository.JpaRepository;

/** 산출물 검토의 관리 단위 프로젝트(예: AI.NSP, AI.NP, e-HR). */
@Entity
@Table(name = "projects")
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String vendor;

    protected Project() {
    }

    public Project(String name, String vendor) {
        this.name = name;
        this.vendor = vendor;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getVendor() {
        return vendor;
    }

    public interface Repository extends JpaRepository<Project, Long> {
        boolean existsByName(String name);
    }
}
