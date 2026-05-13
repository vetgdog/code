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

/**
 * 自定义用户详情服务。
 *
 * <p>Spring Security 在认证成功后需要一个 `UserDetails` 对象来承载账号、密码与权限集合，本类就是数据库账号模型与
 * Security 标准对象之间的适配层。同时它还兼顾一个项目历史兼容任务：自动修复缺失客户角色的旧账号。</p>
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {

    /**
     * 用户主仓库，认证时用于按邮箱加载账号主体。
     */
    @Autowired
    private UserRepository userRepository;

    /**
     * 角色仓库，用于历史账号自动补齐 ROLE_CUSTOMER 等授权信息。
     */
    @Autowired
    private RoleRepository roleRepository;

    /**
     * 客户档案仓库。
     *
     * <p>这里的存在性判断承担一个“客户身份兜底”职责：即使旧数据中用户角色缺失，只要客户档案还在，就能自动恢复客户权限。</p>
     */
    @Autowired
    private CustomerRepository customerRepository;

    /**
     * 按邮箱加载登录用户。
     *
     * <p>系统把邮箱作为用户名使用，因此这里的 `username` 实际上是 email。返回的权限集合来自 `mapRolesToAuthorities`，
     * 会在必要时自动补齐客户角色。</p>
     */
    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {

        // Spring Security 的“用户名”字段在本项目中约定为邮箱地址，
        // 所以登录表单里传入的 email 会直接进入这里作为唯一身份标识。
        User user = userRepository.findByEmail(email).orElseThrow(() -> new UsernameNotFoundException("User not found"));

        // 返回的是 Security 框架自带的 UserDetails 实现，而不是直接暴露业务 User 实体；
        // 这样可以把安全认证模型与业务模型解耦，减少后续框架耦合扩散。
        return new org.springframework.security.core.userdetails.User(user.getEmail(), user.getPassword(), mapRolesToAuthorities(user));
    }

    /**
     * 将系统角色实体映射成 Spring Security 所需的授权集合。
     *
     * <p>这里最重要的设计点是“旧客户账号自动修复”：如果账号邮箱在客户档案中存在，但角色集合中没有 `ROLE_CUSTOMER`，
     * 则系统会自动补上并回写数据库。这样能避免门户客户因历史脏数据突然失去下单权限。</p>
     */
    private Collection<? extends GrantedAuthority> mapRolesToAuthorities(User u) {
        Set<Role> roles = u.getRoles() == null ? new HashSet<>() : new HashSet<>(u.getRoles());

        // customerRepository.existsByEmail(...) 只判断是否存在客户档案，
        // 不需要把完整客户对象查出来，属于更轻量的存在性查询。
        boolean hasCustomerProfile = u.getEmail() != null
                && customerRepository.existsByEmail(u.getEmail().toLowerCase(Locale.ROOT));

        // 先统一角色名格式再判断，避免数据库里混存 CUSTOMER / ROLE_CUSTOMER / customer 等历史数据时判断失准。
        boolean hasCustomerRole = roles.stream()
                .map(Role::getName)
                .map(this::normalizeRoleName)
                .anyMatch("ROLE_CUSTOMER"::equals);

        // 旧版本数据若只建了客户档案但没绑角色，会导致客户能登录却无权下单。
        // 这里在认证阶段顺手修复，是一种“读时修复”策略：兼容老数据，又无需额外写数据清洗脚本。
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

            // 当账号没有任何角色时给一个兜底 ROLE_USER，
            // 可以避免部分安全框架流程因空权限集合而表现异常。
            return java.util.List.of(new SimpleGrantedAuthority("ROLE_USER"));
        }

        // GrantedAuthority 是 Spring Security 内部统一权限抽象，
        // hasRole/hasAuthority 最终都会落到这个集合上做匹配判断。
        return roles.stream()
                .map(Role::getName)
                .map(this::normalizeRoleName)
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());
    }

    /**
     * 统一角色前缀，保证无论库里存的是 `ADMIN` 还是 `ROLE_ADMIN`，最终都会变成 Spring Security 标准格式。
     */
    private String normalizeRoleName(String roleName) {
        if (roleName == null || roleName.isBlank()) {
            return "ROLE_USER";
        }

        // Spring Security 默认角色前缀就是 ROLE_，统一规范后，
        // 前端、数据库、方法权限注解之间才能稳定对齐。
        String normalized = roleName.trim().toUpperCase(Locale.ROOT);
        return normalized.startsWith("ROLE_") ? normalized : "ROLE_" + normalized;
    }
}


