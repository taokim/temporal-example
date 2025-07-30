package com.example.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@EnableTransactionManagement
public class DatabaseConfig {
    // Spring Boot auto-configures DataSource and JdbcTemplate
    // This class ensures transaction management is enabled
}