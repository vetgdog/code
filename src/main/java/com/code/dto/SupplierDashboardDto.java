package com.code.dto;

import java.util.List;

public class SupplierDashboardDto {
    private final String supplierName;
    private final int pendingConfirmCount;
    private final int acceptedPendingShipCount;
    private final int shippedCount;
    private final int totalOpenOrders;
    private final List<RecommendedMaterial> recommendedMaterials;
    private final List<TodoOrder> todoOrders;

    public SupplierDashboardDto(String supplierName,
                                int pendingConfirmCount,
                                int acceptedPendingShipCount,
                                int shippedCount,
                                int totalOpenOrders,
                                List<RecommendedMaterial> recommendedMaterials,
                                List<TodoOrder> todoOrders) {
        this.supplierName = supplierName;
        this.pendingConfirmCount = pendingConfirmCount;
        this.acceptedPendingShipCount = acceptedPendingShipCount;
        this.shippedCount = shippedCount;
        this.totalOpenOrders = totalOpenOrders;
        this.recommendedMaterials = recommendedMaterials;
        this.todoOrders = todoOrders;
    }

    public String getSupplierName() {
        return supplierName;
    }

    public int getPendingConfirmCount() {
        return pendingConfirmCount;
    }

    public int getAcceptedPendingShipCount() {
        return acceptedPendingShipCount;
    }

    public int getShippedCount() {
        return shippedCount;
    }

    public int getTotalOpenOrders() {
        return totalOpenOrders;
    }

    public List<RecommendedMaterial> getRecommendedMaterials() {
        return recommendedMaterials;
    }

    public List<TodoOrder> getTodoOrders() {
        return todoOrders;
    }

    public static class RecommendedMaterial {
        private final Long id;
        private final String sku;
        private final String name;
        private final String materialCategory;
        private final String specification;
        private final String preferredSupplier;
        private final Double unitPrice;
        private final Double safetyStock;

        public RecommendedMaterial(Long id,
                                   String sku,
                                   String name,
                                   String materialCategory,
                                   String specification,
                                   String preferredSupplier,
                                   Double unitPrice,
                                   Double safetyStock) {
            this.id = id;
            this.sku = sku;
            this.name = name;
            this.materialCategory = materialCategory;
            this.specification = specification;
            this.preferredSupplier = preferredSupplier;
            this.unitPrice = unitPrice;
            this.safetyStock = safetyStock;
        }

        public Long getId() {
            return id;
        }

        public String getSku() {
            return sku;
        }

        public String getName() {
            return name;
        }

        public String getMaterialCategory() {
            return materialCategory;
        }

        public String getSpecification() {
            return specification;
        }

        public String getPreferredSupplier() {
            return preferredSupplier;
        }

        public Double getUnitPrice() {
            return unitPrice;
        }

        public Double getSafetyStock() {
            return safetyStock;
        }
    }

    public static class TodoOrder {
        private final Long id;
        private final String poNo;
        private final String status;
        private final String itemsSummary;
        private final Double totalAmount;

        public TodoOrder(Long id, String poNo, String status, String itemsSummary, Double totalAmount) {
            this.id = id;
            this.poNo = poNo;
            this.status = status;
            this.itemsSummary = itemsSummary;
            this.totalAmount = totalAmount;
        }

        public Long getId() {
            return id;
        }

        public String getPoNo() {
            return poNo;
        }

        public String getStatus() {
            return status;
        }

        public String getItemsSummary() {
            return itemsSummary;
        }

        public Double getTotalAmount() {
            return totalAmount;
        }
    }
}

