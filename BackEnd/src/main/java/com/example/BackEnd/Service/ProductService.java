package com.example.BackEnd.Service;
    
    import java.util.List;
    import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.BackEnd.Model.Product;
import com.example.BackEnd.Repository.ProductRepository;
    
    @Service
    public class ProductService {
    
        @Autowired
        private ProductRepository productRepository;
    
        public Product createProduct(Product product) {
            return productRepository.save(product);
        }
    
        public List<Product> getAllProducts() {
            return productRepository.findAll();
        }
    
        public Product getProductById(Long id) {
            Optional<Product> product = productRepository.findById(id);
            return product.orElse(null);
        }
    
        public Product updateProduct(Long id, Product productDetails) {
            Optional<Product> product = productRepository.findById(id);
            if (product.isPresent()) {
                Product existingProduct = product.get();
                existingProduct.setName(productDetails.getName());
                existingProduct.setDescription(productDetails.getDescription());
                existingProduct.setPrice(productDetails.getPrice());
                return productRepository.save(existingProduct);
            }
            return null;
        }
    
        public boolean deleteProduct(Long id) {
            Optional<Product> product = productRepository.findById(id);
            if (product.isPresent()) {
                productRepository.delete(product.get());
                return true;
            }
            return false;
        }
    }