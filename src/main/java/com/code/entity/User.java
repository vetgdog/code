package com.code.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.Set;

@Entity
@Table(name = "users")
@Data
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String username;

    @Column(nullable = false)
    @JsonIgnore
    private String password;

    private String fullName;
    @Column(nullable = false, unique = true, length = 200)
    private String email;
    private String phone;
    private Boolean enabled = true;
    
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    @JsonIgnore
    private Set<Role> roles;

    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Transient
    public String getName() {
        return fullName == null || fullName.isBlank() ? username : fullName;
    }

    @Transient
    public String getCode() {
        return username == null || username.isBlank() ? (email == null ? "" : email) : username;
    }

    @Transient
    public String getContact() {
        return getName();
    }
}

