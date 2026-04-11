package com.code.security;

import com.code.entity.Role;
import com.code.entity.User;
import com.code.repository.CustomerRepository;
import com.code.repository.RoleRepository;
import com.code.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email).orElseThrow(() -> new UsernameNotFoundException("User not found"));
        return new org.springframework.security.core.userdetails.User(user.getEmail(), user.getPassword(), mapRolesToAuthorities(user));
    }

    private Collection<? extends GrantedAuthority> mapRolesToAuthorities(User u) {
        Set<Role> roles = u.getRoles() == null ? new HashSet<>() : new HashSet<>(u.getRoles());
        boolean hasCustomerProfile = u.getEmail() != null
                && customerRepository.existsByEmail(u.getEmail().toLowerCase(Locale.ROOT));
        boolean hasCustomerRole = roles.stream()
                .map(Role::getName)
                .map(this::normalizeRoleName)
                .anyMatch("ROLE_CUSTOMER"::equals);

        // Auto-heal legacy data so customers never lose order permissions.
        if (hasCustomerProfile && !hasCustomerRole) {
            Role customerRole = roleRepository.findByName("ROLE_CUSTOMER").orElseGet(() -> {
                Role role = new Role();
                role.setName("ROLE_CUSTOMER");
                role.setDescription("Default customer role");
                return roleRepository.save(role);
            });
            roles.add(customerRole);
            u.setRoles(roles);
            userRepository.save(u);
        }

        if (roles.isEmpty()) {
            return java.util.List.of(new SimpleGrantedAuthority("ROLE_USER"));
        }
        return roles.stream()
                .map(Role::getName)
                .map(this::normalizeRoleName)
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());
    }

    private String normalizeRoleName(String roleName) {
        if (roleName == null || roleName.isBlank()) {
            return "ROLE_USER";
        }
        String normalized = roleName.trim().toUpperCase(Locale.ROOT);
        return normalized.startsWith("ROLE_") ? normalized : "ROLE_" + normalized;
    }
}


