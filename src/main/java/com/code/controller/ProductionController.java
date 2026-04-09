package com.code.controller;

import com.code.entity.ProductionTask;
import com.code.repository.ProductionTaskRepository;
import com.code.websocket.NotificationMessage;
import com.code.websocket.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/production")
public class ProductionController {

    @Autowired
    private ProductionTaskRepository productionTaskRepository;

    @Autowired
    private NotificationService notificationService;

    @PostMapping("/tasks")
    public ProductionTask createTask(@RequestBody ProductionTask task) {
        ProductionTask saved = productionTaskRepository.save(task);
        notificationService.broadcast("/topic/production", new NotificationMessage("TASK_CREATED","ProductionTask", saved.getId(), saved, null));
        return saved;
    }

    @GetMapping("/tasks/user/{userId}")
    public List<ProductionTask> listByUser(@PathVariable Long userId) {
        return productionTaskRepository.findByAssignedTo(userId);
    }

    @PostMapping("/tasks/{taskId}/status")
    public ProductionTask updateStatus(@PathVariable Long taskId, @RequestParam String status) {
        ProductionTask t = productionTaskRepository.findById(taskId).orElseThrow(() -> new RuntimeException("Task not found"));
        t.setStatus(status);
        ProductionTask saved = productionTaskRepository.save(t);
        notificationService.broadcast("/topic/production", new NotificationMessage("TASK_UPDATED","ProductionTask", saved.getId(), saved, null));
        return saved;
    }
}

