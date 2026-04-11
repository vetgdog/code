package com.code.controller;

import com.code.entity.Customer;
import com.code.entity.Product;
import com.code.entity.SalesOrder;
import com.code.repository.CustomerRepository;
import com.code.repository.ProductRepository;
import com.code.repository.SalesOrderRepository;
import com.code.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerControllerTest {

    @InjectMocks
    private CustomerController customerController;

    @Mock
    private SalesOrderRepository salesOrderRepository;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private Authentication authentication;

    @Test
    void listMyOrdersWhenNoCustomerBindingReturnsEmptyList() {
        when(authentication.getName()).thenReturn("new-customer@example.com");
        when(customerRepository.findByEmail("new-customer@example.com")).thenReturn(Optional.empty());

        List<SalesOrder> result = customerController.listMyOrders(authentication);

        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(salesOrderRepository, never()).findByCustomerId(any());
    }

    @Test
    void createOrderWhenShippingAddressMissingReturnsBadRequest() {
        when(authentication.getName()).thenReturn("customer@example.com");
        Customer customer = new Customer();
        customer.setId(1L);
        when(customerRepository.findByEmail("customer@example.com")).thenReturn(Optional.of(customer));

        CustomerController.CustomerOrderRequest request = new CustomerController.CustomerOrderRequest();
        CustomerController.CustomerOrderItemRequest item = new CustomerController.CustomerOrderItemRequest();
        item.setProductId(10L);
        item.setQuantity(2.0);
        request.setItems(List.of(item));
        request.setShippingAddress("   ");

        ResponseEntity<?> response = customerController.createOrder(request, authentication);

        assertEquals(400, response.getStatusCodeValue());
        assertEquals("请填写收货地址", response.getBody());
    }

    @Test
    void createOrderUsesProductMasterPriceInsteadOfClientPrice() {
        when(authentication.getName()).thenReturn("customer@example.com");

        Customer customer = new Customer();
        customer.setId(1L);
        when(customerRepository.findByEmail("customer@example.com")).thenReturn(Optional.of(customer));

        Product product = new Product();
        product.setId(99L);
        product.setUnitPrice(12.5);
        when(productRepository.findById(99L)).thenReturn(Optional.of(product));

        when(salesOrderRepository.save(any(SalesOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CustomerController.CustomerOrderRequest request = new CustomerController.CustomerOrderRequest();
        request.setShippingAddress("Shanghai Pudong");
        CustomerController.CustomerOrderItemRequest item = new CustomerController.CustomerOrderItemRequest();
        item.setProductId(99L);
        item.setQuantity(2.0);
        item.setUnitPrice(9999.0);
        request.setItems(List.of(item));

        ResponseEntity<?> response = customerController.createOrder(request, authentication);

        assertEquals(200, response.getStatusCodeValue());
        ArgumentCaptor<SalesOrder> orderCaptor = ArgumentCaptor.forClass(SalesOrder.class);
        verify(salesOrderRepository).save(orderCaptor.capture());
        SalesOrder saved = orderCaptor.getValue();
        assertEquals(25.0, saved.getTotalAmount());
        assertEquals("Shanghai Pudong", saved.getShippingAddress());
        assertEquals(12.5, saved.getItems().get(0).getUnitPrice());
    }

    @Test
    void createOrderWithoutCustomerProfileThrowsForbidden() {
        when(authentication.getName()).thenReturn("nocustomer@example.com");
        when(customerRepository.findByEmail("nocustomer@example.com")).thenReturn(Optional.empty());

        CustomerController.CustomerOrderRequest request = new CustomerController.CustomerOrderRequest();
        request.setShippingAddress("Shanghai Pudong");
        CustomerController.CustomerOrderItemRequest item = new CustomerController.CustomerOrderItemRequest();
        item.setProductId(99L);
        item.setQuantity(1.0);
        request.setItems(List.of(item));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> customerController.createOrder(request, authentication));

        assertEquals(403, exception.getStatus().value());
        assertEquals("当前账号没有客户下单权限，请重新登录客户账号后重试", exception.getReason());
    }
}

