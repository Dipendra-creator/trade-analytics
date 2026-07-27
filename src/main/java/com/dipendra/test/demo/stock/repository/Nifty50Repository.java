package com.dipendra.test.demo.stock.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dipendra.test.demo.stock.domain.Nifty50Constituent;

public interface Nifty50Repository extends JpaRepository<Nifty50Constituent, Long> {
    List<Nifty50Constituent> findByActiveTrueOrderByRankAsc();
    Optional<Nifty50Constituent> findBySecurityIdAndActiveTrue(String securityId);
    Optional<Nifty50Constituent> findBySymbolIgnoreCaseAndActiveTrue(String symbol);
}
