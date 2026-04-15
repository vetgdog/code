package com.code.controller;

import com.code.dto.AuthRequest;
import com.code.entity.Role;
import com.code.repository.CustomerRepository;
import com.code.repository.RoleRepository;
import com.code.repository.UserRepository;
import com.code.security.JwtUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @InjectMocks
    private AuthController authController;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Test
    void registerSupplierStoresOnlyUserAccount() {
        AuthRequest request = new AuthRequest();
        request.setEmail("supplier@example.com");
        request.setUsername("supplier01");
        request.setFullName("供应商甲");
        request.setPhone("13800000000");
        request.setPassword("123456");

        Role role = new Role();
        role.setName("ROLE_SUPPLIER");

        when(userRepository.existsByEmail("supplier@example.com")).thenReturn(false);
        when(roleRepository.findByName("ROLE_SUPPLIER")).thenReturn(Optional.of(role));
        when(passwordEncoder.encode("123456")).thenReturn("encoded");

        ResponseEntity<String> response = authController.register(request, "ROLE_SUPPLIER");

        assertEquals(200, response.getStatusCodeValue());
        assertEquals("ok", response.getBody());
        verify(userRepository).save(any());
    }
}

