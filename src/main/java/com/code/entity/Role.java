package com.code.entity;

import lombok.Data;

import javax.persistence.*;

@Entity
@Table(name = "roles")
@Data
/*
 * 角色实体。
 *
 * <p>这是系统 RBAC 权限模型中的“粗粒度授权单元”。控制器上的 {@code @PreAuthorize("hasRole(...)")}、
 * 前端按角色分导航、登录后用户权限装配，最终都会回到这里定义的角色名上。因此该实体虽然简单，但属于整个权限体系的基础字典表。</p>
 */
public class Role {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 角色唯一名。
     *
     * <p>通常采用 Spring Security 习惯的 ROLE_ 前缀，例如 ROLE_ADMIN、ROLE_SUPPLIER。
     * 之所以做唯一约束，是为了保证授权判断时不存在语义相同但拼写不同的重复角色。</p>
     */
    @Column(nullable = false, unique = true)
    private String name;

    /**
     * 面向后台管理与可读性展示的说明文本，不直接参与权限判定。
     */
    private String description;
}

