package com.code.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import javax.persistence.*;

@Entity
@Table(name = "production_weekly_plan_item")
@Data
public class ProductionWeeklyPlanItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "plan_id")
    @JsonIgnore
    private ProductionWeeklyPlan plan;

    @ManyToOne
    @JoinColumn(name = "product_id")
    private Product product;

    @Column(name = "suggested_quantity")
    private Double suggestedQuantity = 0.0;

    @Column(name = "last_week_produced_quantity")
    private Double lastWeekProducedQuantity = 0.0;

    @Column(name = "active_demand_quantity")
    private Double activeDemandQuantity = 0.0;

    @Column(name = "available_inventory_quantity")
    private Double availableInventoryQuantity = 0.0;

    @Column(name = "baseline_quantity")
    private Double baselineQuantity = 0.0;

    @Column(name = "growth_factor")
    private Double growthFactor = 1.1;

    @Column(name = "suggestion_reason")
    private String suggestionReason;
}

