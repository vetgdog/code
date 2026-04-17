package com.code.controller;

import com.code.dto.ProcurementExportRowDto;
import com.code.dto.SupplierDashboardDto;
import com.code.entity.ProcurementWeeklyPlan;
import com.code.entity.Product;
import com.code.entity.PurchaseOrder;
import com.code.entity.PurchaseRequest;
import com.code.entity.User;
import com.code.repository.ProductRepository;
import com.code.repository.PurchaseRequestRepository;
import com.code.service.ProcurementWorkflowService;
import com.code.service.WeeklyPlanningService;
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
import java.nio.charset.StandardCharsets;
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
public class ProcurementController {

    private static final DataFormatter DATA_FORMATTER = new DataFormatter();

    @Autowired
    private PurchaseRequestRepository purchaseRequestRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProcurementWorkflowService procurementWorkflowService;

    @Autowired
    private WeeklyPlanningService weeklyPlanningService;

    @GetMapping("/requests")
    @PreAuthorize("hasAnyRole('PROCUREMENT_MANAGER','ADMIN')")
    public List<PurchaseRequest> listRequests() {
        return purchaseRequestRepository.findAllByOrderByRequestDateDesc();
    }

    @GetMapping("/suppliers")
    @PreAuthorize("hasAnyRole('PROCUREMENT_MANAGER','ADMIN')")
    public List<User> listSuppliers() {
        return procurementWorkflowService.listSuppliersBoundToRawMaterials();
    }

    @GetMapping("/orders")
    @PreAuthorize("hasAnyRole('SUPPLIER','PROCUREMENT_MANAGER','WAREHOUSE_MANAGER','ADMIN')")
    public List<PurchaseOrder> listOrders(@RequestParam(required = false) String keyword,
                                          @RequestParam(required = false) String status,
                                          @RequestParam(required = false) String startDate,
                                          @RequestParam(required = false) String endDate,
                                          Authentication authentication) {
        return filterPurchaseOrders(keyword, status, startDate, endDate, authentication);
    }

    @GetMapping("/orders/export")
    @PreAuthorize("hasAnyRole('SUPPLIER','PROCUREMENT_MANAGER','ADMIN')")
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
    @PreAuthorize("hasAnyRole('PROCUREMENT_MANAGER','ADMIN')")
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
                .body(builder.toString().getBytes(StandardCharsets.UTF_8));
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
    @PreAuthorize("hasAnyRole('PROCUREMENT_MANAGER','ADMIN')")
    public PurchaseOrder createOrder(@RequestBody PurchaseOrder po, Authentication authentication) {
        return procurementWorkflowService.createPurchaseOrder(po, authentication == null ? "" : authentication.getName());
    }

    @PostMapping("/orders/{purchaseOrderId}/supplier-decision")
    @PreAuthorize("hasAnyRole('SUPPLIER','ADMIN')")
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
    @PreAuthorize("hasAnyRole('SUPPLIER','ADMIN')")
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
    @PreAuthorize("hasAnyRole('PROCUREMENT_MANAGER','ADMIN')")
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
    @PreAuthorize("hasAnyRole('WAREHOUSE_MANAGER','ADMIN')")
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
    @PreAuthorize("hasAnyRole('SUPPLIER','ADMIN')")
    public ResponseEntity<?> createRawMaterial(@RequestBody RawMaterialRequest request, Authentication authentication) {
        assignRawMaterialSku(request);
        validateRawMaterialRequest(request);
        applySupplierScope(request, authentication);
        Product material = buildRawMaterial(new Product(), request);
        Product saved = productRepository.save(material);
        return ResponseEntity.ok(saved);
    }

    @GetMapping("/raw-materials/template")
    @PreAuthorize("hasAnyRole('SUPPLIER','PROCUREMENT_MANAGER','ADMIN')")
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
    @PreAuthorize("hasAnyRole('SUPPLIER','ADMIN')")
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

    private void applySupplierScope(RawMaterialRequest request, Authentication authentication) {
        User currentSupplier = resolveCurrentSupplier(authentication);
        if (currentSupplier != null && request != null) {
            request.setPreferredSupplier(currentSupplier.getName());
        }
    }

    private void assignRawMaterialSku(RawMaterialRequest request) {
        if (request == null) {
            return;
        }
        request.setSku(generateRawMaterialSku());
    }

    private String generateRawMaterialSku() {
        for (int attempt = 0; attempt < 10; attempt++) {
            String candidate = UUID.randomUUID().toString().toUpperCase(Locale.ROOT);
            if (productRepository.findBySkuIgnoreCase(candidate).isEmpty()) {
                return candidate;
            }
        }
        throw new ResponseStatusException(HttpStatus.CONFLICT, "原材料编号生成失败，请稍后重试");
    }

    private void validateRawMaterialRequest(RawMaterialRequest request) {
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

