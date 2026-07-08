package com.lab.ecommerce.service;

import com.lab.ecommerce.model.Cart;
import com.lab.ecommerce.model.Product;
import com.lab.ecommerce.repository.CartRepository;
import com.lab.ecommerce.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    @Mock
    private CartRepository cartRepository;

    @Mock
    private ProductRepository productRepository;

    private CartService cartService;

    @Test
    void addItem_allowsAddingWhenStockIsSufficient() {
        cartService = new CartService(cartRepository, productRepository);
        Product product = new Product("Widget", "desc", BigDecimal.TEN, 10);
        product.setId(1L);
        Cart cart = new Cart(42L);

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(cartRepository.findByCustomerId(42L)).thenReturn(Optional.of(cart));
        when(cartRepository.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));

        Cart result = cartService.addItem(42L, 1L, 2);

        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().get(0).getQuantity()).isEqualTo(2);
    }

    @Test
    void addItem_rejectsAddingWhenStockIsInsufficient() {
        cartService = new CartService(cartRepository, productRepository);
        Product product = new Product("Widget", "desc", BigDecimal.TEN, 1);
        product.setId(1L);

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> cartService.addItem(42L, 1L, 5))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Not enough stock");
    }
}