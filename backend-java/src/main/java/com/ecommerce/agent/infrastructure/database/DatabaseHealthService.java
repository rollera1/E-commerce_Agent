package com.ecommerce.agent.infrastructure.database;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** 通过执行简单查询检查 Java 与 MySQL 的真实连接。 */
@Service
public class DatabaseHealthService {

    private final JdbcTemplate jdbcTemplate;

    public DatabaseHealthService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public DatabaseHealthResponse checkHealth() {
        // 不只检查连接池状态，而是要求数据库真正执行一次 SQL。
        Integer probe = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
        return new DatabaseHealthResponse("ok", "mysql", probe == null ? 0 : probe);
    }
}
