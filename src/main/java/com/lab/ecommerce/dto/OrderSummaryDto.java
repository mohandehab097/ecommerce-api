package com.lab.ecommerce.dto;

public class OrderSummaryDto {

    private Long orderId;
    private String productName;
    private Integer quantity;

    public OrderSummaryDto(Long orderId, String productName, Integer quantity) {
        this.orderId = orderId;
        this.productName = productName;
        this.quantity = quantity;
    }

    public Long getOrderId() {
        return orderId;
    }

    public String getProductName() {
        return productName;
    }

    public Integer getQuantity() {
        return quantity;
    }
}
