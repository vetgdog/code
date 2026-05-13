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
/*
 * 认证与注册控制器。
 *
 * <p>该类位于系统的“身份认证模块”，主要解决两个核心问题：
 * 1. 用户如何登录并拿到可用于后续接口调用的 JWT；
 * 2. 新用户如何完成注册，并与系统内部的角色、客户主数据建立初始关联。</p>
 *
 * <p>从业务视角看，它是系统所有角色进入业务流程前的第一站；
 * 从技术视角看，它串联了 Spring Security、PasswordEncoder、JWT、JPA Repository 等核心基础设施。</p>
 */
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
    /*
     * 用户登录。
     *
     * <p>输入：前端提交的邮箱 + 密码。</p>
     * <p>输出：包含 JWT、用户名、邮箱、主角色、全部角色列表的认证结果。</p>
     *
     * <p>执行流程：
     * 1. 规范化邮箱，避免因为大小写或前后空格导致同一账号出现“重复身份”；
     * 2. 调用 AuthenticationManager 走 Spring Security 标准认证流程；
     * 3. 认证通过后再查询数据库取出完整用户对象；
     * 4. 基于邮箱生成 JWT；
     * 5. 提取并排序用户角色，返回给前端做页面权限初始化。</p>
     */
    public AuthResponse login(@RequestBody AuthRequest req) {
        // 登录账号统一转换为小写邮箱，减少因大小写差异造成的账号识别问题。
        String email = normalize(req.getEmail());

        // AuthenticationManager 是 Spring Security 提供的统一认证入口。
        // 这里并没有手工比对密码，而是把认证职责交回框架：
        // - 它会调用 UserDetailsService 查询用户；
        // - 再调用 PasswordEncoder 校验密码；
        // - 认证失败会自动抛出异常。
        authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(email, req.getPassword()));

        // 认证成功后重新查询用户实体，是因为业务响应需要更多领域信息（用户名、角色等），
        // 而不仅仅是认证结果本身。
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));

        // JWT 中写入的是邮箱这个稳定身份标识，后续过滤器会根据它恢复当前用户身份。
        String token = jwtUtil.generateToken(user.getEmail());

        // 通过 Stream 方式提取用户角色：
        // 1. 从 Role 实体映射到角色名；
        // 2. 统一规范角色格式；
        // 3. 排序后返回给前端。
        // 这样处理的好处是前端拿到的角色列表顺序稳定，便于做主角色推导与 UI 权限初始化。
        List<String> roles = (user.getRoles() == null ? java.util.Set.<Role>of() : user.getRoles())
                .stream()
                .map(Role::getName)
                .map(this::normalizeRoleName)
                .sorted(Comparator.naturalOrder())
                .collect(Collectors.toList());

        // 当用户没有角色时，默认视为客户角色，避免前端出现空角色导致路由初始化失败。
        String primaryRole = roles.isEmpty() ? "ROLE_CUSTOMER" : roles.get(0);
        return new AuthResponse(token, user.getUsername(), user.getEmail(), primaryRole, roles);
    }

    @PostMapping("/register")
    /*
     * 用户注册。
     *
     * <p>输入：注册表单（邮箱、用户名、姓名、电话、密码）以及可选角色参数。</p>
     * <p>输出：简单成功/失败消息。</p>
     *
     * <p>该方法不仅负责创建 User 账号，还承担“初始化业务主数据”的职责：
     * 当注册角色是客户时，会同步创建 Customer 主数据，保证客户后续下单、查询订单时有业务身份承接。</p>
     */
    public ResponseEntity<String> register(@RequestBody AuthRequest req, @RequestParam(required = false) String role) {
        // 注册场景下先做输入标准化，避免数据库中出现格式不统一的数据。
        String email = normalize(req.getEmail());
        String username = normalize(req.getUsername());
        String fullName = trim(req.getFullName());
        String phone = trim(req.getPhone());

        // 这里使用“快速失败”方式做字段校验，
        // 好处是规则简单直接，能尽早把不合法请求挡在业务写库前。
        if (isBlank(email) || isBlank(username) || isBlank(fullName) || isBlank(phone) || isBlank(req.getPassword())) {
            return ResponseEntity.badRequest().body("邮箱、用户名、全名、电话和密码为必填项");
        }

        // 优先按邮箱做唯一性校验，避免重复账号进入后续写库逻辑。
        if (userRepository.existsByEmail(email)) {
            return ResponseEntity.badRequest().body("邮箱已存在");
        }

        // 组装用户实体。注意此时尚未落库，仍处于纯内存态。
        User u = new User();
        u.setEmail(email);
        u.setUsername(username);
        u.setFullName(fullName);
        u.setPhone(phone);

        // 密码绝不能明文入库，因此必须通过 PasswordEncoder 做单向加密。
        // 这里使用的是 BCrypt，后端永远不会再拿到用户原始密码。
        u.setPassword(passwordEncoder.encode(req.getPassword()));

        // 支持通过 role 参数指定注册角色，但统一做 ROLE_ 规范化，
        // 避免数据库中出现 ADMIN / ROLE_ADMIN 混杂的数据。
        String requestedRole = trim(role).toUpperCase(Locale.ROOT);
        String roleName = normalizeRoleName(requestedRole.isEmpty() ? "ROLE_CUSTOMER" : requestedRole);

        // 若角色不存在则自动创建角色。
        // 这种设计对演示型或初始化阶段系统较方便，但在严格企业系统中通常会限制角色来源，
        // 避免通过注册入口动态扩张权限模型。
        Role r = roleRepository.findByName(roleName).orElseGet(() -> {
            Role nr = new Role();
            nr.setName(roleName);
            nr.setDescription(roleName + " created");
            return roleRepository.save(nr);
        });
        u.setRoles(java.util.Set.of(r));

        // 当注册的是客户账号时，同步补齐 Customer 主数据。
        // 这样后续订单模块在根据 customer_id 建立关联时，能直接找到对应客户实体，
        // 不需要在首次下单时再被动补数据。
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
            // 真实写库动作放在 try 中，便于统一拦截数据库约束异常并转成更友好的业务提示。
            userRepository.save(u);
            return ResponseEntity.ok("ok");
        } catch (DataIntegrityViolationException ex) {
            // 这里不是简单返回“数据库异常”，而是尽量根据底层异常信息做业务语义翻译，
            // 从而提高注册接口的可用性和前端可读性。
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
        // 一层层剥离异常包装，拿到最底层的数据库错误信息。
        // 这是企业项目中常见的异常“降噪”处理方式，便于上层做更准确的错误分类。
        Throwable cursor = throwable;
        while (cursor.getCause() != null) {
            cursor = cursor.getCause();
        }
        return cursor.getMessage() == null ? "" : cursor.getMessage();
    }

    private String normalize(String value) {
        // 统一文本标准化策略：先 trim，再转小写。
        // 适用于邮箱、用户名等不区分大小写的标识字段。
        return trim(value).toLowerCase(Locale.ROOT);
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean isBlank(String value) {
        return trim(value).isEmpty();
    }

    private String normalizeRoleName(String roleName) {
        // 角色规范化是权限系统的基础动作。
        // 统一角色前缀后，Spring Security 的 hasRole / hasAnyRole 才能稳定生效。
        String value = trim(roleName).toUpperCase(Locale.ROOT);
        if (value.isEmpty()) {
            return "ROLE_CUSTOMER";
        }
        return value.startsWith("ROLE_") ? value : "ROLE_" + value;
    }

    private String generateCustomerCode() {
        // 通过 UUID 片段生成客户编码，并循环校验唯一性。
        // 这种实现简单直接，适合当前中小规模系统；
        // 如果未来客户量很大，可以考虑改为更可读、更可排序的业务编码生成器。
        String code;
        do {
            code = "CUS-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        } while (customerRepository.existsByCode(code));
        return code;
    }

}