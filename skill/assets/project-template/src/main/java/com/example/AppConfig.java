package com.example;

import cc.jfire.jfire.core.prepare.annotation.ComponentScan;
import cc.jfire.jfire.core.prepare.annotation.EnableAutoConfiguration;
import cc.jfire.jfire.core.prepare.annotation.PropertyPath;
import cc.jfire.jfire.core.prepare.annotation.configuration.Bean;
import cc.jfire.jfire.core.prepare.annotation.configuration.Configuration;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;

/**
 * 应用配置类
 * 必须标注 @Configuration，作为 ApplicationContext.boot() 的启动类
 */
@Configuration
@EnableAutoConfiguration  // 启用自动配置（会自动装配 Jsql 相关 Bean）
@ComponentScan("com.example")  // 扫描组件包
@PropertyPath("classpath:application.yml")  // 配置文件路径
public class AppConfig {

    /**
     * 配置数据源 Bean（必须）
     * Jsql Starter 需要一个 DataSource Bean 才能自动装配
     */
    @Bean
    public DataSource dataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:mysql://localhost:3306/mydb?useSSL=false&serverTimezone=UTC");
        config.setUsername("root");
        config.setPassword("password");
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");

        // 连接池配置
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(5);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);

        return new HikariDataSource(config);
    }
}
