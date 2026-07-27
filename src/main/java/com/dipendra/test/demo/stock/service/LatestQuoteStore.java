package com.dipendra.test.demo.stock.service;

import java.time.Duration;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class LatestQuoteStore {
    private static final Logger log = LoggerFactory.getLogger(LatestQuoteStore.class);
    private static final Duration TTL = Duration.ofDays(2);
    private static final String PREFIX = "stock:latest:";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public LatestQuoteStore(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    public void put(LatestQuote quote) {
        try {
            redis.opsForValue().set(PREFIX + quote.securityId(), objectMapper.writeValueAsString(quote), TTL);
        } catch (DataAccessException | JacksonException exception) {
            log.warn("Could not cache latest quote for security {}: {}", quote.securityId(), exception.getMessage());
        }
    }

    public Optional<LatestQuote> get(String securityId) {
        try {
            String json = redis.opsForValue().get(PREFIX + securityId);
            return json == null ? Optional.empty() : Optional.of(objectMapper.readValue(json, LatestQuote.class));
        } catch (DataAccessException | JacksonException exception) {
            log.warn("Could not read latest quote for security {}: {}", securityId, exception.getMessage());
            return Optional.empty();
        }
    }
}
