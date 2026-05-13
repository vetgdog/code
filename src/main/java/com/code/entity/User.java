package com.code.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.Set;

@Entity
@Table(name = "users")
@Data
/*
 * 系统用户实体。
 *
 * <p>该类同时承担“登录账号主数据”和“业务操作者身份载体”两种职责：Spring Security 认证时会基于它加载邮箱、密码、角色，
 * 各业务模块又会把 createdBy / requestedBy / reviewedBy 等字段回指到这里。由于它处在认证、权限、审计三条链路的交叉点，
 * 所以字段设计更偏向稳定、可追溯，而不是只服务单一页面展示。</p>
 */
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 系统内部账号名。
     *
     * <p>它更多承担历史兼容、内部展示和部分迁移脚本匹配职责；真正登录入口在当前项目中已经统一收敛为 email。</p>
     */
    @Column(nullable = false, length = 100)
    private String username;

    /**
     * 加密后的登录密码。
     *
     * <p>使用 {@link JsonIgnore} 是为了避免实体在接口返回或日志序列化时意外泄露敏感字段。数据库中应保存经过 PasswordEncoder
     * 处理后的摘要值，而不是明文密码。</p>
     */
    @Column(nullable = false)
    @JsonIgnore
    private String password;

    /**
     * 业务显示名。
     *
     * <p>销售、采购、仓库等页面通常优先展示该字段；若为空，再降级到 username/email，保证历史账号也有可读身份标识。</p>
     */
    private String fullName;

    /**
     * 邮箱是当前系统事实上的唯一登录名。
     *
     * <p>唯一约束确保认证入口不会出现一人多账号重名冲突，同时方便用邮箱构造通知 topic、追溯业务操作人。</p>
     */
    @Column(nullable = false, unique = true, length = 200)
    private String email;

    /**
     * 联系方式，主要用于基础通讯展示或作为历史供应商账号迁移时的补充信息。
     */
    private String phone;

    /**
     * 账号开关位。
     *
     * <p>相比物理删除，禁用更适合企业系统：既能阻止继续登录，又能保留历史订单、采购、库存流水中的操作者引用。</p>
     */
    private Boolean enabled = true;
    
    /**
     * 用户与角色的多对多关系。
     *
     * <p>这里显式落表到 user_roles，属于较典型的 RBAC 建模。选择 EAGER 是为了让认证阶段拿到用户后即可直接获得权限集合，
     * 减少登录时因延迟加载导致的 Session/事务边界问题；代价是角色数量很多时会增加一次查询负担。</p>
     */
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    @JsonIgnore
    private Set<Role> roles;

    /**
     * 创建时间，主要服务于账号审计与初始化脚本幂等补写。
     */
    private LocalDateTime createdAt = LocalDateTime.now();

    /**
     * 更新时间，可用于后台账号维护、迁移脚本修复后的最后变更痕迹记录。
     */
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Transient
    public String getName() {
        // @Transient 表示该方法返回值不参与数据库映射，
        // 它只是给控制器/前端提供一个“统一展示名”视图，避免各处重复写 fullName 判空逻辑。
        return fullName == null || fullName.isBlank() ? username : fullName;
    }

    @Transient
    public String getCode() {
        // 这里把 username 作为优先业务编码，若历史数据未维护 username，再回退到 email，
        // 用于兼容旧供应商/客户展示和脚本匹配逻辑。
        return username == null || username.isBlank() ? (email == null ? "" : email) : username;
    }

    @Transient
    public String getContact() {
        // 某些页面把“联系人”概念复用为账号显示名，这里通过衍生属性统一出口，避免前端硬编码拼字段。
        return getName();
    }
}

