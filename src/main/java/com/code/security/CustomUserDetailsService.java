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
        if (u.getRoles() == null || u.getRoles().isEmpty()) {
            // Backward compatibility: old customer accounts may miss user_roles data.
            if (u.getEmail() != null && customerRepository.existsByEmail(u.getEmail().toLowerCase())) {
                Role customerRole = roleRepository.findByName("ROLE_CUSTOMER").orElseGet(() -> {
                    Role role = new Role();
                    role.setName("ROLE_CUSTOMER");
                    role.setDescription("Default customer role");
                    return roleRepository.save(role);
                });
                u.setRoles(Set.of(customerRole));
                userRepository.save(u);
                return java.util.List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
            }
            return java.util.List.of(new SimpleGrantedAuthority("ROLE_USER"));
        }
        return u.getRoles().stream().map(Role::getName).map(SimpleGrantedAuthority::new).collect(Collectors.toList());
    }
}


