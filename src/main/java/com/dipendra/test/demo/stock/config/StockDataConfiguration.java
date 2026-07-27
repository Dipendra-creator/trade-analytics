package com.dipendra.test.demo.stock.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(DhanProperties.class)
public class StockDataConfiguration {
}
