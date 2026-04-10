package com.code.controller;

import com.code.dto.AuthRequest;
import com.code.dto.AuthResponse;
import com.code.entity.User;
import com.code.entity.Customer;
import com.code.repository.UserRepository;
import com.code.repository.CustomerRepository;
import com.code.repository.RoleRepository;
import com.code.entity.Role;
import com.code.security.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
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
    private CustomerRepository customerRepository;

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

        User u = new User();
        u.setEmail(email);
        u.setUsername(username);
        u.setFullName(fullName);
        u.setPhone(phone);
        u.setPassword(passwordEncoder.encode(req.getPassword()));

        String requestedRole = trim(role).toUpperCase(Locale.ROOT);
        String roleName = requestedRole.isEmpty() ? "ROLE_CUSTOMER" : (requestedRole.startsWith("ROLE_") ? requestedRole : "ROLE_" + requestedRole);
        Role r = roleRepository.findByName(roleName).orElseGet(() -> {
            Role nr = new Role();
            nr.setName(roleName);
            nr.setDescription(roleName + " created");
            return roleRepository.save(nr);
        });
        u.setRoles(java.util.Set.of(r));

        if ("ROLE_CUSTOMER".equals(roleName) && customerRepository.findByEmail(email).isEmpty()) {
            Customer customer = new Customer();
            customer.setCode(generateCustomerCode());
            customer.setName(fullName);
            customer.setContact(fullName);
            customer.setPhone(phone);
            customer.setEmail(email);
            customerRepository.save(customer);
        }

        try {
            userRepository.save(u);
            return ResponseEntity.ok("ok");
        } catch (DataIntegrityViolationException ex) {
            String rootMessage = extractRootMessage(ex).toLowerCase(Locale.ROOT);
            if (rootMessage.contains("user_email") || rootMessage.contains("email") || rootMessage.contains("duplicate")) {
                return ResponseEntity.badRequest().body("该邮箱已注册，请直接登录");
            }
            if (rootMessage.contains("doesn't have a default value") && rootMessage.contains("user_email")) {
                return ResponseEntity.badRequest().body("注册失败：邮箱字段异常，请联系管理员检查用户表结构");
            }
            return ResponseEntity.badRequest().body("注册失败：提交信息不符合系统规则，请检查后重试");
        }
    }

    private String extractRootMessage(Throwable throwable) {
        Throwable cursor = throwable;
        while (cursor.getCause() != null) {
            cursor = cursor.getCause();
        }
        return cursor.getMessage() == null ? "" : cursor.getMessage();
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

    private String generateCustomerCode() {
        String code;
        do {
            code = "CUS-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        } while (customerRepository.existsByCode(code));
        return code;
    }
}