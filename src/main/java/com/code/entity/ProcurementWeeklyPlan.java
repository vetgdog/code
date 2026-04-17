package com.code.entity;

import lombok.Data;

import javax.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "procurement_weekly_plan", uniqueConstraints = {
        @UniqueConstraint(name = "uk_procurement_weekly_plan_week_start", columnNames = "week_start")
})
@Data
public class ProcurementWeeklyPlan {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "week_start")
    private LocalDate weekStart;

    @Column(name = "week_end")
    private LocalDate weekEnd;

    @Column(name = "based_on_week_start")
    private LocalDate basedOnWeekStart;

    @Column(name = "based_on_week_end")
    private LocalDate basedOnWeekEnd;

    private String status = "GENERATED";

    @Column(name = "generated_by")
    private String generatedBy;

    @Column(name = "algorithm_note")
    private String algorithmNote;

    @Column(name = "generated_at")
    private LocalDateTime generatedAt = LocalDateTime.now();

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @OneToMany(mappedBy = "plan", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProcurementWeeklyPlanItem> items;
}

