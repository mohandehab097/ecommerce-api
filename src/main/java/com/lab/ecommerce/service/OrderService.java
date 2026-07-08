package com.lab.ecommerce.service;

import com.lab.ecommerce.model.Cart;
import com.lab.ecommerce.model.CartItem;
import com.lab.ecommerce.model.Order;
import com.lab.ecommerce.model.OrderItem;
import com.lab.ecommerce.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.NoSuchElementException;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final CartService cartService;

    public OrderService(OrderRepository orderRepository, CartService cartService) {
        this.orderRepository = orderRepository;
        this.cartService = cartService;
    }

    @Transactional
    public Order checkout(Long customerId, String idempotencyKey) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            var existing = orderRepository.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                return existing.get();
            }
        }

        Cart cart = cartService.getCart(customerId);

        if (cart.getItems().isEmpty()) {
            throw new IllegalStateException("Cannot checkout an empty cart");
        }

        Order order = new Order(customerId);
        order.setIdempotencyKey(idempotencyKey != null && !idempotencyKey.isBlank() ? idempotencyKey : null);
        BigDecimal total = BigDecimal.ZERO;

        for (CartItem item : cart.getItems()) {
            int remaining = item.getProduct().getStockQty() - item.getQuantity();
            if (remaining < 0) {
                throw new IllegalStateException("Not enough stock for " + item.getProduct().getName());
            }

            BigDecimal lineTotal = item.getProduct().getPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
            total = total.add(lineTotal);
            order.getItems().add(new OrderItem(order, item.getProduct(), item.getQuantity(), item.getProduct().getPrice()));
            item.getProduct().setStockQty(remaining);
        }

        order.setTotal(total);
        Order saved = orderRepository.save(order);
        cartService.clearCart(cart);
        return saved;
    }

    public Order getOrderForCustomer(Long orderId, Long customerId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NoSuchElementException("Order not found: " + orderId));

        if (!order.getCustomerId().equals(customerId)) {
            throw new SecurityException("Order " + orderId + " does not belong to customer " + customerId);
        }

        return order;
    }

    public List<Order> getOrderHistory(Long customerId) {
        return orderRepository.findByCustomerId(customerId);
    }
}
