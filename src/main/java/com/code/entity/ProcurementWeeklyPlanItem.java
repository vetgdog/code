package com.code.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import javax.persistence.*;

@Entity
@Table(name = "procurement_weekly_plan_item")
@Data
public class ProcurementWeeklyPlanItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "plan_id")
    @JsonIgnore
    private ProcurementWeeklyPlan plan;

    @ManyToOne
    @JoinColumn(name = "product_id")
    private Product product;

    @Column(name = "preferred_supplier")
    private String preferredSupplier;

    @Column(name = "suggested_quantity")
    private Double suggestedQuantity = 0.0;

    @Column(name = "last_week_procured_quantity")
    private Double lastWeekProcuredQuantity = 0.0;

    @Column(name = "bom_demand_quantity")
    private Double bomDemandQuantity = 0.0;

    @Column(name = "available_inventory_quantity")
    private Double availableInventoryQuantity = 0.0;

    @Column(name = "in_transit_quantity")
    private Double inTransitQuantity = 0.0;

    @Column(name = "safety_stock_gap")
    private Double safetyStockGap = 0.0;

    @Column(name = "suggestion_reason")
    private String suggestionReason;
}

