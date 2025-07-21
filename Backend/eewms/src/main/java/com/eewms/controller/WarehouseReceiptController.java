package com.eewms.controller;

import com.eewms.dto.warehouseReceipt.WarehouseReceiptDTO;
import com.eewms.entities.*;
import com.eewms.repository.PurchaseOrderItemRepository;
import com.eewms.repository.PurchaseOrderRepository;
import com.eewms.repository.WarehouseRepository;
import com.eewms.repository.warehouseReceipt.WarehouseReceiptRepository;
import com.eewms.repository.UserRepository;
import com.eewms.services.IWarehouseReceiptService;
import java.security.Principal;
import java.time.LocalDateTime;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;


import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;

import java.util.List;

@Controller
@RequestMapping("/admin/warehouse-receipts")
@RequiredArgsConstructor
public class WarehouseReceiptController {

    private final WarehouseReceiptRepository warehouseReceiptRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderItemRepository purchaseOrderItemRepository;
    private final WarehouseRepository warehouseRepository;
    private final UserRepository userRepository;
    private final IWarehouseReceiptService warehouseReceiptService;

    @GetMapping
    public String listReceipts(Model model) {
        List<WarehouseReceipt> receipts = warehouseReceiptRepository.findAll();
        model.addAttribute("receipts", receipts);
        return "warehouse-receipt-list";
    }

    @GetMapping("/form")
    public String showCreateForm(@RequestParam("purchaseOrderId") Long purchaseOrderId, Model model) {
        PurchaseOrder order = purchaseOrderRepository.findById(purchaseOrderId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy đơn hàng"));

        List<PurchaseOrderItem> orderItems = purchaseOrderItemRepository.findByPurchaseOrderId(purchaseOrderId);
        List<Warehouse> warehouses = warehouseRepository.findAll();

        model.addAttribute("purchaseOrder", order);
        model.addAttribute("orderItems", orderItems);
        model.addAttribute("warehouses", warehouses);
        model.addAttribute("warehouseReceiptDTO", new WarehouseReceiptDTO());

        return "warehouse-receipt-form"; // trỏ đến templates/warehouse-receipt-form.html
    }

    @PostMapping("/save")
    public String saveReceipt(@ModelAttribute WarehouseReceiptDTO dto,
                              Principal principal,
                              RedirectAttributes redirectAttributes) {

        // Lấy user hiện tại
        User user = userRepository.findByUsername(principal.getName())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng"));

        // Lấy đơn hàng
        PurchaseOrder order = purchaseOrderRepository.findById(dto.getPurchaseOrderId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy đơn hàng"));

        // Gắn người tạo + thời gian
        dto.setCreatedAt(LocalDateTime.now());
        dto.setCreatedByName(user.getFullName());

        // Gọi service để lưu
        warehouseReceiptService.saveReceipt(dto, order, user);

        // Chuyển hướng + thông báo
        redirectAttributes.addFlashAttribute("message", "Tạo phiếu nhập kho thành công!");
        return "redirect:/admin/warehouse-receipts";
    }

}
