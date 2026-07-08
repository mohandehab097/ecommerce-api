package com.lab.ecommerce.controller;

import com.lab.ecommerce.dto.AddCartItemRequest;
import com.lab.ecommerce.model.Cart;
import com.lab.ecommerce.service.CartService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/cart")
@CrossOrigin(origins = "http://localhost:4200")
public class CartController {

    private static final Logger log = LoggerFactory.getLogger(CartController.class);

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping("/{customerId}")
    public Cart getCart(@PathVariable Long customerId) {
        Cart cart = cartService.getCart(customerId);
        log.info("Fetched cart for customer {} with {} item lines", customerId, cart.getItems().size());
        return cart;
    }

    @PostMapping("/{customerId}/items")
    public Cart addItem(@PathVariable Long customerId, @Valid @RequestBody AddCartItemRequest request) {
        return cartService.addItem(customerId, request.getProductId(), request.getQuantity());
    }

    @DeleteMapping("/{customerId}/items/{productId}")
    public Cart removeItem(@PathVariable Long customerId, @PathVariable Long productId) {
        return cartService.removeItem(customerId, productId);
    }
}
