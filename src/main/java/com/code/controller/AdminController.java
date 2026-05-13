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
/*
 * 后台账号管理控制器。
 *
 * <p>该类属于“系统管理 / 身份治理”模块，面向系统管理员提供内部账号的查询、创建、修改能力。
 * 它解决的核心业务问题是：如何在系统内部安全地维护各岗位账号（销售、采购、生产、仓库、质检、管理员），
 * 同时确保角色边界清晰、关键管理员账号不会被误删/误禁用、返回给前端的数据结构统一可控。</p>
 *
 * <p>从技术职责上看，它并不是一个简单 CRUD Controller，
 * 而是把“用户主数据维护 + 角色限制 + 管理规则校验”集中在同一个边界中，
 * 从而保证账号治理逻辑不会散落到前端或其它业务模块。</p>
 */
public class AdminController {

    /*
     * 系统允许由后台维护的“内部角色白名单”。
     *
     * <p>之所以显式列出，而不是开放任意角色，是为了防止系统管理员通过接口错误地把客户、供应商等外部角色
     * 混入内部人员管理流程，造成权限模型混乱。</p>
     */
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
    /*
     * 查询系统支持分配的内部角色列表。
     *
     * <p>执行流程：
     * 1. 从角色表读取全部角色；
     * 2. 标准化角色名；
     * 3. 只保留内部角色白名单中的角色；
     * 4. 排序后映射为前端下拉框可直接使用的数据结构。</p>
     *
     * <p>这里返回 RoleOptionView，而不是直接返回 Role 实体，
     * 是典型的“接口视图隔离”设计：避免把数据库内部字段直接暴露给前端。</p>
     */
    public List<RoleOptionView> listInternalRoles() {
        return roleRepository.findAll().stream()
                // 先取角色名字符串，避免将整个 Role 实体继续往下传播。
                .map(Role::getName)
                // 统一角色格式，避免 ROLE_admin / admin / ADMIN 这种不一致情况影响过滤。
                .map(this::normalizeRole)
                // 白名单控制是权限系统的重要设计，防止把非内部角色返回给后台管理界面。
                .filter(INTERNAL_ROLE_NAMES::contains)
                .sorted()
                // 转成更适合前端渲染的 { role, label } 结构。
                .map(role -> new RoleOptionView(role, resolveRoleLabel(role)))
                .collect(Collectors.toList());
    }

    @GetMapping("/users")
    /*
     * 查询内部账号列表，支持关键字、角色、启用状态筛选。
     *
     * <p>输入：
     * - keyword：模糊搜索用户名、邮箱、姓名、电话、角色中文名
     * - role：按角色精确筛选
     * - enabled：按启用状态筛选</p>
     *
     * <p>输出：UserManagementView 列表，用于账号管理页表格展示。</p>
     *
     * <p>这里使用 Stream 连续过滤表达业务规则，优点是：
     * 逻辑顺序和筛选步骤基本一致，可读性高；
     * 缺点是当前实现基于 findAll() 后内存过滤，数据量大时会有性能压力。</p>
     */
    public List<UserManagementView> listUsers(@RequestParam(required = false) String keyword,
                                              @RequestParam(required = false) String role,
                                              @RequestParam(required = false) Boolean enabled) {
        String normalizedKeyword = normalize(keyword);
        String normalizedRole = normalizeRole(role);
        return userRepository.findAll().stream()
                // 第一步先收口到“内部人员”，把外部客户、供应商账号排除出去。
                .filter(this::isInternalUser)
                // 第二步按角色筛选；如果 role 为空，则不过滤。
                .filter(user -> normalizedRole.isEmpty() || userRoleNames(user).contains(normalizedRole))
                // 第三步按启用状态筛选；enabled == null 表示前端未指定该条件。
                .filter(user -> enabled == null || enabled.equals(user.getEnabled()))
                // 第四步做关键字匹配，覆盖用户名、邮箱、姓名、电话、角色名等维度。
                .filter(user -> normalizedKeyword.isEmpty() || matchesKeyword(user, normalizedKeyword))
                // 按创建时间倒序排列，满足后台管理场景下“最新创建账号优先可见”的习惯。
                .sorted(Comparator.comparing(User::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(User::getId, Comparator.nullsLast(Comparator.reverseOrder())))
                // 最后统一映射成视图对象，而不是直接返回实体。
                .map(this::toView)
                .collect(Collectors.toList());
    }

    @PostMapping("/users")
    /*
     * 创建内部账号。
     *
     * <p>该方法承担的不仅是“新增一条用户记录”，更重要的是保证：
     * 1. 只能创建内部人员账号；
     * 2. 邮箱/用户名不重复；
     * 3. 密码以加密形式落库；
     * 4. 新账号默认启用，并立即具备目标岗位角色。</p>
     */
    public ResponseEntity<UserManagementView> createInternalUser(@RequestBody CreateInternalUserRequest request) {
        // 第一层保护：请求体不能为空。
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "账号数据不能为空");
        }

        // 先做输入标准化，避免数据库中出现大小写、空格不一致的数据。
        String email = normalize(request.getEmail());
        String username = normalize(request.getUsername());
        String fullName = trim(request.getFullName());
        String phone = trim(request.getPhone());
        String password = request.getPassword();
        String roleName = normalizeRole(request.getRole());

        // 必填项校验。
        // 这里使用“快速失败”风格，让非法请求尽早返回，避免进入后续数据库操作。
        if (email.isEmpty() || username.isEmpty() || fullName.isEmpty() || phone.isEmpty() || password == null || password.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "邮箱、用户名、姓名、电话、密码和角色为必填项");
        }

        // 白名单校验：后台只允许创建内部角色，避免把外部角色账号错误地纳入内部管理。
        if (!INTERNAL_ROLE_NAMES.contains(roleName)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "仅支持创建内部人员账号");
        }

        // 唯一性校验放在业务层先做一遍，能给前端更友好的提示；
        // 真正的唯一性兜底仍应依赖数据库唯一索引。
        if (userRepository.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "邮箱已存在");
        }
        if (userRepository.existsByUsername(username)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "用户名已存在");
        }

        // 角色必须从数据库存在的角色表中获取，而不是直接 new 一个临时角色，
        // 这样才能保证权限体系来源统一。
        Role role = requireRole(roleName);

        // 开始组装新用户实体。
        User user = new User();
        user.setEmail(email);
        user.setUsername(username);
        user.setFullName(fullName);
        user.setPhone(phone);

        // 新建内部账号默认启用，减少创建后还需额外“激活”的管理成本。
        user.setEnabled(Boolean.TRUE);

        // 密码必须经过 PasswordEncoder 加密后再持久化，
        // 这是最基本的账号安全要求，避免明文密码泄露风险。
        user.setPassword(passwordEncoder.encode(password.trim()));

        // 当前设计一个内部账号只分配一个主角色，因此使用 Set.of(role) 直接覆盖。
        user.setRoles(Set.of(role));
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());

        // 保存后立刻转换为视图对象返回，便于前端刷新表格。
        return ResponseEntity.ok(toView(userRepository.save(user)));
    }

    @PutMapping("/users/{userId}")
    /*
     * 修改内部账号。
     *
     * <p>该方法体现了企业系统中“账号变更”比“账号创建”更严格的治理要求：
     * 1. 只能修改内部人员；
     * 2. 当前管理员不能禁用自己；
     * 3. 系统里至少要保留一个启用中的管理员账号；
     * 4. 角色仍然必须来自内部角色白名单。</p>
     */
    public ResponseEntity<UserManagementView> updateInternalUser(@PathVariable Long userId,
                                                                 @RequestBody UpdateInternalUserRequest request,
                                                                 Authentication authentication) {
        // 先确认当前操作者身份，避免匿名请求或无效认证进入修改流程。
        User currentUser = requireCurrentAdmin(authentication);

        // 查询被修改对象；找不到则直接返回 404，符合 REST 风格。
        User target = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "账号不存在: " + userId));

        // 防止管理员通过该接口去修改客户/供应商等外部用户。
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

        // 更新接口要求关键字段必须完整提交，避免出现“部分字段为空把原值冲掉”的问题。
        if (fullName.isEmpty() || phone.isEmpty() || roleName.isEmpty() || enabled == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "姓名、电话、角色和启用状态均不能为空");
        }
        if (!INTERNAL_ROLE_NAMES.contains(roleName)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "仅支持分配内部角色");
        }

        // 防止当前系统管理员把自己禁用，导致当前会话失去管理入口。
        if (currentUser.getId() != null && currentUser.getId().equals(target.getId()) && !enabled) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "系统管理员不能禁用自己的账号");
        }

        // 下面这一段是“最后一个管理员保护”逻辑：
        // - 如果目标账号当前就是管理员；
        // - 且这次更新会让它不再是启用管理员（被禁用或改成其它角色）；
        // - 同时系统里没有其它启用管理员；
        // 那么本次操作必须被拒绝。
        boolean targetCurrentlyAdmin = userRoleNames(target).contains("ROLE_ADMIN");
        boolean targetWillRemainAdmin = "ROLE_ADMIN".equals(roleName);
        if (targetCurrentlyAdmin && (!enabled || !targetWillRemainAdmin) && enabledAdminCountExcluding(target.getId()) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "系统中至少需要保留一个启用状态的系统管理员账号");
        }

        // 校验通过后再真正修改目标账号字段。
        target.setFullName(fullName);
        target.setPhone(phone);
        target.setEnabled(enabled);
        target.setRoles(Set.of(requireRole(roleName)));
        target.setUpdatedAt(LocalDateTime.now());
        return ResponseEntity.ok(toView(userRepository.save(target)));
    }

    private UserManagementView toView(User user) {
        // 统一的实体 -> 视图对象转换方法。
        // 这样做的好处是返回结构可控，避免 Controller 多处重复组装 JSON 字段。
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
        // Authentication 由 Spring Security 注入，里面保存了当前登录人的认证信息。
        // 这里再次根据 email 查询 User，是为了拿到完整用户实体并统一做后续业务校验。
        if (authentication == null || authentication.getName() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前账号无权执行该操作");
        }
        return userRepository.findByEmailIgnoreCase(authentication.getName().trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "当前账号无权执行该操作"));
    }

    private Role requireRole(String roleName) {
        // 角色不存在时直接抛业务异常，而不是静默创建，
        // 因为后台账号管理属于严格治理场景，角色来源必须可控。
        return roleRepository.findByName(roleName)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "角色不存在: " + roleName));
    }

    private boolean isInternalUser(User user) {
        // 只要用户具备任意一个内部角色，就视为内部人员。
        return user != null && userRoleNames(user).stream().anyMatch(INTERNAL_ROLE_NAMES::contains);
    }

    private Set<String> userRoleNames(User user) {
        if (user == null || user.getRoles() == null) {
            return Set.of();
        }
        return user.getRoles().stream()
                // 从实体角色提取角色名，并统一规范格式。
                .map(Role::getName)
                .map(this::normalizeRole)
                .filter(value -> !value.isEmpty())
                // 使用 LinkedHashSet 而不是 HashSet，是为了在保留去重能力的同时尽量维持遍历顺序稳定。
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private int enabledAdminCountExcluding(Long excludedUserId) {
        // 该方法用于“最后一个管理员保护”规则。
        // 当前实现是全量加载所有用户后内存统计，逻辑直观但在数据量大时性能一般，
        // 后续可考虑下推到数据库层进行 count 查询。
        return (int) userRepository.findAll().stream()
                .filter(this::isInternalUser)
                .filter(user -> user.getId() == null || !user.getId().equals(excludedUserId))
                .filter(user -> Boolean.TRUE.equals(user.getEnabled()))
                .filter(user -> userRoleNames(user).contains("ROLE_ADMIN"))
                .count();
    }

    private boolean matchesKeyword(User user, String keyword) {
        // 关键字匹配覆盖多维字段，兼顾后台账号搜索的实用性。
        // 同时支持角色中文名匹配，提升管理界面的检索体验。
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
        // 通用字符串标准化：trim + lowerCase。
        // 适合邮箱、用户名、关键字等大小写不敏感的场景。
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizeRole(String role) {
        // 角色标准化是权限系统的基础动作，
        // 保证无论前端传 ADMIN 还是 ROLE_ADMIN，后端内部都统一成 ROLE_ 前缀格式。
        if (role == null || role.trim().isEmpty()) {
            return "";
        }
        String normalized = role.trim().toUpperCase(Locale.ROOT);
        return normalized.startsWith("ROLE_") ? normalized : "ROLE_" + normalized;
    }

    private String resolveRoleLabel(String roleName) {
        // 将技术角色名转换为业务可读中文名，用于前端展示。
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

    /**
     * 角色下拉框视图模型。
     * 仅保留前端真正需要的 role + label 两个字段，避免泄露 Role 实体内部细节。
     */
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

    /**
     * 账号管理页列表项视图模型。
     *
     * <p>这是典型的“读模型 / 展示模型”设计：
     * 与数据库 User 实体解耦，防止返回密码、复杂关联对象等敏感或冗余信息。</p>
     */
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

    /**
     * 创建内部账号请求对象。
     * 对应后台“新增账号”表单提交结构。
     */
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

    /**
     * 修改内部账号请求对象。
     *
     * <p>这里不允许直接修改邮箱、用户名、密码，说明当前系统把这些字段视为更高风险信息，
     * 需要走独立流程或独立页面处理。</p>
     */
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

