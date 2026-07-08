package com.lab.ecommerce.controller;

import com.lab.ecommerce.model.Order;
import com.lab.ecommerce.service.OrderService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
@CrossOrigin(origins = "http://localhost:4200")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping("/checkout")
    public Order checkout(@RequestParam Long customerId) {
        return orderService.checkout(customerId);
    }

    @GetMapping("/{id}")
    public Order getOrder(@PathVariable Long id, @RequestParam Long customerId) {
        return orderService.getOrderForCustomer(id, customerId);
    }

    @GetMapping
    public List<Order> getOrderHistory(@RequestParam Long customerId) {
        return orderService.getOrderHistory(customerId);
    }
}
