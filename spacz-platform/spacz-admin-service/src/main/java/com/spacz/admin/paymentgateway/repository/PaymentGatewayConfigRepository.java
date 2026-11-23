package com.spacz.admin.paymentgateway.repository;

import com.spacz.admin.paymentgateway.entity.PaymentGatewayConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentGatewayConfigRepository extends JpaRepository<PaymentGatewayConfig, Long> {
    Optional<PaymentGatewayConfig> findByProvider(String provider);
}
