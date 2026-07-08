package com.lab.ecommerce.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    private String description;

    private BigDecimal price;

    private Integer stockQty;

    @Version
    private Long version;

    public Product(String name, String description, BigDecimal price, Integer stockQty) {
        this.name = name;
        this.description = description;
        this.price = price;
        this.stockQty = stockQty;
    }
}
