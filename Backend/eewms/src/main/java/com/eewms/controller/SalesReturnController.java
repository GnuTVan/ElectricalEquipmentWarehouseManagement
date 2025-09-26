package com.eewms.controller;

import com.eewms.constant.ReturnReason;
import com.eewms.constant.ReturnSettlementOption;
import com.eewms.dto.returning.SalesReturnDTO;
import com.eewms.dto.returning.SalesReturnItemDTO;
import com.eewms.entities.SaleOrder;
import com.eewms.entities.SalesReturn;
import com.eewms.repository.SaleOrderRepository;
import com.eewms.repository.WarehouseRepository;
import com.eewms.repository.returning.SalesReturnRepository;
import com.eewms.services.ISalesReturnService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Controller
@RequestMapping("/admin/sales-returns")
@RequiredArgsConstructor
public class SalesReturnController {

    private final SalesReturnRepository salesReturnRepository;
    private final SaleOrderRepository saleOrderRepository;
    private final ISalesReturnService salesReturnService;
    private final com.eewms.repository.returning.SalesReturnItemRepository salesReturnItemRepository;
    private final WarehouseRepository warehouseRepository;

    /* ============== LIST ============== */
    @GetMapping
    public String list(Model model) {
        var data = salesReturnRepository.findAllWithSaleOrder(Sort.by(Sort.Direction.DESC, "id"));
        model.addAttribute("data", data);
        return "returns/return-list";
    }

    /* ============== DETAIL ============== */
    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        SalesReturnDTO dto = salesReturnService.getById(id);
        model.addAttribute("dto", dto);
        model.addAttribute("warehouses", warehouseRepository.findAll());
        return "returns/return-detail";
    }

    /* ============== CREATE FORM ============== */
    @GetMapping("/create")
    public String createForm(@RequestParam(name = "saleOrderId", required = false) Integer saleOrderId, Model model) {
        SaleOrder so = null;
        java.util.Map<Integer, Long> returnedMap = java.util.Collections.emptyMap();

        if (saleOrderId != null) {
            so = saleOrderRepository.findByIdWithDetails(saleOrderId).orElse(null);
            if (so != null) {
                var statuses = java.util.List.of(
                        com.eewms.constant.ReturnStatus.CHO_DUYET,
                        com.eewms.constant.ReturnStatus.DA_DUYET,
                        com.eewms.constant.ReturnStatus.DA_NHAP_KHO,
                        com.eewms.constant.ReturnStatus.HOAN_TAT
                );
                returnedMap = new java.util.HashMap<>();
                for (var d : so.getDetails()) {
                    Long ret = salesReturnItemRepository.sumReturnedBySoAndProduct(so.getSoId(), d.getProduct().getId(), statuses);
                    returnedMap.put(d.getProduct().getId(), ret == null ? 0L : ret);
                }
            }
        }
        model.addAttribute("saleOrder", so);
        model.addAttribute("returnedMap", returnedMap);
        return "returns/return-form";
    }

    /* ============== CREATE DRAFT ============== */
    @PostMapping
    public String createDraft(@RequestParam("saleOrderId") Integer saleOrderId,
                              @RequestParam("productId") List<Long> productIds,
                              @RequestParam(value = "qtyLoi", required = false) List<Integer> qtyLoi,
                              @RequestParam(value = "qtyHong", required = false) List<Integer> qtyHong,
                              @RequestParam(value = "note", required = false) List<String> notes,
                              RedirectAttributes ra) {

        String username = SecurityContextHolder.getContext().getAuthentication().getName();

        // Lấy SO để map giá
        var so = saleOrderRepository.findByIdWithDetails(saleOrderId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn bán"));
        java.util.Map<Long, java.math.BigDecimal> priceByPid =
                so.getDetails().stream().collect(java.util.stream.Collectors.toMap(
                        d -> d.getProduct().getId().longValue(),
                        com.eewms.entities.SaleOrderDetail::getPrice,
                        (a,b) -> a));

        java.util.List<com.eewms.dto.returning.SalesReturnItemDTO> items = new java.util.ArrayList<>();
        int n = productIds == null ? 0 : productIds.size();
        for (int i = 0; i < n; i++) {
            Long pid = productIds.get(i);
            if (pid == null) continue;
            String lineNote = (notes != null && notes.size() > i) ? notes.get(i) : null;
            int qLoi  = (qtyLoi  != null && qtyLoi.size()  > i && qtyLoi.get(i)  != null) ? Math.max(0, qtyLoi.get(i))  : 0;
            int qHong = (qtyHong != null && qtyHong.size() > i && qtyHong.get(i) != null) ? Math.max(0, qtyHong.get(i)) : 0;

            java.math.BigDecimal price = priceByPid.getOrDefault(pid, java.math.BigDecimal.ZERO);

            if (qLoi > 0) {
                items.add(com.eewms.dto.returning.SalesReturnItemDTO.builder()
                        .productId(pid).quantity(qLoi).unitPrice(price).note(lineNote)
                        .reason(com.eewms.constant.ReturnReason.HANG_LOI).build());
            }
            if (qHong > 0) {
                items.add(com.eewms.dto.returning.SalesReturnItemDTO.builder()
                        .productId(pid).quantity(qHong).unitPrice(price).note(lineNote)
                        .reason(com.eewms.constant.ReturnReason.HANG_HONG).build());
            }
        }

        if (items.isEmpty()) {
            ra.addFlashAttribute("error", "Chưa nhập số lượng hoàn.");
            return "redirect:/admin/sales-returns/create?saleOrderId=" + saleOrderId;
        }

        var dto = com.eewms.dto.returning.SalesReturnDTO.builder()
                .saleOrderId(saleOrderId)
                .items(items)
                .build();

        try {
            var saved = salesReturnService.createDraft(dto, username);
            ra.addFlashAttribute("success", "Đã tạo phiếu " + saved.getCode());
            return "redirect:/admin/sales-returns/" + saved.getId();
        } catch (Exception ex) {
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:/admin/sales-returns/create?saleOrderId=" + saleOrderId;
        }
    }

    /* ============== STATE ACTIONS ============== */
    @PostMapping("/{id}/submit")
    public String submit(@PathVariable Long id, RedirectAttributes ra) {
        try {
            salesReturnService.submit(id);
            ra.addFlashAttribute("success", "Đã gửi duyệt.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/sales-returns/" + id;
    }

    @PostMapping("/{id}/approve")
    public String approve(@PathVariable Long id,
                          @RequestParam(value = "managerNote", required = false) String managerNote,
                          RedirectAttributes ra) {
        try {
            salesReturnService.approve(id, managerNote);
            ra.addFlashAttribute("success", "Đã duyệt phiếu.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/sales-returns/" + id;
    }

    @PostMapping("/{id}/reject")
    public String reject(@PathVariable Long id,
                         @RequestParam("reason") String reason,
                         RedirectAttributes ra) {
        try {
            salesReturnService.reject(id, reason);
            ra.addFlashAttribute("success", "Đã từ chối phiếu.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/sales-returns/" + id;
    }

    @PostMapping("/{id}/receive")
    public String receive(@PathVariable Long id,
                          @RequestParam("settlementOption") ReturnSettlementOption settlementOption,
                          @RequestParam("warehouseId") Long warehouseId,
                          RedirectAttributes ra) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        try {
            salesReturnService.receive(id, username, settlementOption, warehouseId);
            ra.addFlashAttribute("success", "Đã nhập hàng hoàn vào kho.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/sales-returns/" + id;
    }

    @PostMapping("/{id}/complete")
    public String complete(@PathVariable Long id, RedirectAttributes ra) {
        try {
            salesReturnService.complete(id);
            ra.addFlashAttribute("success", "Đã hoàn tất phiếu.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/sales-returns/" + id;
    }


    @PostMapping("/{id}/replacement-request")
    public String createReplacementRequestPost(@PathVariable Long id, RedirectAttributes ra) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        try {
            salesReturnService.createReplacementRequest(id, username);
            ra.addFlashAttribute("success", "Đã tạo yêu cầu đổi hàng (đã sinh phiếu xuất nháp).");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/sales-returns/" + id;
    }

    @GetMapping("/{id}/replacement-request")
    public String createReplacementRequestGet(@PathVariable Long id, RedirectAttributes ra) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        try {
            salesReturnService.createReplacementRequest(id, username);
            ra.addFlashAttribute("success", "Đã tạo yêu cầu đổi hàng (đã sinh phiếu xuất nháp).");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/sales-returns/" + id;
    }

}