package io.github.derekdiliu.dealdrop.catalog.deal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

interface DealRepository extends JpaRepository<Deal, UUID> {

    List<Deal> findByStatusAndEndsAtAfterOrderByStartsAtAsc(DealStatus status, Instant now);
}
