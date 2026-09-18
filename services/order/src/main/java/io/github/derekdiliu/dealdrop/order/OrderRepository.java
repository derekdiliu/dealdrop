package io.github.derekdiliu.dealdrop.order;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface OrderRepository extends JpaRepository<Order, UUID> {

    List<Order> findTop50ByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<Order> findByIdAndUserId(UUID id, UUID userId);
}
