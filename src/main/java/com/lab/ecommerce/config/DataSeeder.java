package com.lab.ecommerce.config;

import com.lab.ecommerce.model.Customer;
import com.lab.ecommerce.model.Product;
import com.lab.ecommerce.repository.CustomerRepository;
import com.lab.ecommerce.repository.ProductRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class DataSeeder implements CommandLineRunner {

    private final ProductRepository productRepository;
    private final CustomerRepository customerRepository;

    public DataSeeder(ProductRepository productRepository, CustomerRepository customerRepository) {
        this.productRepository = productRepository;
        this.customerRepository = customerRepository;
    }

    @Override
    public void run(String... args) {
        productRepository.save(new Product("Wireless Mouse", "Ergonomic 2.4GHz mouse", new BigDecimal("19.99"), 50));
        productRepository.save(new Product("Mechanical Keyboard", "Hot-swappable, RGB", new BigDecimal("79.99"), 20));
        productRepository.save(new Product("USB-C Hub", "7-in-1 hub", new BigDecimal("34.50"), 3));
        productRepository.save(new Product("Laptop Stand", "Aluminum, adjustable", new BigDecimal("29.00"), 15));
        productRepository.save(new Product("4K Webcam", "Autofocus, low-light", new BigDecimal("59.99"), 1));

        customerRepository.save(new Customer("Alice Ahmed", "alice@example.com"));
        customerRepository.save(new Customer("Bilal Khan", "bilal@example.com"));
    }
}
