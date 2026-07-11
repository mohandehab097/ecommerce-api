package com.lab.ecommerce.service;

import com.lab.ecommerce.model.Cart;
import com.lab.ecommerce.model.CartItem;
import com.lab.ecommerce.model.Product;
import com.lab.ecommerce.repository.CartRepository;
import com.lab.ecommerce.repository.ProductRepository;
import org.springframework.stereotype.Service;

import java.util.NoSuchElementException;
import java.util.Optional;

@Service
public class CartService {

    private final CartRepository cartRepository;
    private final ProductRepository productRepository;

    public CartService(CartRepository cartRepository, ProductRepository productRepository) {
        this.cartRepository = cartRepository;
        this.productRepository = productRepository;
    }

    public Cart getCart(Long customerId) {
        return cartRepository.findByCustomerId(customerId)
                .orElseGet(() -> cartRepository.save(new Cart(customerId)));
    }

    public Cart addItem(Long customerId, Long productId, Integer quantity) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new NoSuchElementException("Product not found: " + productId));

        if (product.getStockQty() < quantity) {
            throw new IllegalStateException("Not enough stock for " + product.getName());
        }

        Cart cart = cartRepository.findByCustomerId(customerId)
                .orElseGet(() -> cartRepository.save(new Cart(customerId)));

        findItem(cart, productId)
                .ifPresentOrElse(
                        existing -> existing.setQuantity(existing.getQuantity() + quantity),
                        () -> cart.getItems().add(new CartItem(cart, product, quantity))
                );

        return cartRepository.save(cart);
    }

    public Cart updateItemQuantity(Long customerId, Long productId, Integer quantity) {
        Cart cart = cartRepository.findByCustomerId(customerId)
                .orElseThrow(() -> new NoSuchElementException("Cart not found for customer: " + customerId));

        CartItem item = findItem(cart, productId)
                .orElseThrow(() -> new NoSuchElementException("Item not in cart: " + productId));

        if (item.getProduct().getStockQty() < quantity) {
            throw new IllegalStateException("Not enough stock for " + item.getProduct().getName());
        }

        item.setQuantity(quantity);
        return cartRepository.save(cart);
    }

    public Cart removeItem(Long customerId, Long productId) {
        Cart cart = cartRepository.findByCustomerId(customerId)
                .orElseThrow(() -> new NoSuchElementException("Cart not found for customer: " + customerId));

        cart.getItems().removeIf(item -> item.getProduct().getId().equals(productId));
        return cartRepository.save(cart);
    }

    public void clearCart(Cart cart) {
        cart.getItems().clear();
        cartRepository.save(cart);
    }

    private Optional<CartItem> findItem(Cart cart, Long productId) {
        return cart.getItems().stream()
                .filter(item -> item.getProduct().getId().equals(productId))
                .findFirst();
    }
}
