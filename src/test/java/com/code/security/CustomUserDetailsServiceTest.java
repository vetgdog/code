package com.code.security;

import com.code.entity.Role;
import com.code.entity.User;
import com.code.repository.CustomerRepository;
import com.code.repository.RoleRepository;
import com.code.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @InjectMocks
    private CustomUserDetailsService customUserDetailsService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private CustomerRepository customerRepository;

    @Test
    void loadUserByUsernameBackfillsCustomerRoleWhenLegacyUserHasNoRoles() {
        User user = new User();
        user.setEmail("legacy-customer@example.com");
        user.setPassword("encoded");
        user.setRoles(Collections.emptySet());

        when(userRepository.findByEmail("legacy-customer@example.com")).thenReturn(Optional.of(user));
        when(customerRepository.existsByEmail("legacy-customer@example.com")).thenReturn(true);

        Role customerRole = new Role();
        customerRole.setId(1L);
        customerRole.setName("ROLE_CUSTOMER");
        when(roleRepository.findByName("ROLE_CUSTOMER")).thenReturn(Optional.of(customerRole));

        UserDetails details = customUserDetailsService.loadUserByUsername("legacy-customer@example.com");

        assertTrue(details.getAuthorities().stream().anyMatch(a -> "ROLE_CUSTOMER".equals(a.getAuthority())));
        verify(userRepository).save(any(User.class));
    }
}

