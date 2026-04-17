package com.code.controller;

import com.code.entity.Batch;
import com.code.entity.QualityRecord;
import com.code.service.QualityService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/quality")
public class QualityController {

    private final QualityService qualityService;

    public QualityController(QualityService qualityService) {
        this.qualityService = qualityService;
    }

    @GetMapping("/batches")
    @PreAuthorize("hasAnyRole('QUALITY_INSPECTOR','ADMIN')")
    public List<Batch> listBatches(@RequestParam(required = false) String keyword,
                                   @RequestParam(required = false) String status) {
        return qualityService.listBatches(keyword, status);
    }

    @GetMapping("/batch/{batchNo}")
    @PreAuthorize("hasAnyRole('QUALITY_INSPECTOR','ADMIN')")
    public Batch getByBatchNo(@PathVariable String batchNo) {
        return qualityService.getBatchByNo(batchNo);
    }

    @GetMapping("/batch/{batchId}/records")
    @PreAuthorize("hasAnyRole('QUALITY_INSPECTOR','ADMIN')")
    public List<QualityRecord> records(@PathVariable Long batchId) {
        return qualityService.listRecords(batchId);
    }

    @GetMapping("/my-records")
    @PreAuthorize("hasAnyRole('QUALITY_INSPECTOR','ADMIN')")
    public List<QualityRecord> listMyRecords(@RequestParam(required = false) String keyword,
                                             @RequestParam(required = false) String result,
                                             Authentication authentication) {
        return qualityService.listMyRecords(authentication == null ? "" : authentication.getName(), keyword, result);
    }

    @PostMapping("/batch/{batchId}/inspect")
    @PreAuthorize("hasAnyRole('QUALITY_INSPECTOR','ADMIN')")
    public ResponseEntity<Batch> inspect(@PathVariable Long batchId,
                                         @RequestBody InspectRequest request,
                                         Authentication authentication) {
        Batch batch = qualityService.inspectBatch(
                batchId,
                request == null ? "" : request.getResult(),
                request == null ? "" : request.getRemarks(),
                authentication == null ? "" : authentication.getName()
        );
        return ResponseEntity.ok(batch);
    }

    public static class InspectRequest {
        private String result;
        private String remarks;

        public String getResult() {
            return result;
        }

        public void setResult(String result) {
            this.result = result;
        }

        public String getRemarks() {
            return remarks;
        }

        public void setRemarks(String remarks) {
            this.remarks = remarks;
        }
    }
}

