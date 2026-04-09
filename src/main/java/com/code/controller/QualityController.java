package com.code.controller;

import com.code.entity.Batch;
import com.code.entity.QualityRecord;
import com.code.repository.BatchRepository;
import com.code.repository.QualityRecordRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/quality")
public class QualityController {

    @Autowired
    private BatchRepository batchRepository;

    @Autowired
    private QualityRecordRepository qualityRecordRepository;

    @GetMapping("/batch/{batchNo}")
    public Batch getByBatchNo(@PathVariable String batchNo) {
        return batchRepository.findAll().stream().filter(b -> b.getBatchNo().equals(batchNo)).findFirst().orElseThrow(() -> new RuntimeException("Batch not found"));
    }

    @GetMapping("/batch/{batchId}/records")
    public List<QualityRecord> records(@PathVariable Long batchId) {
        return qualityRecordRepository.findAll().stream().filter(r -> r.getBatch() != null && r.getBatch().getId().equals(batchId)).toList();
    }
}

