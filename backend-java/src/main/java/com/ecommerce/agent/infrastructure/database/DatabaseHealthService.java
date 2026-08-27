package com.ecommerce.agent.infrastructure.database;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class DatabaseHealthService {

    private final JdbcTemplate jdbcTemplate;

    public DatabaseHealthService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public DatabaseHealthResponse checkHealth() {
        Integer probe = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
        return new DatabaseHealthResponse("ok", "mysql", probe == null ? 0 : probe);
    }
}
