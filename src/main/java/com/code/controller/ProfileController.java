package com.code.controller;

import com.code.entity.User;
import com.code.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/v1/profile")
public class ProfileController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public ProfileController(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping("/change-password")
    public ResponseEntity<String> changePassword(@RequestBody ChangePasswordRequest request,
                                                 Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录后再修改密码");
        }
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "密码数据不能为空");
        }
        if (isBlank(request.getCurrentPassword()) || isBlank(request.getNewPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "当前密码和新密码不能为空");
        }
        if (request.getNewPassword().trim().length() < 6) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "新密码长度不能少于6位");
        }
        if (request.getNewPassword().equals(request.getCurrentPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "新密码不能与当前密码相同");
        }

        User user = userRepository.findByEmailIgnoreCase(authentication.getName().trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "当前账号不存在"));
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "当前密码不正确");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword().trim()));
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
        return ResponseEntity.ok("密码修改成功，请重新登录");
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public static class ChangePasswordRequest {
        private String currentPassword;
        private String newPassword;

        public String getCurrentPassword() {
            return currentPassword;
        }

        public void setCurrentPassword(String currentPassword) {
            this.currentPassword = currentPassword;
        }

        public String getNewPassword() {
            return newPassword;
        }

        public void setNewPassword(String newPassword) {
            this.newPassword = newPassword;
        }
    }
}

