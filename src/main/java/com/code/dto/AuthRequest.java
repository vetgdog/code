package com.code.dto;

import lombok.Data;

@Data
public class AuthRequest {
    private String email;
    private String username;
    private String fullName;
    private String phone;
    private String password;
}

