package com.code.controller;

import com.code.entity.User;
import com.code.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProfileControllerTest {

    @InjectMocks
    private ProfileController profileController;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Test
    void changePasswordShouldUpdateEncodedPassword() {
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn("admin@test.com");

        User user = new User();
        user.setId(1L);
        user.setEmail("admin@test.com");
        user.setPassword("encoded-old");

        ProfileController.ChangePasswordRequest request = new ProfileController.ChangePasswordRequest();
        request.setCurrentPassword("old123456");
        request.setNewPassword("new123456");

        when(userRepository.findByEmailIgnoreCase("admin@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("old123456", "encoded-old")).thenReturn(true);
        when(passwordEncoder.encode("new123456")).thenReturn("encoded-new");

        String result = profileController.changePassword(request, authentication).getBody();

        assertEquals("密码修改成功，请重新登录", result);
        assertEquals("encoded-new", user.getPassword());
        verify(userRepository).save(user);
    }

    @Test
    void changePasswordShouldRejectWrongCurrentPassword() {
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn("admin@test.com");

        User user = new User();
        user.setEmail("admin@test.com");
        user.setPassword("encoded-old");

        ProfileController.ChangePasswordRequest request = new ProfileController.ChangePasswordRequest();
        request.setCurrentPassword("wrong-password");
        request.setNewPassword("new123456");

        when(userRepository.findByEmailIgnoreCase("admin@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-password", "encoded-old")).thenReturn(false);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> profileController.changePassword(request, authentication)
        );

        assertEquals("当前密码不正确", exception.getReason());
    }
}

