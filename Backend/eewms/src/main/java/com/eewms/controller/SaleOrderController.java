package com.eewms.controller;

import com.eewms.constant.ItemOrigin;
import com.eewms.dto.SaleOrderMapper;
import com.eewms.dto.SaleOrderRequestDTO;
import com.eewms.dto.SaleOrderResponseDTO;
import com.eewms.entities.SaleOrder;
import com.eewms.repository.ComboRepository;
import com.eewms.repository.GoodIssueNoteRepository;
import com.eewms.repository.SaleOrderComboRepository;
import com.eewms.repository.purchaseRequest.PurchaseRequestRepository;
import com.eewms.services.IComboService;
import com.eewms.services.ICustomerService;
import com.eewms.services.IGoodIssueService;
import com.eewms.services.IProductServices;
import com.eewms.services.ISaleOrderService;
import com.eewms.utils.ComboJsonHelper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/sale-orders")
@RequiredArgsConstructor
public class SaleOrderController {

    private final ISaleOrderService saleOrderService;
    private final ICustomerService customerService;
    private final IProductServices productService;
    private final IGoodIssueService goodIssueService;
    private final IComboService comboService;
    private final ComboJsonHelper comboJsonHelper;
    private final GoodIssueNoteRepository goodIssueRepository;
    private final SaleOrderComboRepository saleOrderComboRepository;
    private final PurchaseRequestRepository prRepo;
    private final ComboRepository cbRepo;

    // ========== LIST ==========
    @GetMapping
    public String listOrders(@RequestParam(value = "keyword", required = false) String keyword,
                             Model model) {
        if (!model.containsAttribute("saleOrderForm")) {
            model.addAttribute("saleOrderForm", new SaleOrderRequestDTO());
        }
        if (keyword != null && !keyword.isBlank()) {
            model.addAttribute("sale_orders", saleOrderService.searchByKeyword(keyword));
        } else {
            model.addAttribute("sale_orders", saleOrderService.getAllOrders());
        }
        model.addAttribute("customers", customerService.findAll());
        model.addAttribute("products", productService.getAll()); // dùng cho list
        model.addAttribute("keyword", keyword);
        return "sale-order/sale-order-list";
    }

    // ========== CREATE ==========
    @GetMapping("/create")
    public String showCreateForm(Model model) {
        SaleOrderRequestDTO form = SaleOrderRequestDTO.builder()
                .soCode(saleOrderService.generateNextCode())
                .status(SaleOrder.SaleOrderStatus.PENDING)
                .build();

        model.addAttribute("saleOrderForm", form);
        model.addAttribute("customers", customerService.findAll());
        model.addAttribute("products", productService.getAllActiveProducts());
        model.addAttribute("combos", comboService.getAllActive());
        model.addAttribute("orderStatuses", SaleOrder.SaleOrderStatus.values());

        return "sale-order/sale-order-form";
    }

    // API tiện ích: lấy mã tiếp theo cho UI
    @GetMapping("/next-code")
    @ResponseBody
    public Map<String, String> nextCode() {
        return Map.of("code", saleOrderService.generateNextCode());
    }

    /**
     * Tạo đơn hàng.
     * - Nếu action=SELL → chuyển sang trang *xem trước phiếu xuất* (chưa lưu).
     * - Nếu action=SAVE → quay về danh sách đơn.
     */
    @PostMapping("/create")
    public String createOrder(@Valid @ModelAttribute("saleOrderForm") SaleOrderRequestDTO dto,
                              org.springframework.validation.BindingResult result,
                              @RequestParam(name = "action", defaultValue = "SAVE") String action,
                              Model model,
                              RedirectAttributes ra) {

        if (dto.getSoCode() == null || dto.getSoCode().isBlank()) {
            dto.setSoCode(saleOrderService.generateNextCode());
        }
        if (dto.getStatus() == null) {
            dto.setStatus(SaleOrder.SaleOrderStatus.PENDING);
        }

        if (result.hasErrors()) {
            model.addAttribute("saleOrderForm", dto);
            model.addAttribute("customers", customerService.findAll());
            model.addAttribute("products", productService.getAllActiveProducts());
            model.addAttribute("combos", comboService.getAllActive());
            model.addAttribute("orderStatuses", SaleOrder.SaleOrderStatus.values());
            return "sale-order/sale-order-form";
        }

        try {
            final String currentUsername =
                    SecurityContextHolder.getContext().getAuthentication().getName();

            // Tạo đơn
            SaleOrderResponseDTO created = saleOrderService.createOrder(dto, currentUsername);

            if ("SELL".equalsIgnoreCase(action)) {
                Integer soId = created.getSoId(); // yêu cầu mapper map đúng soId
                if (soId == null) {
                    ra.addFlashAttribute("error",
                            "Đã tạo đơn nhưng không lấy được ID đơn hàng. " +
                                    "Vui lòng kiểm tra SaleOrderMapper.toOrderResponseDTO có map trường soId.");
                    return "redirect:/sale-orders";
                }
                ra.addFlashAttribute("success", "Đã tạo đơn. Vui lòng kiểm tra và Lưu phiếu xuất.");
                return "redirect:/good-issue/create-from-order/" + soId;
            }

            ra.addFlashAttribute("success", "Tạo đơn hàng thành công. Mã: " + created.getOrderCode());
            return "redirect:/sale-orders";

        } catch (Exception ex) {
            ra.addFlashAttribute("error",
                    "Lỗi khi tạo đơn hàng: " + (ex.getMessage() == null ? "Xem log server" : ex.getMessage()));
            return "redirect:/sale-orders";
        }
    }

    // ========== EDIT ==========
    @GetMapping("/{id}/edit")
    public String editOrder(@PathVariable Integer id, Model model, RedirectAttributes ra) {
        // Lấy DTO nhẹ để hiển thị thông tin chung (mã, trạng thái, ...).
        SaleOrderResponseDTO dto = saleOrderService.getById(id);
        if (dto == null) {
            ra.addFlashAttribute("error", "Không tìm thấy đơn hàng.");
            return "redirect:/sale-orders";
        }

        // Nếu không còn trạng thái PENDING thì hiển thị trang chi tiết readonly
        if (dto.getStatus() != SaleOrder.SaleOrderStatus.PENDING) {
            SaleOrder orderEntityReadonly = saleOrderService.getOrderEntityById(id);
            model.addAttribute("saleOrder", orderEntityReadonly);
            return "sale-order/sale-order-detail";
        }

        // Lấy entity đầy đủ để build form sửa
        SaleOrder orderEntity = saleOrderService.getOrderEntityById(id);

        // 1) Làm sạch ghi chú nếu từng dính PayOS
        String cleanedDescription = orderEntity.getDescription();
        if (cleanedDescription != null && cleanedDescription.startsWith("[PAYOS]")) {
            cleanedDescription = "";
        }

        // 2) Chỉ lấy các dòng manual để cho phép sửa
        var manualDetails = orderEntity.getDetails().stream()
                .filter(d -> d.getOrigin() == ItemOrigin.MANUAL)
                .map(SaleOrderMapper::toDetailDTO)
                .toList();

        // 3) Combo đã chọn -> gom lại theo id để JS dựng lại dòng combo
        var expandedComboIds = saleOrderService.getComboIdsExpanded(id);
        Map<Long, Integer> comboCounts = new LinkedHashMap<>();
        for (Long cid : expandedComboIds) comboCounts.merge(cid, 1, Integer::sum);

        // 4) Build form bind ra view
        var form = SaleOrderRequestDTO.builder()
                .customerId(orderEntity.getCustomer() != null ? orderEntity.getCustomer().getId() : null)
                .description(cleanedDescription)
                .details(manualDetails)
                .comboCounts(comboCounts)
                .build();

        // 5) Chuẩn bị mảng products “nhẹ” để Thymeleaf serialize ra JSON chắc chắn
        var productsLite = new java.util.ArrayList<Map<String, Object>>();
        for (var p : productService.getAllActiveProducts()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", p.getId());
            m.put("name", p.getName());
            m.put("listingPrice", p.getListingPrice());
            m.put("quantity", p.getQuantity());
            productsLite.add(m);
        }

        // 6) Đổ dữ liệu cho view
        model.addAttribute("saleOrder", dto);
        model.addAttribute("saleOrderForm", form);
        model.addAttribute("customers", customerService.findAll());
        model.addAttribute("combos", cbRepo.findAll());
        model.addAttribute("prExists", prRepo.existsBySaleOrder_SoId(id));

        // ✅ dùng mảng “nhẹ” cho JS trang sửa
        model.addAttribute("productsLite", productsLite);

        // ✅ id để form action submit chính xác
        model.addAttribute("saleOrderId", id);

        return "sale-order/sale-order-edit";
    }

    // ====== SAVE ITEMS (PRG, không dùng @Valid để tránh re-render form) ======
    @PostMapping("/{id}/items/edit")
    public String updateOrderItems(@PathVariable Integer id,
                                   @ModelAttribute("saleOrderForm") SaleOrderRequestDTO form,
                                   RedirectAttributes ra) {
        try {
            saleOrderService.updateOrderItems(id, form);

            // Lấy mã đơn để hiển thị toast
            String code = null;
            try { code = saleOrderService.getOrderEntityById(id).getSoCode(); } catch (Exception ignore) {}
            if (code == null) {
                try { code = saleOrderService.getById(id).getOrderCode(); } catch (Exception ignore) {}
            }
            if (code == null) code = "ORD#" + id;

            // Gửi flash attribute cho trang danh sách
            ra.addFlashAttribute("toastSuccess", "Đơn " + code + " đã được lưu thành công.");

            // Quay về danh sách
            return "redirect:/sale-orders";

        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/sale-orders/" + id + "/edit";
        }
    }


    @GetMapping("/{id}/view")
    public String viewOrderDetails(@PathVariable Integer id, Model model) {
        SaleOrder saleOrder = saleOrderService.getOrderEntityById(id);

        // ✅ Cho phép xem nếu KHÔNG phải PENDING
        if (saleOrder.getStatus() == SaleOrder.SaleOrderStatus.PENDING) {
            return "redirect:/sale-orders/" + id + "/edit";
        }

        model.addAttribute("saleOrder", saleOrder);

        // === NEW: tính ĐÃ GIAO / CÒN LẠI cho từng product ===
        // 1 query group-by để lấy tổng đã xuất theo product
        List<Object[]> rows = goodIssueRepository
                .sumIssuedBySaleOrderGroupByProduct(saleOrder.getSoId());

        // Map<productId, issuedQty>
        java.util.Map<Integer, Integer> issuedByPid = new java.util.HashMap<>();
        for (Object[] r : rows) {
            Integer pid = (Integer) r[0];
            Number sum = (Number) r[1];
            issuedByPid.put(pid, sum == null ? 0 : sum.intValue());
        }

        // Map<productId, remainingQty> = ordered - issued (>=0)
        java.util.Map<Integer, Integer> remainingByPid = new java.util.HashMap<>();
        for (var d : saleOrder.getDetails()) {
            Integer pid = d.getProduct() != null ? d.getProduct().getId() : null;
            if (pid == null) continue;
            int ordered = d.getOrderedQuantity() != null ? d.getOrderedQuantity() : 0;
            int issued  = issuedByPid.getOrDefault(pid, 0);
            int remain  = Math.max(0, ordered - issued);
            remainingByPid.put(pid, remain);
        }

        model.addAttribute("issuedByPid", issuedByPid);
        model.addAttribute("remainingByPid", remainingByPid);

        return "sale-order/sale-order-detail";
    }

    @PostMapping("/{id}/actions/mark-unpaid")
    public String markUnpaid(@PathVariable Integer id, RedirectAttributes ra) {
        SaleOrder so = saleOrderService.getOrderEntityById(id);
        if (so == null) {
            ra.addFlashAttribute("error", "Không tìm thấy đơn hàng.");
            return "redirect:/sale-orders";
        }

        if (so.getPaymentStatus() == SaleOrder.PaymentStatus.PAID) {
            ra.addFlashAttribute("warning", "Đơn đã thanh toán, không thể chuyển sang công nợ.");
            return "redirect:/sale-orders/" + id + "/edit";
        }

        saleOrderService.updatePaymentStatus(id, SaleOrder.PaymentStatus.UNPAID);
        ra.addFlashAttribute("success", "Đơn đã chuyển sang UNPAID (bán công nợ).");
        return "redirect:/sale-orders/" + id + "/edit";
    }

    // ====== DELETE (chỉ cho PENDING) ======
    @PostMapping("/{id}/delete")
    public String deleteOrder(@PathVariable Integer id, RedirectAttributes ra) {
        try {
            saleOrderService.deleteIfPending(id);
            ra.addFlashAttribute("toastSuccess", "Đã xoá đơn hàng #" + id + " thành công.");
        } catch (Exception ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/sale-orders";
    }
}
