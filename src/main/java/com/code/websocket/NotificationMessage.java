package com.code.websocket;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NotificationMessage {
    private String messageType;
    private String entity;
    private Long entityId;
    private Object payload;
    private LocalDateTime timestamp = LocalDateTime.now();
}

