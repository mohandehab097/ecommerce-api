package com.lab.ecommerce.service;

import com.lab.ecommerce.model.Cart;
import com.lab.ecommerce.model.CartItem;
import com.lab.ecommerce.model.Order;
import com.lab.ecommerce.model.Product;
import com.lab.ecommerce.repository.OrderRepository;
import com.lab.ecommerce.repository.ProductRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private CartService cartService;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private EntityManager entityManager;

    @Mock
    private Query query;

    private OrderService orderService;

    @Test
    void checkout_computesLineTotalFromQuantityNotLineCount() {
        orderService = new OrderService(orderRepository, cartService, productRepository);
        ReflectionTestUtils.setField(orderService, "entityManager", entityManager);

        Product productA = new Product("Widget", "desc", BigDecimal.TEN, 10);
        productA.setId(1L);
        Product productB = new Product("Gadget", "desc", BigDecimal.valueOf(5), 10);
        productB.setId(2L);

        Cart cart = new Cart(42L);
        cart.getItems().add(new CartItem(cart, productA, 3));
        cart.getItems().add(new CartItem(cart, productB, 1));

        when(cartService.getCart(42L)).thenReturn(cart);
        when(entityManager.createQuery(anyString())).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        Order result = orderService.checkout(42L, null, null);

        // productA: 3 * 10 = 30, productB: 1 * 5 = 5 -> total 35
        assertThat(result.getTotal()).isEqualByComparingTo(BigDecimal.valueOf(35));
    }
}
