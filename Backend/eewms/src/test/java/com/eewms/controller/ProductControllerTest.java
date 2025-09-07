package com.eewms.controller;

import com.eewms.constant.SettingType;
import com.eewms.dto.ProductDetailsDTO;
import com.eewms.dto.ProductFormDTO;
import com.eewms.exception.InventoryException;
import com.eewms.repository.GoodIssueNoteRepository;
import com.eewms.repository.warehouseReceipt.WarehouseReceiptItemRepository;
import com.eewms.services.IProductServices;
import com.eewms.services.ISettingServices;
import com.eewms.services.ISupplierService;
import com.eewms.services.ImageUploadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.view.InternalResourceViewResolver;

import java.math.BigDecimal;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;


@ExtendWith(MockitoExtension.class)
class ProductControllerTest {

    @Mock private IProductServices productService;
    @Mock private ISettingServices settingService;
    @Mock private ISupplierService supplierService;
    @Mock private ImageUploadService imageUploadService;
    @Mock private WarehouseReceiptItemRepository warehouseReceiptItemRepository;
    @Mock private GoodIssueNoteRepository goodIssueNoteRepository;

    // Tạo controller bằng tay và inject mocks
    @InjectMocks private ProductController controller;

    private MockMvc mockMvc;

    // ViewResolver đơn giản để trả được view name "product/product-list"
    private InternalResourceViewResolver viewResolver() {
        InternalResourceViewResolver vr = new InternalResourceViewResolver();
        vr.setPrefix("/templates/");     // giá trị giả; không thực sự render file
        vr.setSuffix(".html");
        return vr;
    }

    @BeforeEach
    void setup() {
        this.mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setViewResolvers(viewResolver())
                .build();
    }

    private ProductDetailsDTO dto(int id, String code, String name, int qty, String price) {
        ProductDetailsDTO d = new ProductDetailsDTO();
        d.setId(id);
        d.setCode(code);
        d.setName(name);
        d.setQuantity(qty);
        d.setListingPrice(new BigDecimal(price));
        return d;
    }

    @Test
    @DisplayName("GET /products: không keyword → lấy all, tính returned/newQty map, trả view product-list")
    void list_NoKeyword_Success() throws Exception {
        var p1 = dto(1, "P01", "Quat", 10, "120");
        var p2 = dto(2, "P02", "Den",  3, "50");
        when(productService.getAll()).thenReturn(List.of(p1, p2));

        // repo trả List<Object[]>: [productId, sumQty]
        when(warehouseReceiptItemRepository.sumReturnedByProduct())
                .thenReturn(List.of(new Object[]{1, 6L}, new Object[]{2, 1L}));

        when(settingService.findByTypeAndActive(SettingType.UNIT)).thenReturn(List.of());
        when(settingService.findByTypeAndActive(SettingType.BRAND)).thenReturn(List.of());
        when(settingService.findByTypeAndActive(SettingType.CATEGORY)).thenReturn(List.of());
        when(supplierService.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/products"))
                .andExpect(status().isOk())
                .andExpect(view().name("product/product-list"))
                .andExpect(model().attribute("products", hasSize(2)))
                .andExpect(model().attribute("keyword", nullValue()))
                .andExpect(model().attribute("returnedQtyMap", allOf(
                        hasEntry(1, 6L), hasEntry(2, 1L)
                )))
                .andExpect(model().attribute("newQtyMap", allOf(
                        hasEntry(1, 4L), hasEntry(2, 2L)
                )))
                .andExpect(model().attributeExists("units", "brands", "categories", "suppliers"));
    }

    @Test
    @DisplayName("POST /products: InventoryException (mã trùng) → trả lại view product-list + hasFormError")
    void create_DuplicateCode_ReturnsListView() throws Exception {
        doThrow(new InventoryException("Mã sản phẩm đã tồn tại"))
                .when(productService).create(ArgumentMatchers.any(ProductFormDTO.class));

        when(productService.getAll()).thenReturn(List.of());
        when(settingService.findByTypeAndActive(SettingType.UNIT)).thenReturn(List.of());
        when(settingService.findByTypeAndActive(SettingType.BRAND)).thenReturn(List.of());
        when(settingService.findByTypeAndActive(SettingType.CATEGORY)).thenReturn(List.of());
        when(supplierService.findAll()).thenReturn(List.of());

        mockMvc.perform(multipart("/products")
                        .param("code", "P01")
                        .param("name", "Quat")
                        .param("quantity", "5")
                        .param("listingPrice", "120")
                        .param("originPrice", "100")
                        .param("unitId", "1")
                        .param("categoryId", "2")
                        .param("brandId", "3"))
                .andExpect(status().isOk())
                .andExpect(view().name("product/product-list"))
                .andExpect(model().attribute("hasFormError", is(true)))
                .andExpect(model().attributeExists("products", "units", "brands", "categories", "suppliers"));

        verify(productService).create(ArgumentMatchers.any(ProductFormDTO.class));
        verify(productService).getAll();
    }

    @Test
    @DisplayName("POST /products: tạo mới thành công → redirect /products + flash success")
    void create_Success_Redirect() throws Exception {
        doAnswer(inv -> null).when(productService)
                .create(ArgumentMatchers.any(ProductFormDTO.class));

        mockMvc.perform(multipart("/products")
                        .param("code", "P01")
                        .param("name", "Quat")
                        .param("quantity", "5")
                        .param("listingPrice", "120")
                        .param("originPrice", "100")
                        .param("unitId", "1")
                        .param("categoryId", "2")
                        .param("brandId", "3"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/products"))
                .andExpect(flash().attribute("messageType", "success"));

        verify(productService).create(ArgumentMatchers.any(ProductFormDTO.class));
    }
}
