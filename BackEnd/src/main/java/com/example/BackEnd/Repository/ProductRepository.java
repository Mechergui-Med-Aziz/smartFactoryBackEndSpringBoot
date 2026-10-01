package com.example.BackEnd.Repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.BackEnd.Model.Product;

public interface ProductRepository extends JpaRepository<Product, Long> {

    
}   