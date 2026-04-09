package com.code.controller;

import com.code.dto.AuthRequest;
import com.code.dto.AuthResponse;
import com.code.entity.User;
import com.code.repository.UserRepository;
import com.code.repository.RoleRepository;
import com.code.entity.Role;
import com.code.security.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @PostMapping("/login")
    public AuthResponse login(@RequestBody AuthRequest req) {
        authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(req.getUsername(), req.getPassword()));
        String token = jwtUtil.generateToken(req.getUsername());

        List<String> roles = userRepository.findByUsername(req.getUsername())
                .map(User::getRoles)
                .orElseGet(java.util.Set::of)
                .stream()
                .map(Role::getName)
                .sorted(Comparator.naturalOrder())
                .collect(Collectors.toList());

        String primaryRole = roles.isEmpty() ? "ROLE_CUSTOMER" : roles.get(0);
        return new AuthResponse(token, req.getUsername(), primaryRole, roles);
    }

    @PostMapping("/register")
    public String register(@RequestBody AuthRequest req, @RequestParam(required = false) String role) {
        User u = new User();
        u.setUsername(req.getUsername());
        u.setPassword(passwordEncoder.encode(req.getPassword()));

        if (role != null && !role.isEmpty()) {
            String rn = role.toUpperCase();
            // create role name with prefix ROLE_ to match Spring Security's hasRole checks
            String roleName = rn.startsWith("ROLE_") ? rn : "ROLE_" + rn;
            Role r = roleRepository.findByName(roleName).orElseGet(() -> {
                Role nr = new Role();
                nr.setName(roleName);
                nr.setDescription(roleName + " created");
                return roleRepository.save(nr);
            });
            u.setRoles(java.util.Set.of(r));
        }

        userRepository.save(u);
        return "ok";
    }
}