package com.code.controller;

import com.code.entity.Role;
import com.code.entity.User;
import com.code.repository.RoleRepository;
import com.code.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private static final Set<String> INTERNAL_ROLE_NAMES = Set.of(
            "ROLE_ADMIN",
            "ROLE_SALES_MANAGER",
            "ROLE_PROCUREMENT_MANAGER",
            "ROLE_PRODUCTION_MANAGER",
            "ROLE_WAREHOUSE_MANAGER",
            "ROLE_QUALITY_INSPECTOR"
    );

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminController(UserRepository userRepository,
                           RoleRepository roleRepository,
                           PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping("/roles")
    public List<RoleOptionView> listInternalRoles() {
        return roleRepository.findAll().stream()
                .map(Role::getName)
                .map(this::normalizeRole)
                .filter(INTERNAL_ROLE_NAMES::contains)
                .sorted()
                .map(role -> new RoleOptionView(role, resolveRoleLabel(role)))
                .collect(Collectors.toList());
    }

    @GetMapping("/users")
    public List<UserManagementView> listUsers(@RequestParam(required = false) String keyword,
                                              @RequestParam(required = false) String role,
                                              @RequestParam(required = false) Boolean enabled) {
        String normalizedKeyword = normalize(keyword);
        String normalizedRole = normalizeRole(role);
        return userRepository.findAll().stream()
                .filter(this::isInternalUser)
                .filter(user -> normalizedRole.isEmpty() || userRoleNames(user).contains(normalizedRole))
                .filter(user -> enabled == null || enabled.equals(user.getEnabled()))
                .filter(user -> normalizedKeyword.isEmpty() || matchesKeyword(user, normalizedKeyword))
                .sorted(Comparator.comparing(User::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(User::getId, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(this::toView)
                .collect(Collectors.toList());
    }

    @PostMapping("/users")
    public ResponseEntity<UserManagementView> createInternalUser(@RequestBody CreateInternalUserRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "账号数据不能为空");
        }
        String email = normalize(request.getEmail());
        String username = normalize(request.getUsername());
        String fullName = trim(request.getFullName());
        String phone = trim(request.getPhone());
        String password = request.getPassword();
        String roleName = normalizeRole(request.getRole());

        if (email.isEmpty() || username.isEmpty() || fullName.isEmpty() || phone.isEmpty() || password == null || password.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "邮箱、用户名、姓名、电话、密码和角色为必填项");
        }
        if (!INTERNAL_ROLE_NAMES.contains(roleName)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "仅支持创建内部人员账号");
        }
        if (userRepository.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "邮箱已存在");
        }
        if (userRepository.existsByUsername(username)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "用户名已存在");
        }

        Role role = requireRole(roleName);
        User user = new User();
        user.setEmail(email);
        user.setUsername(username);
        user.setFullName(fullName);
        user.setPhone(phone);
        user.setEnabled(Boolean.TRUE);
        user.setPassword(passwordEncoder.encode(password.trim()));
        user.setRoles(Set.of(role));
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        return ResponseEntity.ok(toView(userRepository.save(user)));
    }

    @PutMapping("/users/{userId}")
    public ResponseEntity<UserManagementView> updateInternalUser(@PathVariable Long userId,
                                                                 @RequestBody UpdateInternalUserRequest request,
                                                                 Authentication authentication) {
        User currentUser = requireCurrentAdmin(authentication);
        User target = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "账号不存在: " + userId));
        if (!isInternalUser(target)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "仅支持管理内部人员账号");
        }
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "更新内容不能为空");
        }

        String fullName = trim(request.getFullName());
        String phone = trim(request.getPhone());
        String roleName = normalizeRole(request.getRole());
        Boolean enabled = request.getEnabled();

        if (fullName.isEmpty() || phone.isEmpty() || roleName.isEmpty() || enabled == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "姓名、电话、角色和启用状态均不能为空");
        }
        if (!INTERNAL_ROLE_NAMES.contains(roleName)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "仅支持分配内部角色");
        }
        if (currentUser.getId() != null && currentUser.getId().equals(target.getId()) && !enabled) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "系统管理员不能禁用自己的账号");
        }

        boolean targetCurrentlyAdmin = userRoleNames(target).contains("ROLE_ADMIN");
        boolean targetWillRemainAdmin = "ROLE_ADMIN".equals(roleName);
        if (targetCurrentlyAdmin && (!enabled || !targetWillRemainAdmin) && enabledAdminCountExcluding(target.getId()) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "系统中至少需要保留一个启用状态的系统管理员账号");
        }

        target.setFullName(fullName);
        target.setPhone(phone);
        target.setEnabled(enabled);
        target.setRoles(Set.of(requireRole(roleName)));
        target.setUpdatedAt(LocalDateTime.now());
        return ResponseEntity.ok(toView(userRepository.save(target)));
    }

    private UserManagementView toView(User user) {
        return new UserManagementView(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getFullName(),
                user.getPhone(),
                Boolean.TRUE.equals(user.getEnabled()),
                userRoleNames(user).stream().toList(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }

    private User requireCurrentAdmin(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前账号无权执行该操作");
        }
        return userRepository.findByEmailIgnoreCase(authentication.getName().trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "当前账号无权执行该操作"));
    }

    private Role requireRole(String roleName) {
        return roleRepository.findByName(roleName)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "角色不存在: " + roleName));
    }

    private boolean isInternalUser(User user) {
        return user != null && userRoleNames(user).stream().anyMatch(INTERNAL_ROLE_NAMES::contains);
    }

    private Set<String> userRoleNames(User user) {
        if (user == null || user.getRoles() == null) {
            return Set.of();
        }
        return user.getRoles().stream()
                .map(Role::getName)
                .map(this::normalizeRole)
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private int enabledAdminCountExcluding(Long excludedUserId) {
        return (int) userRepository.findAll().stream()
                .filter(this::isInternalUser)
                .filter(user -> user.getId() == null || !user.getId().equals(excludedUserId))
                .filter(user -> Boolean.TRUE.equals(user.getEnabled()))
                .filter(user -> userRoleNames(user).contains("ROLE_ADMIN"))
                .count();
    }

    private boolean matchesKeyword(User user, String keyword) {
        return contains(user.getUsername(), keyword)
                || contains(user.getEmail(), keyword)
                || contains(user.getFullName(), keyword)
                || contains(user.getPhone(), keyword)
                || userRoleNames(user).stream().anyMatch(role -> contains(resolveRoleLabel(role), keyword) || contains(role, keyword));
    }

    private boolean contains(String source, String keyword) {
        return normalize(source).contains(keyword);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizeRole(String role) {
        if (role == null || role.trim().isEmpty()) {
            return "";
        }
        String normalized = role.trim().toUpperCase(Locale.ROOT);
        return normalized.startsWith("ROLE_") ? normalized : "ROLE_" + normalized;
    }

    private String resolveRoleLabel(String roleName) {
        return switch (normalizeRole(roleName)) {
            case "ROLE_ADMIN" -> "系统管理员";
            case "ROLE_SALES_MANAGER" -> "销售管理员";
            case "ROLE_PROCUREMENT_MANAGER" -> "采购管理员";
            case "ROLE_PRODUCTION_MANAGER" -> "生产管理员";
            case "ROLE_WAREHOUSE_MANAGER" -> "仓库管理员";
            case "ROLE_QUALITY_INSPECTOR" -> "质检员";
            default -> roleName;
        };
    }

    public static class RoleOptionView {
        private final String role;
        private final String label;

        public RoleOptionView(String role, String label) {
            this.role = role;
            this.label = label;
        }

        public String getRole() {
            return role;
        }

        public String getLabel() {
            return label;
        }
    }

    public static class UserManagementView {
        private final Long id;
        private final String username;
        private final String email;
        private final String fullName;
        private final String phone;
        private final boolean enabled;
        private final List<String> roles;
        private final LocalDateTime createdAt;
        private final LocalDateTime updatedAt;

        public UserManagementView(Long id,
                                  String username,
                                  String email,
                                  String fullName,
                                  String phone,
                                  boolean enabled,
                                  List<String> roles,
                                  LocalDateTime createdAt,
                                  LocalDateTime updatedAt) {
            this.id = id;
            this.username = username;
            this.email = email;
            this.fullName = fullName;
            this.phone = phone;
            this.enabled = enabled;
            this.roles = roles;
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
        }

        public Long getId() { return id; }
        public String getUsername() { return username; }
        public String getEmail() { return email; }
        public String getFullName() { return fullName; }
        public String getPhone() { return phone; }
        public boolean isEnabled() { return enabled; }
        public List<String> getRoles() { return roles; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public LocalDateTime getUpdatedAt() { return updatedAt; }
    }

    public static class CreateInternalUserRequest {
        private String username;
        private String email;
        private String fullName;
        private String phone;
        private String password;
        private String role;

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getFullName() { return fullName; }
        public void setFullName(String fullName) { this.fullName = fullName; }
        public String getPhone() { return phone; }
        public void setPhone(String phone) { this.phone = phone; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }
    }

    public static class UpdateInternalUserRequest {
        private String fullName;
        private String phone;
        private String role;
        private Boolean enabled;

        public String getFullName() { return fullName; }
        public void setFullName(String fullName) { this.fullName = fullName; }
        public String getPhone() { return phone; }
        public void setPhone(String phone) { this.phone = phone; }
        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }
        public Boolean getEnabled() { return enabled; }
        public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    }
}

