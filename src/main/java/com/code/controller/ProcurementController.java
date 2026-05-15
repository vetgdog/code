package com.code.controller;

import com.code.dto.ProcurementExportRowDto;
import com.code.dto.SupplierDashboardDto;
import com.code.entity.ProcurementWeeklyPlan;
import com.code.entity.Product;
import com.code.entity.PurchaseOrder;
import com.code.entity.PurchaseRequest;
import com.code.entity.User;
import com.code.repository.BatchRepository;
import com.code.repository.InventoryItemRepository;
import com.code.repository.MrpRequirementRepository;
import com.code.repository.ProductRepository;
import com.code.repository.ProductionMaterialRequestItemRepository;
import com.code.repository.PurchaseOrderItemRepository;
import com.code.repository.PurchaseRequestRepository;
import com.code.repository.StockTransactionRepository;
import com.code.service.ProcurementWorkflowService;
import com.code.service.WeeklyPlanningService;
import com.code.util.CsvExportUtils;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.core.Authentication;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/procurement")
/*
 * 采购模块控制器。
 *
 * <p>该类同时承接三类核心业务：
 * 1. 采购订单流转（采购管理员、供应商、仓库协同）；
 * 2. 原材料档案维护（供应商 / 采购 / 仓库按权限维护）；
 * 3. 周采购计划与采购导出视图。</p>
 *
 * <p>它是系统中典型的“多角色协同 Controller”：
 * 不同接口分别面向供应商、采购管理员、仓库管理员，
 * 因此在设计上大量依赖 @PreAuthorize 和 Authentication 来做角色边界与数据范围裁剪。</p>
 */
public class ProcurementController {

    private static final DataFormatter DATA_FORMATTER = new DataFormatter();

    @Autowired
    private PurchaseRequestRepository purchaseRequestRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private PurchaseOrderItemRepository purchaseOrderItemRepository;

    @Autowired
    private InventoryItemRepository inventoryItemRepository;

    @Autowired
    private StockTransactionRepository stockTransactionRepository;

    @Autowired
    private MrpRequirementRepository mrpRequirementRepository;

    @Autowired
    private ProductionMaterialRequestItemRepository productionMaterialRequestItemRepository;

    @Autowired
    private BatchRepository batchRepository;

    @Autowired
    private ProcurementWorkflowService procurementWorkflowService;

    @Autowired
    private WeeklyPlanningService weeklyPlanningService;

    @GetMapping("/requests")
    @PreAuthorize("hasAnyRole('PROCUREMENT_MANAGER','ADMIN')")
    public List<PurchaseRequest> listRequests() {
        return purchaseRequestRepository.findByStatusOrderByRequestDateDesc("OPEN");
    }

    @GetMapping("/suppliers")
    @PreAuthorize("hasAnyRole('PROCUREMENT_MANAGER','ADMIN')")
    public List<User> listSuppliers() {
        return procurementWorkflowService.listSuppliersBoundToRawMaterials();
    }

    @GetMapping("/orders")
    @PreAuthorize("hasAnyRole('SUPPLIER','PROCUREMENT_MANAGER','WAREHOUSE_MANAGER','ADMIN')")
    // 查询采购订单列表。
    // 该接口并不是简单查表，而是会根据当前登录人的角色决定能看到哪些采购单：
    // - 供应商只看与自己相关的订单；
    // - 采购管理员看采购全局；
    // - 仓库管理员看与收货有关的订单。
    public List<PurchaseOrder> listOrders(@RequestParam(required = false) String keyword,
                                          @RequestParam(required = false) String status,
                                          @RequestParam(required = false) String startDate,
                                          @RequestParam(required = false) String endDate,
                                          Authentication authentication) {
        return filterPurchaseOrders(keyword, status, startDate, endDate, authentication);
    }

    @GetMapping("/orders/export")
    @PreAuthorize("hasAnyRole('SUPPLIER','PROCUREMENT_MANAGER','ADMIN')")
    // 导出采购订单。
    // 这里复用和列表接口同一套筛选逻辑，保证“页面可见数据”和“导出数据”口径一致，
    // 防止用户通过导出接口获取超权限数据。
    public ResponseEntity<?> exportOrders(@RequestParam(required = false) String keyword,
                                          @RequestParam(required = false) String status,
                                          @RequestParam(required = false) String startDate,
                                          @RequestParam(required = false) String endDate,
                                          @RequestParam(defaultValue = "csv") String format,
                                          Authentication authentication) {
        List<PurchaseOrder> orders = filterPurchaseOrders(keyword, status, startDate, endDate, authentication);
        List<ProcurementExportRowDto> rows = procurementWorkflowService.buildExportRows(orders);
        String normalizedFormat = normalize(format);
        if ("xlsx".equals(normalizedFormat) || "excel".equals(normalizedFormat)) {
            return exportOrdersAsExcel(rows);
        }
        return exportOrdersAsCsv(rows);
    }

    @GetMapping("/dashboard")
    @PreAuthorize("hasAnyRole('SUPPLIER','ADMIN')")
    // 供应商工作台。
    // 返回的不是数据库表原样数据，而是 SupplierDashboardDto 聚合视图，
    // 目的是把“待处理订单、推荐原材料、统计计数”等供应商真正关心的信息一次性返回给前端。
    public SupplierDashboardDto getSupplierDashboard(Authentication authentication) {
        String email = authentication == null ? "" : authentication.getName();
        return procurementWorkflowService.buildSupplierDashboard(email);
    }

    @GetMapping("/weekly-plans")
    @PreAuthorize("hasAnyRole('PROCUREMENT_MANAGER','ADMIN')")
    public List<ProcurementWeeklyPlan> listWeeklyPlans() {
        return weeklyPlanningService.listProcurementPlans();
    }

    @GetMapping("/weekly-plans/current")
    @PreAuthorize("hasAnyRole('PROCUREMENT_MANAGER','ADMIN')")
    public ProcurementWeeklyPlan getCurrentWeeklyPlan(@RequestParam(required = false) String referenceDate,
                                                      Authentication authentication) {
        return weeklyPlanningService.getOrGenerateProcurementPlan(parseReferenceDate(referenceDate), authentication == null ? "" : authentication.getName());
    }

    @PostMapping("/weekly-plans/generate")
    @PreAuthorize("hasRole('PROCUREMENT_MANAGER')")
    public ProcurementWeeklyPlan generateWeeklyPlan(@RequestParam(required = false) String referenceDate,
                                                    Authentication authentication) {
        return weeklyPlanningService.generateProcurementPlan(parseReferenceDate(referenceDate), authentication == null ? "" : authentication.getName());
    }

    private List<PurchaseOrder> filterPurchaseOrders(String keyword,
                                                     String status,
                                                     String startDate,
                                                     String endDate,
                                                     Authentication authentication) {
        String normalizedKeyword = normalize(keyword);
        String normalizedStatus = normalize(status);
        LocalDateTime start = parseStartDate(startDate);
        LocalDateTime end = parseEndDate(endDate);
        if (start != null && end != null && end.isBefore(start)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "结束日期不能早于开始日期");
        }

        String role = authentication == null || authentication.getAuthorities() == null
                ? ""
                : authentication.getAuthorities().stream().findFirst().map(org.springframework.security.core.GrantedAuthority::getAuthority).orElse("");
        String email = authentication == null ? "" : authentication.getName();
        return procurementWorkflowService.listOrders(role, email).stream()
                .filter(order -> normalizedStatus.isEmpty() || normalize(order.getStatus()).equals(normalizedStatus))
                .filter(order -> start == null || (order.getOrderDate() != null && !order.getOrderDate().isBefore(start)))
                .filter(order -> end == null || (order.getOrderDate() != null && !order.getOrderDate().isAfter(end)))
                .filter(order -> normalizedKeyword.isEmpty() || matchesPurchaseOrderKeyword(order, normalizedKeyword))
                .collect(Collectors.toList());
    }

    private ResponseEntity<byte[]> exportOrdersAsCsv(List<ProcurementExportRowDto> rows) {
        StringBuilder builder = new StringBuilder();
        builder.append("采购单号,状态,供应商编码,供应商名称,原材料SKU,原材料名称,数量,单价,行金额,采购单总额,下单时间,发货时间,入库时间,供应商备注,采购备注,仓库备注\n");
        for (ProcurementExportRowDto row : rows) {
            builder.append(csv(row.getPoNo())).append(',')
                    .append(csv(row.getStatus())).append(',')
                    .append(csv(row.getSupplierCode())).append(',')
                    .append(csv(row.getSupplierName())).append(',')
                    .append(csv(row.getItemSku())).append(',')
                    .append(csv(row.getItemName())).append(',')
                    .append(csv(number(row.getQuantity()))).append(',')
                    .append(csv(number(row.getUnitPrice()))).append(',')
                    .append(csv(number(row.getLineTotal()))).append(',')
                    .append(csv(number(row.getOrderTotalAmount()))).append(',')
                    .append(csv(row.getOrderDate())).append(',')
                    .append(csv(row.getShippedAt())).append(',')
                    .append(csv(row.getReceivedAt())).append(',')
                    .append(csv(row.getSupplierNote())).append(',')
                    .append(csv(row.getProcurementNote())).append(',')
                    .append(csv(row.getWarehouseNote())).append('\n');
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=procurement-records.csv")
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(CsvExportUtils.toExcelCompatibleUtf8Bytes(builder.toString()));
    }

    private ResponseEntity<byte[]> exportOrdersAsExcel(List<ProcurementExportRowDto> rows) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("procurement-records");
            String[] headers = {
                    "采购单号", "状态", "供应商编码", "供应商名称", "原材料SKU", "原材料名称", "数量", "单价",
                    "行金额", "采购单总额", "下单时间", "发货时间", "入库时间", "供应商备注", "采购备注", "仓库备注"
            };
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                headerRow.createCell(i).setCellValue(headers[i]);
                sheet.setColumnWidth(i, 18 * 256);
            }
            int rowIndex = 1;
            for (ProcurementExportRowDto row : rows) {
                Row dataRow = sheet.createRow(rowIndex++);
                dataRow.createCell(0).setCellValue(row.getPoNo());
                dataRow.createCell(1).setCellValue(row.getStatus());
                dataRow.createCell(2).setCellValue(row.getSupplierCode());
                dataRow.createCell(3).setCellValue(row.getSupplierName());
                dataRow.createCell(4).setCellValue(row.getItemSku());
                dataRow.createCell(5).setCellValue(row.getItemName());
                dataRow.createCell(6).setCellValue(number(row.getQuantity()));
                dataRow.createCell(7).setCellValue(number(row.getUnitPrice()));
                dataRow.createCell(8).setCellValue(number(row.getLineTotal()));
                dataRow.createCell(9).setCellValue(number(row.getOrderTotalAmount()));
                dataRow.createCell(10).setCellValue(row.getOrderDate());
                dataRow.createCell(11).setCellValue(row.getShippedAt());
                dataRow.createCell(12).setCellValue(row.getReceivedAt());
                dataRow.createCell(13).setCellValue(row.getSupplierNote());
                dataRow.createCell(14).setCellValue(row.getProcurementNote());
                dataRow.createCell(15).setCellValue(row.getWarehouseNote());
            }
            workbook.write(out);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=procurement-records.xlsx")
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(out.toByteArray());
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "采购记录导出失败");
        }
    }

    private String csv(String value) {
        String safeValue = value == null ? "" : value;
        return '"' + safeValue.replace("\"", "\"\"") + '"';
    }

    private String number(Double value) {
        return value == null ? "" : String.format(Locale.ROOT, "%.2f", value);
    }

    @GetMapping("/orders/pending-warehouse-receipt")
    @PreAuthorize("hasAnyRole('WAREHOUSE_MANAGER','ADMIN')")
    public List<PurchaseOrder> listPendingWarehouseReceiptOrders() {
        return procurementWorkflowService.listPendingWarehouseReceipts();
    }

    @PostMapping("/orders")
    @PreAuthorize("hasRole('PROCUREMENT_MANAGER')")
    public PurchaseOrder createOrder(@RequestBody PurchaseOrder po, Authentication authentication) {
        return procurementWorkflowService.createPurchaseOrder(po, authentication == null ? "" : authentication.getName());
    }

    @PostMapping("/orders/{purchaseOrderId}/supplier-decision")
    @PreAuthorize("hasRole('SUPPLIER')")
    public PurchaseOrder supplierDecision(@PathVariable Long purchaseOrderId,
                                          @RequestParam String decision,
                                          @RequestBody(required = false) NoteRequest request,
                                          Authentication authentication) {
        return procurementWorkflowService.supplierDecision(
                purchaseOrderId,
                decision,
                authentication == null ? "" : authentication.getName(),
                request == null ? "" : request.getNote()
        );
    }

    @PostMapping("/orders/{purchaseOrderId}/supplier-ship")
    @PreAuthorize("hasRole('SUPPLIER')")
    public PurchaseOrder supplierShip(@PathVariable Long purchaseOrderId,
                                      @RequestBody(required = false) NoteRequest request,
                                      Authentication authentication) {
        return procurementWorkflowService.supplierShip(
                purchaseOrderId,
                authentication == null ? "" : authentication.getName(),
                request == null ? "" : request.getNote()
        );
    }

    @PostMapping("/orders/{purchaseOrderId}/notify-warehouse")
    @PreAuthorize("hasRole('PROCUREMENT_MANAGER')")
    public PurchaseOrder notifyWarehouse(@PathVariable Long purchaseOrderId,
                                         @RequestBody(required = false) NoteRequest request,
                                         Authentication authentication) {
        return procurementWorkflowService.notifyWarehouseForReceipt(
                purchaseOrderId,
                authentication == null ? "" : authentication.getName(),
                request == null ? "" : request.getNote()
        );
    }

    @PostMapping("/orders/{purchaseOrderId}/warehouse-receive")
    @PreAuthorize("hasRole('WAREHOUSE_MANAGER')")
    public PurchaseOrder warehouseReceive(@PathVariable Long purchaseOrderId,
                                          @RequestBody(required = false) NoteRequest request,
                                          Authentication authentication) {
        return procurementWorkflowService.warehouseReceive(
                purchaseOrderId,
                authentication == null ? "" : authentication.getName(),
                request == null ? "" : request.getNote()
        );
    }

    @GetMapping("/raw-materials")
    @PreAuthorize("hasAnyRole('SUPPLIER','PROCUREMENT_MANAGER','WAREHOUSE_MANAGER','ADMIN')")
    // 查询原材料档案。
    // 这里把原材料视为 Product 中 product_type = RAW_MATERIAL 的子集，
    // 而不是单独拆一张主数据表，是一种“统一产品主数据模型”的设计。
    public List<Product> listRawMaterials(@RequestParam(required = false) String keyword,
                                          @RequestParam(required = false) String startDate,
                                          @RequestParam(required = false) String endDate,
                                          Authentication authentication) {
        String normalizedKeyword = normalize(keyword);
        LocalDateTime start = parseStartDate(startDate);
        LocalDateTime end = parseEndDate(endDate);
        if (start != null && end != null && end.isBefore(start)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "结束日期不能早于开始日期");
        }

        User currentSupplier = resolveCurrentSupplier(authentication);

        return productRepository.findAll().stream()
                .filter(this::isRawMaterial)
                .filter(product -> currentSupplier == null || procurementWorkflowService.isSupplierRelatedMaterial(product, currentSupplier))
                .filter(product -> start == null || (product.getCreatedAt() != null && !product.getCreatedAt().isBefore(start)))
                .filter(product -> end == null || (product.getCreatedAt() != null && !product.getCreatedAt().isAfter(end)))
                .filter(product -> normalizedKeyword.isEmpty() || matchesKeyword(product, normalizedKeyword))
                .sorted(Comparator.comparing(Product::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(Product::getId, Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());
    }

    @GetMapping("/raw-materials/{id}")
    @PreAuthorize("hasAnyRole('SUPPLIER','PROCUREMENT_MANAGER','WAREHOUSE_MANAGER','ADMIN')")
    // 查询单个原材料详情。
    // 除了主键存在性校验，还会做供应商数据范围校验，避免供应商越权查看不属于自己的原材料。
    public Product getRawMaterial(@PathVariable Long id, Authentication authentication) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "原材料不存在: " + id));
        if (!isRawMaterial(product)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "原材料不存在: " + id);
        }
        User currentSupplier = resolveCurrentSupplier(authentication);
        if (currentSupplier != null && !procurementWorkflowService.isSupplierRelatedMaterial(product, currentSupplier)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "原材料不存在: " + id);
        }
        return product;
    }

    @PostMapping("/raw-materials")
    @PreAuthorize("hasAnyRole('SUPPLIER','PROCUREMENT_MANAGER','WAREHOUSE_MANAGER','ADMIN')")
    // 新增原材料。
    // 核心处理要点：
    // 1. 自动分配 SKU；
    // 2. 校验字段合法性；
    // 3. 若当前操作者是供应商，则自动把 preferredSupplier 绑定为当前供应商。
    public ResponseEntity<Product> createRawMaterial(@RequestBody RawMaterialRequest request, Authentication authentication) {
        assignRawMaterialSku(request);
        validateRawMaterialRequest(request);
        applySupplierScope(request, authentication);
        Product material = buildRawMaterial(new Product(), request);
        Product saved = productRepository.save(material);
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/raw-materials/{id}")
    @PreAuthorize("hasAnyRole('SUPPLIER','PROCUREMENT_MANAGER','WAREHOUSE_MANAGER','ADMIN')")
    // 修改原材料。
    // 这里显式保留 existing SKU，不允许通过更新接口修改编号，
    // 避免主数据编码在外部引用后被随意篡改。
    public ResponseEntity<Product> updateRawMaterial(@PathVariable Long id,
                                                     @RequestBody RawMaterialRequest request,
                                                     Authentication authentication) {
        Product existing = findAccessibleRawMaterial(id, authentication);
        request.setSku(existing.getSku());
        validateRawMaterialRequest(request);
        applySupplierScope(request, authentication);
        Product saved = productRepository.save(buildRawMaterial(existing, request));
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/raw-materials/{id}")
    @PreAuthorize("hasAnyRole('SUPPLIER','WAREHOUSE_MANAGER')")
    // 删除原材料。
    // 删除前会检查是否已被采购申请、采购订单、库存、MRP、领料申请、批次等业务数据引用。
    // 这是典型的“业务删除保护”设计，比单纯依赖外键报错更友好，也更可解释。
    public ResponseEntity<Void> deleteRawMaterial(@PathVariable Long id, Authentication authentication) {
        Product existing = findAccessibleRawMaterial(id, authentication);
        assertRawMaterialCanBeDeleted(existing);
        productRepository.delete(existing);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/raw-materials/template")
    @PreAuthorize("hasAnyRole('SUPPLIER','PROCUREMENT_MANAGER','ADMIN')")
    // 下载原材料导入模板。
    // 通过 Apache POI 动态生成 Excel，避免静态模板文件与代码字段定义脱节。
    public ResponseEntity<byte[]> downloadRawMaterialTemplate() {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("raw-material-template");
            Row header = sheet.createRow(0);
            String[] headers = {
                    "sku", "name", "materialCategory", "specification", "unit", "unitPrice",
                    "preferredSupplier", "origin", "safetyStock", "leadTimeDays", "description"
            };
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
                sheet.setColumnWidth(i, 18 * 256);
            }
            Row example = sheet.createRow(1);
            example.createCell(0).setCellValue("");
            example.createCell(1).setCellValue("冷轧钢板");
            example.createCell(2).setCellValue("钢材");
            example.createCell(3).setCellValue("Q235 / 2mm");
            example.createCell(4).setCellValue("kg");
            example.createCell(5).setCellValue(5.86);
            example.createCell(6).setCellValue("华东钢材供应商");
            example.createCell(7).setCellValue("上海");
            example.createCell(8).setCellValue(200);
            example.createCell(9).setCellValue(7);
            example.createCell(10).setCellValue("用于钣金件生产");
            workbook.write(out);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=raw-material-template.xlsx")
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(out.toByteArray());
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "模板生成失败");
        }
    }

    @PostMapping(value = "/raw-materials/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('SUPPLIER')")
    // Excel 批量导入原材料。
    // 采用“逐行解析 + 累积错误”的方式，而不是一行失败就整体中断，
    // 更符合企业批量主数据导入场景下的可运维性要求。
    public ResponseEntity<ImportResult> importRawMaterials(@RequestParam("file") MultipartFile file,
                                                           Authentication authentication) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请上传Excel文件");
        }
        String originalName = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (!originalName.endsWith(".xlsx")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "仅支持 .xlsx 格式文件");
        }

        List<String> errors = new ArrayList<>();
        int created = 0;
        int updated = 0;

        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null || isRowEmpty(row)) {
                    continue;
                }
                try {
                    RawMaterialRequest request = parseRow(row);
                    if (isBlank(request.getSku())) {
                        assignRawMaterialSku(request);
                    }
                    validateRawMaterialRequest(request);
                    applySupplierScope(request, authentication);
                    Optional<Product> existing = productRepository.findBySkuIgnoreCase(request.getSku().trim());
                    Product target = existing.orElseGet(Product::new);
                    boolean isNew = existing.isEmpty();
                    buildRawMaterial(target, request);
                    productRepository.save(target);
                    if (isNew) {
                        created++;
                    } else {
                        updated++;
                    }
                } catch (ResponseStatusException ex) {
                    errors.add("第 " + (rowIndex + 1) + " 行：" + ex.getReason());
                } catch (Exception ex) {
                    errors.add("第 " + (rowIndex + 1) + " 行：导入失败");
                }
            }
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Excel解析失败，请检查文件内容");
        }

        return ResponseEntity.ok(new ImportResult(created, updated, errors));
    }

    private Product buildRawMaterial(Product target, RawMaterialRequest request) {
        // 统一的原材料实体赋值方法。
        // 创建和更新都复用这一套映射规则，避免字段映射逻辑在多个方法里重复散落。
        target.setSku(request.getSku().trim().toUpperCase(Locale.ROOT));
        target.setName(request.getName().trim());
        target.setProductType("RAW_MATERIAL");
        target.setMaterialCategory(trimToNull(request.getMaterialCategory()));
        target.setSpecification(trimToNull(request.getSpecification()));
        target.setUnit(trimToNull(request.getUnit()));
        target.setPreferredSupplier(trimToNull(request.getPreferredSupplier()));
        target.setOrigin(trimToNull(request.getOrigin()));
        target.setDescription(trimToNull(request.getDescription()));
        target.setUnitPrice(request.getUnitPrice() == null ? 0.0 : request.getUnitPrice());
        target.setSafetyStock(request.getSafetyStock() == null ? 0.0 : request.getSafetyStock());
        target.setLeadTimeDays(request.getLeadTimeDays() == null ? 0 : request.getLeadTimeDays());
        return target;
    }

    private Product findAccessibleRawMaterial(Long id, Authentication authentication) {
        // 先校验原材料存在，再校验当前用户是否有访问该原材料的范围权限。
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "原材料不存在: " + id));
        if (!isRawMaterial(product)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "原材料不存在: " + id);
        }
        User currentSupplier = resolveCurrentSupplier(authentication);
        if (currentSupplier != null && !procurementWorkflowService.isSupplierRelatedMaterial(product, currentSupplier)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "原材料不存在: " + id);
        }
        return product;
    }

    private void assertRawMaterialCanBeDeleted(Product product) {
        // 企业主数据删除保护：只要已经进入任一业务链路，就禁止物理删除。
        if (product == null || product.getId() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "原材料不存在");
        }
        Long productId = product.getId();
        if (purchaseRequestRepository.existsByProductId(productId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该原材料已关联采购申请，无法删除");
        }
        if (purchaseOrderItemRepository.existsByProductId(productId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该原材料已关联采购订单，无法删除");
        }
        if (inventoryItemRepository.existsByProductId(productId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该原材料已存在库存记录，无法删除");
        }
        if (stockTransactionRepository.existsByProductId(productId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该原材料已存在库存流水，无法删除");
        }
        if (mrpRequirementRepository.existsByProductId(productId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该原材料已关联物料需求计划，无法删除");
        }
        if (productionMaterialRequestItemRepository.existsByMaterialProductId(productId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该原材料已关联领料申请，无法删除");
        }
        if (batchRepository.existsByProductId(productId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该原材料已关联批次记录，无法删除");
        }
    }

    private void applySupplierScope(RawMaterialRequest request, Authentication authentication) {
        // 如果当前登录人是供应商，则强制把首选供应商绑定为当前供应商名称，
        // 防止供应商伪造其它供应商身份创建或修改原材料。
        User currentSupplier = resolveCurrentSupplier(authentication);
        if (currentSupplier != null && request != null) {
            request.setPreferredSupplier(currentSupplier.getName());
        }
    }

    private void assignRawMaterialSku(RawMaterialRequest request) {
        // 新增场景下由系统统一生成 SKU，减少人工维护成本并避免编号冲突。
        if (request == null) {
            return;
        }
        request.setSku(generateRawMaterialSku());
    }

    private String generateRawMaterialSku() {
        // 采用 UUID 方案快速生成唯一编号。
        // 简单高效，但可读性较弱；如果后续有更强业务语义要求，可改为规则编码生成器。
        for (int attempt = 0; attempt < 10; attempt++) {
            String candidate = UUID.randomUUID().toString().toUpperCase(Locale.ROOT);
            if (productRepository.findBySkuIgnoreCase(candidate).isEmpty()) {
                return candidate;
            }
        }
        throw new ResponseStatusException(HttpStatus.CONFLICT, "原材料编号生成失败，请稍后重试");
    }

    private void validateRawMaterialRequest(RawMaterialRequest request) {
        // 原材料主数据统一校验入口。
        // 这里把数值非负、名称必填、SKU 必填等基础规则集中收口，避免创建/更新逻辑各写一遍。
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "原材料数据不能为空");
        }
        if (isBlank(request.getSku())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "SKU不能为空");
        }
        if (isBlank(request.getName())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "原材料名称不能为空");
        }
        if (request.getUnitPrice() != null && request.getUnitPrice() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "单价不能小于0");
        }
        if (request.getSafetyStock() != null && request.getSafetyStock() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "安全库存不能小于0");
        }
        if (request.getLeadTimeDays() != null && request.getLeadTimeDays() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "供货周期不能小于0");
        }
    }

    private RawMaterialRequest parseRow(Row row) {
        RawMaterialRequest request = new RawMaterialRequest();
        request.setSku(readCellAsString(row.getCell(0)));
        request.setName(readCellAsString(row.getCell(1)));
        request.setMaterialCategory(readCellAsString(row.getCell(2)));
        request.setSpecification(readCellAsString(row.getCell(3)));
        request.setUnit(readCellAsString(row.getCell(4)));
        request.setUnitPrice(readCellAsDouble(row.getCell(5)));
        request.setPreferredSupplier(readCellAsString(row.getCell(6)));
        request.setOrigin(readCellAsString(row.getCell(7)));
        request.setSafetyStock(readCellAsDouble(row.getCell(8)));
        Double leadTimeValue = readCellAsDouble(row.getCell(9));
        request.setLeadTimeDays(leadTimeValue == null ? null : leadTimeValue.intValue());
        request.setDescription(readCellAsString(row.getCell(10)));
        return request;
    }

    private boolean isRowEmpty(Row row) {
        for (int i = 0; i < 11; i++) {
            if (!isBlank(readCellAsString(row.getCell(i)))) {
                return false;
            }
        }
        return true;
    }

    private String readCellAsString(Cell cell) {
        if (cell == null) {
            return "";
        }
        String formatted = DATA_FORMATTER.formatCellValue(cell);
        if (!isBlank(formatted)) {
            return formatted.trim();
        }
        if (cell.getCellType() == CellType.NUMERIC) {
            double value = cell.getNumericCellValue();
            if (Math.floor(value) == value) {
                return String.valueOf((long) value);
            }
            return String.valueOf(value);
        }
        if (cell.getCellType() == CellType.BOOLEAN) {
            return String.valueOf(cell.getBooleanCellValue());
        }
        return "";
    }

    private Double readCellAsDouble(Cell cell) {
        String raw = readCellAsString(cell);
        if (isBlank(raw)) {
            return null;
        }
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "数值列格式错误: " + raw);
        }
    }

    private boolean isRawMaterial(Product product) {
        return product != null && "RAW_MATERIAL".equalsIgnoreCase(product.getProductType());
    }

    private User resolveCurrentSupplier(Authentication authentication) {
        if (authentication == null || !hasSupplierRole(authentication) || isBlank(authentication.getName())) {
            return null;
        }
        return procurementWorkflowService.resolveSupplierAccount(authentication.getName().trim().toLowerCase(Locale.ROOT));
    }

    private boolean hasSupplierRole(Authentication authentication) {
        return authentication != null
                && authentication.getAuthorities() != null
                && authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_SUPPLIER"::equalsIgnoreCase);
    }

    private boolean matchesKeyword(Product product, String keyword) {
        return contains(product.getSku(), keyword)
                || contains(product.getName(), keyword)
                || contains(product.getMaterialCategory(), keyword)
                || contains(product.getSpecification(), keyword)
                || contains(product.getPreferredSupplier(), keyword)
                || contains(product.getOrigin(), keyword)
                || contains(product.getDescription(), keyword);
    }

    private boolean matchesPurchaseOrderKeyword(PurchaseOrder order, String keyword) {
        return contains(order.getPoNo(), keyword)
                || contains(order.getStatus(), keyword)
                || contains(order.getSupplier() == null ? null : order.getSupplier().getName(), keyword)
                || contains(order.getSupplier() == null ? null : order.getSupplier().getCode(), keyword)
                || contains(order.getSupplierNote(), keyword)
                || contains(order.getProcurementNote(), keyword)
                || contains(order.getWarehouseNote(), keyword)
                || (order.getItems() != null && order.getItems().stream().anyMatch(item ->
                contains(item.getProduct() == null ? null : item.getProduct().getName(), keyword)
                        || contains(item.getProduct() == null ? null : item.getProduct().getSku(), keyword)));
    }

    private boolean contains(String value, String keyword) {
        return normalize(value).contains(keyword);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String trimToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private LocalDateTime parseStartDate(String raw) {
        if (isBlank(raw)) {
            return null;
        }
        try {
            return LocalDate.parse(raw.trim()).atStartOfDay();
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "开始日期格式错误，应为 yyyy-MM-dd");
        }
    }

    private LocalDate parseReferenceDate(String raw) {
        if (isBlank(raw)) {
            return LocalDate.now();
        }
        try {
            return LocalDate.parse(raw.trim());
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "参考日期格式错误，应为 yyyy-MM-dd");
        }
    }

    private LocalDateTime parseEndDate(String raw) {
        if (isBlank(raw)) {
            return null;
        }
        try {
            return LocalDate.parse(raw.trim()).atTime(23, 59, 59);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "结束日期格式错误，应为 yyyy-MM-dd");
        }
    }

    public static class RawMaterialRequest {
        private String sku;
        private String name;
        private String materialCategory;
        private String specification;
        private String unit;
        private Double unitPrice;
        private String preferredSupplier;
        private String origin;
        private Double safetyStock;
        private Integer leadTimeDays;
        private String description;

        public String getSku() { return sku; }
        public void setSku(String sku) { this.sku = sku; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getMaterialCategory() { return materialCategory; }
        public void setMaterialCategory(String materialCategory) { this.materialCategory = materialCategory; }
        public String getSpecification() { return specification; }
        public void setSpecification(String specification) { this.specification = specification; }
        public String getUnit() { return unit; }
        public void setUnit(String unit) { this.unit = unit; }
        public Double getUnitPrice() { return unitPrice; }
        public void setUnitPrice(Double unitPrice) { this.unitPrice = unitPrice; }
        public String getPreferredSupplier() { return preferredSupplier; }
        public void setPreferredSupplier(String preferredSupplier) { this.preferredSupplier = preferredSupplier; }
        public String getOrigin() { return origin; }
        public void setOrigin(String origin) { this.origin = origin; }
        public Double getSafetyStock() { return safetyStock; }
        public void setSafetyStock(Double safetyStock) { this.safetyStock = safetyStock; }
        public Integer getLeadTimeDays() { return leadTimeDays; }
        public void setLeadTimeDays(Integer leadTimeDays) { this.leadTimeDays = leadTimeDays; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
    }

    public static class ImportResult {
        private final int createdCount;
        private final int updatedCount;
        private final List<String> errors;

        public ImportResult(int createdCount, int updatedCount, List<String> errors) {
            this.createdCount = createdCount;
            this.updatedCount = updatedCount;
            this.errors = errors;
        }

        public int getCreatedCount() { return createdCount; }
        public int getUpdatedCount() { return updatedCount; }
        public List<String> getErrors() { return errors; }
    }

    public static class NoteRequest {
        private String note;

        public String getNote() {
            return note;
        }

        public void setNote(String note) {
            this.note = note;
        }
    }
}

