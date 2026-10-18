package io.github.derekdiliu.dealdrop.order;

import io.github.derekdiliu.dealdrop.order.OrderController.OrderResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Back-office view of every customer's orders; admin role required (see SecurityConfig). Read-only.
 */
@RestController
@RequestMapping("/api/order/admin/orders")
class AdminOrderController {

    private final OrderService orders;

    AdminOrderController(OrderService orders) {
        this.orders = orders;
    }

    @GetMapping
    Page<OrderResponse> list(
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) UUID dealId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return orders.all(status, dealId, pageable).map(OrderResponse::from);
    }

    @GetMapping("/{id}")
    OrderResponse get(@PathVariable UUID id) {
        return OrderResponse.from(orders.get(id));
    }
}
