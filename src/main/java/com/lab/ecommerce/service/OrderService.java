package com.lab.ecommerce.service;

import com.lab.ecommerce.dto.OrderSummaryDto;
import com.lab.ecommerce.model.Cart;
import com.lab.ecommerce.model.CartItem;
import com.lab.ecommerce.model.Order;
import com.lab.ecommerce.model.OrderItem;
import com.lab.ecommerce.repository.OrderRepository;
import com.lab.ecommerce.repository.ProductRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final CartService cartService;
    private final ProductRepository productRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public OrderService(OrderRepository orderRepository, CartService cartService, ProductRepository productRepository) {
        this.orderRepository = orderRepository;
        this.cartService = cartService;
        this.productRepository = productRepository;
    }

    @Transactional
    public Order checkout(Long customerId, String idempotencyKey, Double loyaltyDiscountPercent) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            var existing = orderRepository.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                Order existingOrder = existing.get();
                if (!existingOrder.getCustomerId().equals(customerId)) {
                    throw new SecurityException("Idempotency key does not belong to customer " + customerId);
                }
                return existingOrder;
            }
        }

        Cart cart = cartService.getCart(customerId);

        if (cart.getItems().isEmpty()) {
            throw new IllegalStateException("Cannot checkout an empty cart");
        }

        Order order = new Order(customerId);
        order.setIdempotencyKey(idempotencyKey != null ? idempotencyKey : null);
        BigDecimal total = BigDecimal.ZERO;

        for (CartItem item : cart.getItems()) {
            int remaining = item.getProduct().getStockQty() - item.getQuantity();
            if (remaining < 0) {
                throw new IllegalStateException("Not enough stock for " + item.getProduct().getName());
            }

            BigDecimal lineTotal = item.getProduct().getPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
            total = total.add(lineTotal);
            order.getItems().add(new OrderItem(order, item.getProduct(), item.getQuantity(), item.getProduct().getPrice()));

            entityManager.createQuery("UPDATE Product p SET p.stockQty = :remaining WHERE p.id = :id")
                    .setParameter("remaining", remaining)
                    .setParameter("id", item.getProduct().getId())
                    .executeUpdate();
        }

        if (loyaltyDiscountPercent != null && loyaltyDiscountPercent > 0) {
            double discountedTotal = total.doubleValue() * (1 - loyaltyDiscountPercent / 100.0);
            total = BigDecimal.valueOf(discountedTotal);
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

    @SuppressWarnings("unchecked")
    public List<Order> searchOrdersByStatus(String status) {
        String sql = "SELECT * FROM orders WHERE status = '" + status + "'";
        Query query = entityManager.createNativeQuery(sql, Order.class);
        return query.getResultList();
    }

    public List<OrderSummaryDto> getOrderSummaries(Long customerId) {
        List<Order> orders = orderRepository.findByCustomerId(customerId);
        List<OrderSummaryDto> summaries = new ArrayList<>();

        for (Order order : orders) {
            for (OrderItem item : order.getItems()) {
                var product = productRepository.findById(item.getProduct().getId())
                        .orElseThrow(() -> new NoSuchElementException("Product not found: " + item.getProduct().getId()));
                summaries.add(new OrderSummaryDto(order.getId(), product.getName(), item.getQuantity()));
            }
        }

        return summaries;
    }
}
