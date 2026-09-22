package com.bytevault.media.service;

import com.bytevault.media.entity.Product;
import com.bytevault.media.repository.ProductRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class ProductService {

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }

    public Optional<Product> getProductById(Long id) {
        return productRepository.findById(id);
    }

    public Product createProduct(Product product) {
        return productRepository.save(product);
    }

    public Optional<Product> updateProduct(Long id, Product productDetails) {

        return productRepository.findById(id).map(product -> {

            product.setName(productDetails.getName());
            product.setType(productDetails.getType());
            product.setPrice(productDetails.getPrice());
            product.setDownloadUrl(productDetails.getDownloadUrl());

            return productRepository.save(product);
        });
    }

    public boolean deleteProduct(Long id) {

        if (productRepository.existsById(id)) {
            productRepository.deleteById(id);
            return true;
        }

        return false;
    }
}