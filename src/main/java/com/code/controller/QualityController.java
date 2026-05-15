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

/**
 * 质量管理控制器。
 *
 * <p>该类本身保持得非常薄，核心业务都下沉到 {@link QualityService}：控制器只负责声明质检相关 HTTP 入口、
 * 角色权限以及请求参数转换。这样的设计有利于把“接口协议”与“质量判定规则”解耦。</p>
 */
@RestController
@RequestMapping("/api/v1/quality")
public class QualityController {

    /**
     * 质检领域服务。
     *
     * <p>批次状态流转、质检记录落库、订单联动与通知等真正有副作用的业务，都应该由服务层统一处理。</p>
     */
    private final QualityService qualityService;

    /**
     * 构造器注入保持控制器无状态，也便于测试时替换成 mock 服务。
     */
    public QualityController(QualityService qualityService) {
        this.qualityService = qualityService;
    }

    /**
     * 查询待检/已检批次列表，供质检看板展示。
     */
    @GetMapping("/batches")
    @PreAuthorize("hasAnyRole('QUALITY_INSPECTOR','ADMIN')")
    public List<Batch> listBatches(@RequestParam(required = false) String keyword,
                                   @RequestParam(required = false) String status) {
        // 控制器不在这里重复做过滤逻辑，而是把参数直接转给服务层，
        // 保持“接口层只负责协议，业务层负责规则”的清晰分工。
        return qualityService.listBatches(keyword, status);
    }

    /**
     * 按批次号查询批次详情。
     *
     * <p>之所以使用批次号而不是批次 id，是因为业务现场更常以打印在标签上的批次号识别对象。</p>
     */
    @GetMapping("/batch/{batchNo}")
    @PreAuthorize("hasAnyRole('QUALITY_INSPECTOR','ADMIN')")
    public Batch getByBatchNo(@PathVariable String batchNo) {
        return qualityService.getBatchByNo(batchNo);
    }

    /**
     * 查询某批次全部质检记录，便于查看复检历史。
     */
    @GetMapping("/batch/{batchId}/records")
    @PreAuthorize("hasAnyRole('QUALITY_INSPECTOR','ADMIN')")
    public List<QualityRecord> records(@PathVariable Long batchId) {
        // 记录列表通常用于查看复检轨迹，因此这里按 batchId 查的是某批次完整检验历史，而不是单次结果。
        return qualityService.listRecords(batchId);
    }

    /**
     * 查询当前质检员自己的检验记录。
     */
    @GetMapping("/my-records")
    @PreAuthorize("hasAnyRole('QUALITY_INSPECTOR','ADMIN')")
    public List<QualityRecord> listMyRecords(@RequestParam(required = false) String keyword,
                                             @RequestParam(required = false) String result,
                                             Authentication authentication) {
        // authentication 为空时传空串而不是直接空指针，
        // 让服务层统一决定“无登录邮箱”应如何处理，避免控制器层出现重复判空逻辑。
        return qualityService.listMyRecords(authentication == null ? "" : authentication.getName(), keyword, result);
    }

    /**
     * 对批次执行检验。
     *
     * <p>控制器只负责把请求体与登录账号转交给服务层，真正的检验结果落库、批次状态流转、通知等副作用都应该在服务层完成，
     * 这也是事务边界应当所在的位置。</p>
     */
    @PostMapping("/batch/{batchId}/inspect")
    @PreAuthorize("hasRole('QUALITY_INSPECTOR')")
    public ResponseEntity<Batch> inspect(@PathVariable Long batchId,
                                         @RequestBody InspectRequest request,
                                         Authentication authentication) {

        // request 允许为空时回退为空串，是为了把 HTTP 层“缺字段/空请求体”的噪声收敛掉，
        // 真正的结果合法性校验仍交给服务层统一处理。
        Batch batch = qualityService.inspectBatch(
                batchId,
                request == null ? "" : request.getResult(),
                request == null ? "" : request.getRemarks(),
                authentication == null ? "" : authentication.getName()
        );
        return ResponseEntity.ok(batch);
    }

    /**
     * 质检提交请求体。
     *
     * <p>该对象只承载本次检验提交的最小必要字段：检验结果与备注。它不直接暴露 Batch 实体，
     * 避免前端把批次状态等服务端管理字段一起提交回来，造成接口语义混乱。</p>
     */
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

