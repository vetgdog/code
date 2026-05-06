package com.code.controller;

import com.code.dto.ProcurementExportRowDto;
import com.code.dto.SupplierDashboardDto;
import com.code.entity.Product;
import com.code.entity.PurchaseOrder;
import com.code.entity.PurchaseRequest;
import com.code.entity.Role;
import com.code.entity.User;
import com.code.repository.ProductRepository;
import com.code.repository.PurchaseOrderRepository;
import com.code.repository.PurchaseRequestRepository;
import com.code.service.ProcurementWorkflowService;
import com.code.service.WeeklyPlanningService;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProcurementControllerTest {

    @InjectMocks
    private ProcurementController procurementController;

    @Mock
    private PurchaseRequestRepository purchaseRequestRepository;

    @Mock
    private PurchaseOrderRepository purchaseOrderRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProcurementWorkflowService procurementWorkflowService;

    @Mock
    private WeeklyPlanningService weeklyPlanningService;

    @Mock
    private Authentication authentication;

    @Test
    void createRawMaterialStoresRawMaterialType() {
        ProcurementController.RawMaterialRequest request = new ProcurementController.RawMaterialRequest();
        request.setName("冷轧钢板");
        request.setMaterialCategory("钢材");
        request.setUnit("kg");
        request.setUnitPrice(5.2);
        request.setSafetyStock(100.0);
        request.setLeadTimeDays(7);

        when(productRepository.findBySkuIgnoreCase(any())).thenReturn(Optional.empty());
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ResponseEntity<?> response = procurementController.createRawMaterial(request, null);

        assertEquals(200, response.getStatusCodeValue());
        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(captor.capture());
        Product saved = captor.getValue();
        assertEquals("RAW_MATERIAL", saved.getProductType());
        assertFalse(saved.getSku().isBlank());
        assertEquals(saved.getSku(), saved.getSku().toUpperCase());
        assertEquals("冷轧钢板", saved.getName());
        assertEquals(100.0, saved.getSafetyStock());
    }

    @Test
    void listRequestsOnlyReturnsOpenRequests() {
        PurchaseRequest openRequest = new PurchaseRequest();
        openRequest.setId(1L);
        openRequest.setRequestNo("PR-OPEN-001");
        openRequest.setStatus("OPEN");

        when(purchaseRequestRepository.findByStatusOrderByRequestDateDesc("OPEN")).thenReturn(List.of(openRequest));

        List<PurchaseRequest> result = procurementController.listRequests();

        assertEquals(1, result.size());
        assertEquals("PR-OPEN-001", result.get(0).getRequestNo());
        verify(purchaseRequestRepository).findByStatusOrderByRequestDateDesc("OPEN");
    }

    @Test
    void listRawMaterialsOnlyReturnsRawMaterialRecords() {
        Product rawMaterial = new Product();
        rawMaterial.setId(1L);
        rawMaterial.setSku("RM-001");
        rawMaterial.setName("冷轧钢板");
        rawMaterial.setProductType("RAW_MATERIAL");
        rawMaterial.setCreatedAt(LocalDateTime.now());

        Product finishedGood = new Product();
        finishedGood.setId(2L);
        finishedGood.setSku("FG-001");
        finishedGood.setName("成品机箱");
        finishedGood.setProductType("FINISHED_GOOD");
        finishedGood.setCreatedAt(LocalDateTime.now());

        when(productRepository.findAll()).thenReturn(List.of(rawMaterial, finishedGood));

        List<Product> result = procurementController.listRawMaterials("钢板", null, null, null);

        assertEquals(1, result.size());
        assertEquals("RM-001", result.get(0).getSku());
    }

    @Test
    void createRawMaterialRejectsDuplicateSku() {
        ProcurementController.RawMaterialRequest request = new ProcurementController.RawMaterialRequest();
        request.setName("冷轧钢板");

        when(productRepository.findBySkuIgnoreCase(any()))
                .thenReturn(Optional.of(new Product()))
                .thenReturn(Optional.empty());
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ResponseEntity<?> response = procurementController.createRawMaterial(request, null);

        assertEquals(200, response.getStatusCodeValue());
        verify(productRepository).save(argThat(product -> product.getSku() != null && !product.getSku().isBlank()));
    }

    @Test
    void importRawMaterialsCreatesAndUpdatesBySku() throws Exception {
        byte[] bytes = buildWorkbook();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "raw-materials.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                bytes
        );

        Product existing = new Product();
        existing.setId(99L);
        existing.setSku("RM-002");
        existing.setProductType("RAW_MATERIAL");

        when(productRepository.findBySkuIgnoreCase("RM-001")).thenReturn(Optional.empty());
        when(productRepository.findBySkuIgnoreCase("RM-002")).thenReturn(Optional.of(existing));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ResponseEntity<ProcurementController.ImportResult> response = procurementController.importRawMaterials(file, null);

        assertEquals(200, response.getStatusCodeValue());
        ProcurementController.ImportResult result = response.getBody();
        assertNotNull(result);
        assertEquals(1, result.getCreatedCount());
        assertEquals(1, result.getUpdatedCount());
        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    void exportOrdersAsCsvReturnsAttachment() {
        java.util.List<org.springframework.security.core.GrantedAuthority> authorities = java.util.List.of(new SimpleGrantedAuthority("ROLE_PROCUREMENT_MANAGER"));
        doReturn(authorities).when(authentication).getAuthorities();
        when(authentication.getName()).thenReturn("proc@example.com");
        when(procurementWorkflowService.listOrders("ROLE_PROCUREMENT_MANAGER", "proc@example.com")).thenReturn(List.of());
        when(procurementWorkflowService.buildExportRows(List.of())).thenReturn(List.of(
                new ProcurementExportRowDto("PO-001", "待供应商确认", "SUP-1", "供应商A", "RM-1", "钢卷", 5.0, 6.0, 30.0, 30.0,
                        "2026-04-15T10:00:00", "", "", "", "", "")
        ));

        ResponseEntity<?> response = procurementController.exportOrders(null, null, null, null, "csv", authentication);

        assertEquals(200, response.getStatusCodeValue());
        assertTrue(String.valueOf(response.getHeaders().getFirst("Content-Disposition")).contains("procurement-records.csv"));
        assertNotNull(response.getBody());
        byte[] body = (byte[]) response.getBody();
        assertTrue(body.length > 3);
        assertEquals((byte) 0xEF, body[0]);
        assertEquals((byte) 0xBB, body[1]);
        assertEquals((byte) 0xBF, body[2]);
        String csv = new String(body, 3, body.length - 3, StandardCharsets.UTF_8);
        assertTrue(csv.contains("PO-001"));
        assertTrue(csv.contains("待供应商确认"));
    }

    @Test
    void exportOrdersAsExcelKeepsChineseReadable() throws Exception {
        java.util.List<org.springframework.security.core.GrantedAuthority> authorities = java.util.List.of(new SimpleGrantedAuthority("ROLE_PROCUREMENT_MANAGER"));
        doReturn(authorities).when(authentication).getAuthorities();
        when(authentication.getName()).thenReturn("proc@example.com");
        when(procurementWorkflowService.listOrders("ROLE_PROCUREMENT_MANAGER", "proc@example.com")).thenReturn(List.of());
        when(procurementWorkflowService.buildExportRows(List.of())).thenReturn(List.of(
                new ProcurementExportRowDto("PO-002", "供应商已发货", "SUP-2", "供应商乙", "RM-2", "不锈钢板", 8.0, 9.5, 76.0, 76.0,
                        "2026-04-16T11:00:00", "2026-04-17T12:00:00", "", "已发货", "加急", "")
        ));

        ResponseEntity<?> response = procurementController.exportOrders(null, null, null, null, "xlsx", authentication);

        assertEquals(200, response.getStatusCodeValue());
        assertNotNull(response.getBody());
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream((byte[]) response.getBody()))) {
            XSSFSheet sheet = workbook.getSheetAt(0);
            assertEquals("状态", sheet.getRow(0).getCell(1).getStringCellValue());
            assertEquals("供应商已发货", sheet.getRow(1).getCell(1).getStringCellValue());
            assertEquals("不锈钢板", sheet.getRow(1).getCell(5).getStringCellValue());
        }
    }

    @Test
    void getSupplierDashboardDelegatesToWorkflowService() {
        SupplierDashboardDto dashboard = new SupplierDashboardDto("供应商甲", 1, 2, 0, 3, List.of(), List.of());
        when(authentication.getName()).thenReturn("supplier@example.com");
        when(procurementWorkflowService.buildSupplierDashboard("supplier@example.com")).thenReturn(dashboard);

        SupplierDashboardDto result = procurementController.getSupplierDashboard(authentication);

        assertEquals("供应商甲", result.getSupplierName());
        verify(procurementWorkflowService).buildSupplierDashboard(eq("supplier@example.com"));
    }

    @Test
    void createOrderAllowsBackendGeneratedPoNo() {
        PurchaseOrder request = new PurchaseOrder();
        User supplier = supplierUser(1L, "supplier01", "供应商甲", "supplier@example.com");
        supplier.setId(1L);
        request.setSupplier(supplier);
        request.setItems(List.of());

        PurchaseOrder saved = new PurchaseOrder();
        saved.setId(12L);
        saved.setPoNo("PO202604151230001234");

        when(authentication.getName()).thenReturn("proc@example.com");
        when(procurementWorkflowService.createPurchaseOrder(request, "proc@example.com")).thenReturn(saved);

        PurchaseOrder result = procurementController.createOrder(request, authentication);

        assertEquals("PO202604151230001234", result.getPoNo());
        verify(procurementWorkflowService).createPurchaseOrder(eq(request), eq("proc@example.com"));
    }

    @Test
    void listSuppliersOnlyReturnsSuppliersReferencedByRawMaterials() {
        User supplierA = supplierUser(1L, "SUP-A", "供应商A", "supplier-a@example.com");
        User supplierB = supplierUser(2L, "SUP-B", "供应商B", "supplier-b@example.com");

        when(procurementWorkflowService.listSuppliersBoundToRawMaterials()).thenReturn(List.of(supplierA, supplierB));

        List<User> result = procurementController.listSuppliers();

        assertEquals(2, result.size());
        assertEquals("SUP-A", result.get(0).getCode());
    }

    @Test
    void listRawMaterialsForSupplierOnlyReturnsOwnMaterials() {
        Product ownMaterial = new Product();
        ownMaterial.setId(10L);
        ownMaterial.setSku("RM-OWN");
        ownMaterial.setName("我的钢卷");
        ownMaterial.setProductType("RAW_MATERIAL");
        ownMaterial.setCreatedAt(LocalDateTime.now());

        Product otherMaterial = new Product();
        otherMaterial.setId(11L);
        otherMaterial.setSku("RM-OTHER");
        otherMaterial.setName("别家铝板");
        otherMaterial.setProductType("RAW_MATERIAL");
        otherMaterial.setCreatedAt(LocalDateTime.now().minusDays(1));

        User supplier = supplierUser(5L, "supplier01", "供应商甲", "supplier@example.com");

        when(authentication.getName()).thenReturn("supplier@example.com");
        doReturn(List.of(new SimpleGrantedAuthority("ROLE_SUPPLIER"))).when(authentication).getAuthorities();
        when(procurementWorkflowService.resolveSupplierAccount("supplier@example.com")).thenReturn(supplier);
        when(productRepository.findAll()).thenReturn(List.of(ownMaterial, otherMaterial));
        when(procurementWorkflowService.isSupplierRelatedMaterial(ownMaterial, supplier)).thenReturn(true);
        when(procurementWorkflowService.isSupplierRelatedMaterial(otherMaterial, supplier)).thenReturn(false);

        List<Product> result = procurementController.listRawMaterials(null, null, null, authentication);

        assertEquals(1, result.size());
        assertEquals("RM-OWN", result.get(0).getSku());
    }

    @Test
    void createRawMaterialForSupplierBindsPreferredSupplierAutomatically() {
        ProcurementController.RawMaterialRequest request = new ProcurementController.RawMaterialRequest();
        request.setName("供应商专属钢板");

        User supplier = supplierUser(8L, "supplier08", "供应商甲", "supplier@example.com");

        when(authentication.getName()).thenReturn("supplier@example.com");
        doReturn(List.of(new SimpleGrantedAuthority("ROLE_SUPPLIER"))).when(authentication).getAuthorities();
        when(procurementWorkflowService.resolveSupplierAccount("supplier@example.com")).thenReturn(supplier);
        when(productRepository.findBySkuIgnoreCase(any())).thenReturn(Optional.empty());
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ResponseEntity<?> response = procurementController.createRawMaterial(request, authentication);

        assertEquals(200, response.getStatusCodeValue());
        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(captor.capture());
        assertEquals("供应商甲", captor.getValue().getPreferredSupplier());
        assertFalse(captor.getValue().getSku().isBlank());
    }

    @Test
    void importRawMaterialsForSupplierBindsPreferredSupplierAndSucceeds() throws Exception {
        byte[] bytes = buildWorkbook();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "raw-materials.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                bytes
        );

        User supplier = supplierUser(9L, "supplier09", "供应商甲", "supplier@example.com");

        when(authentication.getName()).thenReturn("supplier@example.com");
        doReturn(List.of(new SimpleGrantedAuthority("ROLE_SUPPLIER"))).when(authentication).getAuthorities();
        when(procurementWorkflowService.resolveSupplierAccount("supplier@example.com")).thenReturn(supplier);
        when(productRepository.findBySkuIgnoreCase("RM-001")).thenReturn(Optional.empty());
        when(productRepository.findBySkuIgnoreCase("RM-002")).thenReturn(Optional.empty());
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ResponseEntity<ProcurementController.ImportResult> response = procurementController.importRawMaterials(file, authentication);

        assertEquals(200, response.getStatusCodeValue());
        assertNotNull(response.getBody());
        assertEquals(2, response.getBody().getCreatedCount());
        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        assertTrue(captor.getAllValues().stream().allMatch(product -> "供应商甲".equals(product.getPreferredSupplier())));
        assertTrue(response.getBody().getErrors().isEmpty());
    }

    private User supplierUser(Long id, String username, String fullName, String email) {
        Role role = new Role();
        role.setName("ROLE_SUPPLIER");

        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setFullName(fullName);
        user.setEmail(email);
        user.setRoles(java.util.Set.of(role));
        return user;
    }

    private byte[] buildWorkbook() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("raw-material-template");
            var header = sheet.createRow(0);
            String[] headers = {
                    "sku", "name", "materialCategory", "specification", "unit", "unitPrice",
                    "preferredSupplier", "origin", "safetyStock", "leadTimeDays", "description"
            };
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
            }
            var row1 = sheet.createRow(1);
            row1.createCell(0).setCellValue("RM-001");
            row1.createCell(1).setCellValue("钢卷");
            row1.createCell(2).setCellValue("钢材");
            row1.createCell(3).setCellValue("1.5mm");
            row1.createCell(4).setCellValue("kg");
            row1.createCell(5).setCellValue(4.5);
            row1.createCell(6).setCellValue("供应商A");
            row1.createCell(7).setCellValue("无锡");
            row1.createCell(8).setCellValue(50);
            row1.createCell(9).setCellValue(5);
            row1.createCell(10).setCellValue("测试新增");

            var row2 = sheet.createRow(2);
            row2.createCell(0).setCellValue("RM-002");
            row2.createCell(1).setCellValue("铝板");
            row2.createCell(2).setCellValue("金属");
            row2.createCell(3).setCellValue("AL-3mm");
            row2.createCell(4).setCellValue("kg");
            row2.createCell(5).setCellValue(7.2);
            row2.createCell(6).setCellValue("供应商B");
            row2.createCell(7).setCellValue("苏州");
            row2.createCell(8).setCellValue(20);
            row2.createCell(9).setCellValue(3);
            row2.createCell(10).setCellValue("测试更新");

            workbook.write(out);
            return out.toByteArray();
        }
    }
}

