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
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
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

    @Test
    void loadUserByUsernameNormalizesLegacyAuthorityName() {
        User user = new User();
        user.setEmail("legacy-role@example.com");
        user.setPassword("encoded");
        Role legacyRole = new Role();
        legacyRole.setName("customer");
        user.setRoles(Set.of(legacyRole));

        when(userRepository.findByEmail("legacy-role@example.com")).thenReturn(Optional.of(user));
        when(customerRepository.existsByEmail("legacy-role@example.com")).thenReturn(false);

        UserDetails details = customUserDetailsService.loadUserByUsername("legacy-role@example.com");

        assertEquals(1, details.getAuthorities().size());
        assertTrue(details.getAuthorities().stream().anyMatch(a -> "ROLE_CUSTOMER".equals(a.getAuthority())));
        verify(userRepository, never()).save(any(User.class));
    }
}

