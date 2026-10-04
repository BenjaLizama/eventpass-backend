package cl.eventpass.ms_orders.service;

import cl.eventpass.ms_orders.entity.OrderEntity;

public interface OrderCapacityService {
    void releaseCapacity(OrderEntity order);
}
