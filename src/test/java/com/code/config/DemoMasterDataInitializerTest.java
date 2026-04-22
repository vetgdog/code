package com.code.config;

import com.code.entity.InventoryItem;
import com.code.entity.Product;
import com.code.entity.Role;
import com.code.entity.User;
import com.code.entity.Warehouse;
import com.code.repository.InventoryItemRepository;
import com.code.repository.ProductRepository;
import com.code.repository.RoleRepository;
import com.code.repository.UserRepository;
import com.code.repository.WarehouseRepository;
import com.code.support.WarehouseDefaults;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DemoMasterDataInitializerTest {

    @InjectMocks
    private DemoMasterDataInitializer initializer;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private WarehouseRepository warehouseRepository;

    @Mock
    private InventoryItemRepository inventoryItemRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Test
    void runSeedsTwoWarehousesUsersProductsAndInventoryStubs() throws Exception {
        AtomicLong warehouseIds = new AtomicLong(1L);
        AtomicLong productIds = new AtomicLong(100L);

        when(roleRepository.findByName(anyString())).thenReturn(Optional.empty());
        when(roleRepository.save(any(Role.class))).thenAnswer(invocation -> invocation.getArgument(0));

        when(warehouseRepository.findByCodeIgnoreCase(anyString())).thenReturn(Optional.empty());
        when(warehouseRepository.save(any(Warehouse.class))).thenAnswer(invocation -> {
            Warehouse warehouse = invocation.getArgument(0);
            if (warehouse.getId() == null) {
                warehouse.setId(warehouseIds.getAndIncrement());
            }
            return warehouse;
        });

        when(userRepository.findByEmailIgnoreCase(anyString())).thenReturn(Optional.empty());
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        when(passwordEncoder.encode("密码123456")).thenReturn("encoded-password");

        when(productRepository.findBySkuIgnoreCase(anyString())).thenReturn(Optional.empty());
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
            Product product = invocation.getArgument(0);
            if (product.getId() == null) {
                product.setId(productIds.getAndIncrement());
            }
            return product;
        });

        when(inventoryItemRepository.findByProductId(anyLong())).thenReturn(List.of());
        when(inventoryItemRepository.save(any(InventoryItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        initializer.run(new DefaultApplicationArguments(new String[0]));

        verify(warehouseRepository, times(2)).save(any(Warehouse.class));
        verify(userRepository, times(4)).save(any(User.class));
        verify(productRepository, times(7)).save(any(Product.class));
        verify(inventoryItemRepository, times(7)).save(any(InventoryItem.class));

        org.mockito.ArgumentCaptor<Warehouse> warehouseCaptor = org.mockito.ArgumentCaptor.forClass(Warehouse.class);
        verify(warehouseRepository, times(2)).save(warehouseCaptor.capture());
        assertTrue(warehouseCaptor.getAllValues().stream().anyMatch(warehouse -> WarehouseDefaults.RAW_MATERIAL_WAREHOUSE_CODE.equals(warehouse.getCode())));
        assertTrue(warehouseCaptor.getAllValues().stream().anyMatch(warehouse -> WarehouseDefaults.FINISHED_GOODS_WAREHOUSE_CODE.equals(warehouse.getCode())));

        org.mockito.ArgumentCaptor<Product> productCaptor = org.mockito.ArgumentCaptor.forClass(Product.class);
        verify(productRepository, times(7)).save(productCaptor.capture());
        long rawMaterialCount = productCaptor.getAllValues().stream().filter(product -> "RAW_MATERIAL".equals(product.getProductType())).count();
        long finishedGoodsCount = productCaptor.getAllValues().stream().filter(product -> "FINISHED_GOOD".equals(product.getProductType())).count();
        assertEquals(4, rawMaterialCount);
        assertEquals(3, finishedGoodsCount);
    }
}
