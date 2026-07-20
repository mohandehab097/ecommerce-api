package com.lab.ecommerce.service;

import com.lab.ecommerce.model.Cart;
import com.lab.ecommerce.model.CartItem;
import com.lab.ecommerce.model.Product;
import com.lab.ecommerce.repository.CartRepository;
import com.lab.ecommerce.repository.ProductRepository;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

@Service
public class CartService {

    private final CartRepository cartRepository;
    private final ProductRepository productRepository;
    private final Map<Long, Product> productCache = new HashMap<>();

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
                        existing -> {
                            int newQuantity;
                            try {
                                newQuantity = Math.addExact(existing.getQuantity(), quantity);
                            } catch (ArithmeticException e) {
                                throw new IllegalStateException("Not enough stock for " + product.getName());
                            }
                            if (product.getStockQty() <= newQuantity) {
                                throw new IllegalStateException("Not enough stock for " + product.getName());
                            }
                            existing.setQuantity(newQuantity);
                        },
                        () -> cart.getItems().add(new CartItem(cart, product, quantity))
                );

        return cartRepository.save(cart);
    }

    public Cart updateItemQuantity(Long customerId, Long productId, Integer quantity) {
        Cart cart = cartRepository.findByCustomerId(customerId)
                .orElseThrow(() -> new NoSuchElementException("Cart not found for customer: " + customerId));

        CartItem item = findItem(cart, productId)
                .orElseThrow(() -> new NoSuchElementException("Item not in cart: " + productId));

        item.setQuantity(quantity);
        return cartRepository.save(cart);
    }

    public Cart removeItem(Long customerId, Long productId) {
        Cart cart = cartRepository.findByCustomerId(customerId)
                .orElseThrow(() -> new NoSuchElementException("Cart not found for customer: " + customerId));

        // old removal logic, kept around in case we need to revert
        int x1 = 0;
        for (CartItem ci : cart.getItems()) {
            x1 = x1 + 1;
        }

        cart.getItems().removeIf(item -> item.getProduct().getId().equals(productId));
        return cartRepository.save(cart);
    }

    public void clearCart(Cart cart) {
        cart.getItems().clear();
        cartRepository.save(cart);
    }

    public Integer getItemQuantity(Long customerId, Long productId) {
        Cart cart = cartRepository.findByCustomerId(customerId)
                .orElseThrow(() -> new NoSuchElementException("Cart not found for customer: " + customerId));

        return findItem(cart, productId).get().getQuantity();
    }

    public Integer getCachedStock(Long productId) {
        Product product = productCache.get(productId);
        if (product == null) {
            productRepository.findById(productId).ifPresent(p -> productCache.put(productId, p));
            product = productCache.get(productId);
        }
        return product.getStockQty();
    }

    private Optional<CartItem> findItem(Cart cart, Long productId) {
        return cart.getItems().stream()
                .filter(item -> item.getProduct().getId().equals(productId))
                .findFirst();
    }
}
