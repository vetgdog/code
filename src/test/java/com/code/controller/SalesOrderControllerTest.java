package com.code.controller;

import com.code.entity.SalesRecord;
import com.code.entity.SalesOrder;
import com.code.entity.User;
import com.code.repository.ProductionPlanRepository;
import com.code.repository.SalesOrderRepository;
import com.code.repository.SalesRecordRepository;
import com.code.repository.UserRepository;
import com.code.service.OrderWorkflowService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
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

    @Mock
    private UserRepository userRepository;

    @Mock
    private Authentication authentication;

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

    @Test
    void exportSalesRecordsUsesUtf8BomForExcelAndKeepsChineseStatusReadable() {
        SalesRecord record = new SalesRecord();
        record.setId(1L);
        record.setRecordNo("SR-001");
        record.setOrderNo("SO-001");
        record.setCustomerName("杭州客户");
        record.setShippingAddress("浙江杭州");
        record.setTotalAmount(1280.0);
        record.setStatus("已完成");
        record.setCreatedBy(9L);
        record.setCreatedByName("销售甲");
        record.setCreatedAt(LocalDateTime.of(2026, 5, 3, 10, 30));

        User currentUser = new User();
        currentUser.setId(9L);
        currentUser.setEmail("sales@example.com");

        doReturn(List.of(new SimpleGrantedAuthority("ROLE_SALES_MANAGER"))).when(authentication).getAuthorities();
        when(authentication.getName()).thenReturn("sales@example.com");
        when(userRepository.findByEmailIgnoreCase("sales@example.com")).thenReturn(Optional.of(currentUser));
        when(salesRecordRepository.findByCreatedByOrderByCreatedAtDesc(9L)).thenReturn(List.of(record));

        ResponseEntity<byte[]> response = salesOrderController.exportSalesRecords(null, null, authentication);

        assertEquals(200, response.getStatusCodeValue());
        assertTrue(String.valueOf(response.getHeaders().getFirst("Content-Disposition")).contains("sales-records-"));
        byte[] body = response.getBody();
        assertNotNull(body);
        assertTrue(body.length > 3);
        assertEquals((byte) 0xEF, body[0]);
        assertEquals((byte) 0xBB, body[1]);
        assertEquals((byte) 0xBF, body[2]);
        String csv = new String(body, 3, body.length - 3, StandardCharsets.UTF_8);
        assertTrue(csv.contains("已完成"));
        assertTrue(csv.contains("杭州客户"));
    }
}

