package com.code.dto;

public class ProcurementExportRowDto {
    private final String poNo;
    private final String status;
    private final String supplierCode;
    private final String supplierName;
    private final String itemSku;
    private final String itemName;
    private final Double quantity;
    private final Double unitPrice;
    private final Double lineTotal;
    private final Double orderTotalAmount;
    private final String orderDate;
    private final String shippedAt;
    private final String receivedAt;
    private final String supplierNote;
    private final String procurementNote;
    private final String warehouseNote;

    public ProcurementExportRowDto(String poNo,
                                   String status,
                                   String supplierCode,
                                   String supplierName,
                                   String itemSku,
                                   String itemName,
                                   Double quantity,
                                   Double unitPrice,
                                   Double lineTotal,
                                   Double orderTotalAmount,
                                   String orderDate,
                                   String shippedAt,
                                   String receivedAt,
                                   String supplierNote,
                                   String procurementNote,
                                   String warehouseNote) {
        this.poNo = poNo;
        this.status = status;
        this.supplierCode = supplierCode;
        this.supplierName = supplierName;
        this.itemSku = itemSku;
        this.itemName = itemName;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.lineTotal = lineTotal;
        this.orderTotalAmount = orderTotalAmount;
        this.orderDate = orderDate;
        this.shippedAt = shippedAt;
        this.receivedAt = receivedAt;
        this.supplierNote = supplierNote;
        this.procurementNote = procurementNote;
        this.warehouseNote = warehouseNote;
    }

    public String getPoNo() {
        return poNo;
    }

    public String getStatus() {
        return status;
    }

    public String getSupplierCode() {
        return supplierCode;
    }

    public String getSupplierName() {
        return supplierName;
    }

    public String getItemSku() {
        return itemSku;
    }

    public String getItemName() {
        return itemName;
    }

    public Double getQuantity() {
        return quantity;
    }

    public Double getUnitPrice() {
        return unitPrice;
    }

    public Double getLineTotal() {
        return lineTotal;
    }

    public Double getOrderTotalAmount() {
        return orderTotalAmount;
    }

    public String getOrderDate() {
        return orderDate;
    }

    public String getShippedAt() {
        return shippedAt;
    }

    public String getReceivedAt() {
        return receivedAt;
    }

    public String getSupplierNote() {
        return supplierNote;
    }

    public String getProcurementNote() {
        return procurementNote;
    }

    public String getWarehouseNote() {
        return warehouseNote;
    }
}

