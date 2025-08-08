package com.eewms.controller;

import com.eewms.dto.ProductDetailsDTO;
import com.eewms.dto.ProductFormDTO;
import com.eewms.services.IProductServices;
import com.eewms.services.ISettingServices;
import com.eewms.services.ImageUploadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProductController.class) // Chỉ load lớp ProductController để test web layer
public class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc; // Dùng để giả lập các request HTTP

    @MockBean
    private IProductServices productService; // Giả lập service để không phụ thuộc logic thật

    @MockBean
    private ISettingServices settingService;

    @MockBean
    private ImageUploadService imageUploadService;

    @Autowired
    private WebApplicationContext webApplicationContext;

    // Cài đặt MockMvc trước mỗi test
    @BeforeEach
    public void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Test
    void testListWithKeyword() throws Exception {
        // Tạo danh sách sản phẩm giả
        List<ProductDetailsDTO> mockProducts = List.of(new ProductDetailsDTO());

        // Khi gọi service tìm kiếm thì trả về danh sách giả
        when(productService.searchByKeyword("laptop")).thenReturn(mockProducts);
        when(settingService.findByTypeAndActive(any())).thenReturn(List.of());

        // Gửi request GET
        mockMvc.perform(get("/products").param("keyword", "laptop"))
                .andExpect(status().isOk()) // HTTP 200
                .andExpect(model().attributeExists("products")) // model có "products"
                .andExpect(view().name("product/product-list")); // trả về đúng view

        // Kiểm tra service có được gọi đúng không
        verify(productService).searchByKeyword("laptop");
    }


    @Test
    void testCreateProductValid() throws Exception {
        // Tạo file ảnh giả lập
        MockMultipartFile image = new MockMultipartFile("images", "image.png", "image/png", "test image content".getBytes());

        // Gửi POST request (multipart/form-data)
        mockMvc.perform(multipart("/products")
                        .file(image)
                        .param("name", "New Product")
                        .param("price", "100")
                        .flashAttr("productDTO", new ProductFormDTO()) // gửi DTO giả
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isOk()); // hoặc .is3xxRedirection() nếu controller redirect

    }
}
