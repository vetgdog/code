package com.code.controller;

import com.code.dto.AuthRequest;
import com.code.dto.AuthResponse;
import com.code.entity.User;
import com.code.repository.UserRepository;
import com.code.repository.RoleRepository;
import com.code.entity.Role;
import com.code.security.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
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
        String email = normalize(req.getEmail());
        authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(email, req.getPassword()));

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        String token = jwtUtil.generateToken(user.getEmail());

        List<String> roles = (user.getRoles() == null ? java.util.Set.<Role>of() : user.getRoles())
                .stream()
                .map(Role::getName)
                .sorted(Comparator.naturalOrder())
                .collect(Collectors.toList());

        String primaryRole = roles.isEmpty() ? "ROLE_CUSTOMER" : roles.get(0);
        return new AuthResponse(token, user.getUsername(), user.getEmail(), primaryRole, roles);
    }

    @PostMapping("/register")
    public ResponseEntity<String> register(@RequestBody AuthRequest req, @RequestParam(required = false) String role) {
        String email = normalize(req.getEmail());
        String username = normalize(req.getUsername());
        String fullName = trim(req.getFullName());
        String phone = trim(req.getPhone());

        if (isBlank(email) || isBlank(username) || isBlank(fullName) || isBlank(phone) || isBlank(req.getPassword())) {
            return ResponseEntity.badRequest().body("邮箱、用户名、全名、电话和密码为必填项");
        }
        if (userRepository.existsByEmail(email)) {
            return ResponseEntity.badRequest().body("邮箱已存在");
        }
        if (userRepository.existsByUsername(username)) {
            return ResponseEntity.badRequest().body("用户名已存在");
        }

        User u = new User();
        u.setEmail(email);
        u.setUsername(username);
        u.setFullName(fullName);
        u.setPhone(phone);
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
        return ResponseEntity.ok("ok");
    }

    private String normalize(String value) {
        return trim(value).toLowerCase(Locale.ROOT);
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean isBlank(String value) {
        return trim(value).isEmpty();
    }
}