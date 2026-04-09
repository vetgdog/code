package com.code.service;

import com.code.entity.MrpRequirement;
import com.code.entity.OrderItem;
import com.code.entity.SalesOrder;
import com.code.repository.MrpRequirementRepository;
import com.code.repository.SalesOrderRepository;
import com.code.websocket.NotificationMessage;
import com.code.websocket.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class MrpService {

    @Autowired
    private SalesOrderRepository salesOrderRepository;

    @Autowired
    private MrpRequirementRepository mrpRequirementRepository;

    @Autowired
    private NotificationService notificationService;

    // run every minute for demo (adjust as needed)
    @Scheduled(fixedRate = 60000)
    @Transactional
    public void runMrp() {
        List<SalesOrder> orders = salesOrderRepository.findAll();
        for (SalesOrder order : orders) {
            if (!"COMPLETED".equals(order.getStatus())) {
                List<OrderItem> items = order.getItems() == null ? List.of() : order.getItems();
                for (OrderItem it : items) {
                    MrpRequirement r = new MrpRequirement();
                    r.setProduct(it.getProduct());
                    r.setRequiredQuantity(it.getQuantity());
                    r.setRequiredDate(LocalDateTime.now().plusDays(7));
                    r.setSourceType("ORDER");
                    r.setRelatedId(order.getId());
                    mrpRequirementRepository.save(r);

                    notificationService.broadcast("/topic/mrp", new NotificationMessage("MRP_CREATED","MrpRequirement", r.getId(), r, LocalDateTime.now()));
                }
            }
        }
    }
}


