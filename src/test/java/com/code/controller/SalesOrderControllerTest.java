package com.code.controller;

import com.code.entity.SalesOrder;
import com.code.repository.ProductionPlanRepository;
import com.code.repository.SalesOrderRepository;
import com.code.repository.SalesRecordRepository;
import com.code.service.OrderWorkflowService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SalesOrderControllerTest {

    @InjectMocks
    private SalesOrderController salesOrderController;

    @Mock
    private SalesOrderRepository salesOrderRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private ProductionPlanRepository productionPlanRepository;

    @Mock
    private SalesRecordRepository salesRecordRepository;

    @Mock
    private OrderWorkflowService orderWorkflowService;

    @Test
    void listReturnsOrdersFromNewestToOldest() {
        SalesOrder older = new SalesOrder();
        older.setId(1L);
        older.setOrderNo("SO-OLD");
        older.setOrderDate(LocalDateTime.of(2026, 4, 10, 8, 0));
        older.setCreatedAt(LocalDateTime.of(2026, 4, 10, 8, 0));

        SalesOrder newer = new SalesOrder();
        newer.setId(2L);
        newer.setOrderNo("SO-NEW");
        newer.setOrderDate(LocalDateTime.of(2026, 4, 15, 8, 0));
        newer.setCreatedAt(LocalDateTime.of(2026, 4, 15, 8, 0));

        when(salesOrderRepository.findAll()).thenReturn(List.of(older, newer));

        List<SalesOrder> result = salesOrderController.list();

        assertEquals(2, result.size());
        assertEquals("SO-NEW", result.get(0).getOrderNo());
        assertEquals("SO-OLD", result.get(1).getOrderNo());
    }
}

