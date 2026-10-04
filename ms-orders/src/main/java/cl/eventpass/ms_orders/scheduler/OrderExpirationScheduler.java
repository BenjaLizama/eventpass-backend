package cl.eventpass.ms_orders.scheduler;

import cl.eventpass.ms_orders.service.OrderExpirationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderExpirationScheduler {

    private final OrderExpirationService orderExpirationService;

    @Scheduled(fixedDelay = 60000)
    public void expireOrders() {
        log.debug("Ejecutando proceso de expiración de órdenes.");

        orderExpirationService.expireOrders();
    }
}
