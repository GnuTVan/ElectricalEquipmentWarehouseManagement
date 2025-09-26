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
import com.eewms.services.*;
import com.eewms.utils.ComboJsonHelper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
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
    private final IStockLookupService stockLookupService;

    // ========== LIST ==========
//Map URL /sale-orders (GET) → method này.
//
//Nhận các tham số lọc (keyword,status,from,to) + phân trang (page,size), và Model để nhét dữ liệu ra view.
    @GetMapping
    public String listOrders(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) SaleOrder.SaleOrderStatus status,
            @RequestParam(value = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(value = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "100") int size,
            Model model
    ) {
        //Khi quay lại từ redirect (flash attr) thì model có thể đã có form;
        // nếu chưa có thì gắn form trống để tránh lỗi th:object.
        if (!model.containsAttribute("saleOrderForm")) {
            model.addAttribute("saleOrderForm", new SaleOrderRequestDTO());
        }
//gọi saleOrderService.searchWithFilters
        //Chuẩn hóa keyword rỗng thành null.
        //
        //Gọi service để tìm kiếm theo filter, trả về Page<SaleOrder> (có cả content + info trang).
        Page<SaleOrder> result = saleOrderService.searchWithFilters(
                (keyword != null && !keyword.isBlank()) ? keyword : null,
                status, from, to, page, size
        );
        //Đưa danh sách trạng thái cho <select>.
        //
        //Đưa danh sách order (sale_orders) và object page để view vẽ trang.
        model.addAttribute("orderStatuses", SaleOrder.SaleOrderStatus.values());
        model.addAttribute("sale_orders", result.getContent()); // nếu view đang duyệt "sale_orders"
        model.addAttribute("page", result);
        model.addAttribute("size", size);

        // Lưu lại filter hiện tại để view bind giá trị lên input.
        model.addAttribute("keyword", keyword);
        model.addAttribute("status", status);
        model.addAttribute("from", from);
        model.addAttribute("to", to);
       // Đưa thêm dữ liệu tham chiếu (nếu view cần).
        //
        //Trả tên view thymeleaf.
        model.addAttribute("customers", customerService.findAll());
        model.addAttribute("products", productService.getAll()); // dùng cho list

        return "sale-order/sale-order-list";
    }

    // ========== CREATE ==========
    @GetMapping("/create")
    public String showCreateForm(Model model) {
        //Tạo form mặc định: mã đơn sinh sẵn; trạng thái khởi tạo PENDING.
        SaleOrderRequestDTO form = SaleOrderRequestDTO.builder()
                .soCode(saleOrderService.generateNextCode())
                .status(SaleOrder.SaleOrderStatus.PENDING)
                .build();
//Xác định kho làm việc theo user đăng nhập để tra tồn cho đúng kho.
        Integer whId = stockLookupService.resolveWarehouseIdForCurrentUser();
        String whName = stockLookupService.resolveWarehouseNameForCurrentUser();
//Nhét tất cả dữ liệu cần cho form tạo đơn (khách, sp có tồn, combo, trạng thái, kho).
//Trả về view của form.
        model.addAttribute("saleOrderForm", form);
        model.addAttribute("customers", customerService.findAll());
        model.addAttribute("products", stockLookupService.buildProductListWithStock(whId));
        model.addAttribute("combos", comboService.getAllActive());
        model.addAttribute("orderStatuses", SaleOrder.SaleOrderStatus.values());
        model.addAttribute("warehouseName", whName);
        model.addAttribute("warehouseId", whId);

        return "sale-order/sale-order-form";
    }

    // API tiện ích: lấy mã tiếp theo cho UI
    //Endpoint JSON để UI gọi AJAX lấy mã đơn tiếp theo.
    //Trả về {"code":"ORD00001"} dạng Map.
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
    //Bind form vào dto, bật validation (Jakarta).
    //Lấy action để quyết định flow: SAVE (về list) hay SELL (sang trang xuất kho).
    //Dùng RedirectAttributes để set flash messages.
    public String createOrder(@Valid @ModelAttribute("saleOrderForm") SaleOrderRequestDTO dto,
                              BindingResult result,
                              @RequestParam(name = "action", defaultValue = "SAVE") String action,
                              Model model,
                              RedirectAttributes ra) {

//Bảo đảm dữ liệu tối thiểu nếu UI thiếu.
        if (dto.getSoCode() == null || dto.getSoCode().isBlank()) {
            dto.setSoCode(saleOrderService.generateNextCode());
        }
        if (dto.getStatus() == null) {
            dto.setStatus(SaleOrder.SaleOrderStatus.PENDING);
        }
//Nếu validation lỗi: render lại form + dữ liệu phụ trợ; không redirect để giữ lỗi hiển thị.
        if (result.hasErrors()) {
            Integer whId = stockLookupService.resolveWarehouseIdForCurrentUser();
            String whName = stockLookupService.resolveWarehouseNameForCurrentUser();
            model.addAttribute("saleOrderForm", dto);
            model.addAttribute("customers", customerService.findAll());
            model.addAttribute("products", stockLookupService.buildProductListWithStock(whId)); // tồn theo kho
            model.addAttribute("combos", comboService.getAllActive());
            model.addAttribute("orderStatuses", SaleOrder.SaleOrderStatus.values());
            model.addAttribute("warehouseName", whName);
            model.addAttribute("warehouseId", whId);
            return "sale-order/sale-order-form";
        }
//Lấy username từ SecurityContext.
//Gọi service tạo đơn (trong transaction), trả về DTO kết quả.
        try {
            final String currentUsername =
                    SecurityContextHolder.getContext().getAuthentication().getName();

            // Tạo đơn
            SaleOrderResponseDTO created = saleOrderService.createOrder(dto, currentUsername);
//Nhánh SELL: cần soId để chuyển sang trang “tạo phiếu xuất từ đơn”.
//Nếu mapper quên map soId → cảnh báo và quay list.
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
            //Nhánh SAVE: toast thành công, quay về list.
            ra.addFlashAttribute("success", "Tạo đơn hàng thành công. Mã: " + created.getOrderCode());
            return "redirect:/sale-orders";

        } catch (Exception ex) {
            //Bắt lỗi chung → hiển thị flash error → quay list.
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

        // .Chỉ cho sửa khi PENDING.Nếu không còn trạng thái PENDING thì hiển thị trang chi tiết readonly
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

        // 4) Xác định tồn theo kho & gán availableQuantity cho từng dòng manual
        Integer whId = stockLookupService.resolveWarehouseIdForCurrentUser();
        var stockByPid = stockLookupService.getStockByProductAtWarehouse(whId);
        manualDetails.forEach(md -> {
            md.setAvailableQuantity(stockByPid.getOrDefault(md.getProductId(), 0));
        });

        // 5) Chuẩn bị mảng products “nhẹ” theo tồn kho user (đưa ra view cho JS)
        var productsLite = stockLookupService.buildProductListWithStock(whId);

        // 6) Build form bind ra view (sau khi manualDetails đã có availableQuantity)
        var form = SaleOrderRequestDTO.builder()
                .customerId(orderEntity.getCustomer() != null ? orderEntity.getCustomer().getId() : null)
                .description(cleanedDescription)
                .details(manualDetails) // đã có availableQuantity
                .comboCounts(comboCounts)
                .build();

        // 7) Đổ dữ liệu cho view
        model.addAttribute("saleOrder", dto);
        model.addAttribute("saleOrderForm", form);
        model.addAttribute("customers", customerService.findAll());
        model.addAttribute("combos", cbRepo.findAll());
        model.addAttribute("prExists", prRepo.existsBySaleOrder_SoId(id));

        // dùng mảng “nhẹ” cho JS trang sửa
        model.addAttribute("productsLite", productsLite);

        // id để form action submit chính xác
        model.addAttribute("saleOrderId", id);

        return "sale-order/sale-order-edit";
    }

    // ====== SAVE ITEMS (PRG, không dùng @Valid để tránh re-render form) ======
    @PostMapping("/{id}/items/edit")
    public String updateOrderItems(@PathVariable Integer id,
                                   @ModelAttribute("saleOrderForm") SaleOrderRequestDTO form,
                                   RedirectAttributes ra) {
        try {
            //Đẩy toàn bộ logic cập nhật chi tiết sang service (atomic).
            saleOrderService.updateOrderItems(id, form);

            //  lấy mã đơn cho toast (fallback 3 tầng, tránh crash vì lazy).
            String code = null;
            try {
                code = saleOrderService.getOrderEntityById(id).getSoCode();
            } catch (Exception ignore) {
            }
            if (code == null) {
                try {
                    code = saleOrderService.getById(id).getOrderCode();
                } catch (Exception ignore) {
                }
            }
            if (code == null) code = "ORD#" + id;

            // Gửi flash attribute cho trang danh sách
            ra.addFlashAttribute("toastSuccess", "Đơn " + code + " đã được lưu thành công.");

            // Thành công → toast + quay list (PRG pattern).
            return "redirect:/sale-orders";
            //Lỗi → flash error → quay lại trang edit.
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/sale-orders/" + id + "/edit";
        }
    }


    @GetMapping("/{id}/view")
    //Nếu vẫn PENDING → chuyển về edit, vì view detail là readonly.
    public String viewOrderDetails(@PathVariable Integer id, Model model) {
        SaleOrder saleOrder = saleOrderService.getOrderEntityById(id);

        // ✅ Cho phép xem nếu KHÔNG phải PENDING
        if (saleOrder.getStatus() == SaleOrder.SaleOrderStatus.PENDING) {
            return "redirect:/sale-orders/" + id + "/edit";
        }

        model.addAttribute("saleOrder", saleOrder);

        // === NEW: tính ĐÃ GIAO / CÒN LẠI cho từng product ===
        // Query group-by để lấy tong đã xuất theo product id → map {pid -> issuedQuantity}.
        List<Object[]> rows = goodIssueRepository
                .sumIssuedBySaleOrderGroupByProduct(saleOrder.getSoId());

        // Map<productId, issuedQty>
        java.util.Map<Integer, Integer> issuedByPid = new java.util.HashMap<>();
        for (Object[] r : rows) {
            Integer pid = (Integer) r[0];
            Number sum = (Number) r[1];
            issuedByPid.put(pid, sum == null ? 0 : sum.intValue());
        }

        // Tính còn lại = ordered – issued (không âm).//Đưa 2 map ra view để hiển thị tiến độ giao.

        //Tạo một Map mới tên remainingByPid
        //(key: productId kiểu Integer, value: số lượng còn lại phải giao kiểu Integer).
        //Dùng new java.util.HashMap<>() đầy đủ tên gói để tránh nhầm lẫn import
        // (hoặc do file không có import Map, HashMap).
        java.util.Map<Integer, Integer> remainingByPid = new java.util.HashMap<>();

        //Lặp qua tất cả dòng chi tiết (SaleOrderDetail) của đơn hàng saleOrder.
        //var d để Java tự suy luận kiểu (ở đây là SaleOrderDetail).
        for (var d : saleOrder.getDetails()) {
            //Lấy productId (pid) của dòng:
            //Nếu d.getProduct() khác null ⇒ lấy id.
            //Nếu d.getProduct() là null ⇒ pid = null.
            //Lý do phòng thủ: tránh NullPointerException nếu vì lý do nào đó dòng chưa gắn product.
            Integer pid = d.getProduct() != null ? d.getProduct().getId() : null;
            //Nếu không xác định được productId thì bỏ qua dòng này (không thể tính issued/remaining theo pid).
            if (pid == null) continue;
            //Lấy số lượng đã đặt (ordered) cho dòng:
            //Nếu orderedQuantity có giá trị ⇒ dùng nó.
            //Nếu null ⇒ mặc định 0 (an toàn tính toán).
            int ordered = d.getOrderedQuantity() != null ? d.getOrderedQuantity() : 0;
            //Lấy tổng số lượng đã xuất (issued) cho sản phẩm này từ map issuedByPid
            // (đã tính trước bằng query group-by).
            //Nếu map không có key pid ⇒ coi như đã xuất 0.
            int issued = issuedByPid.getOrDefault(pid, 0);
            //Tính số còn lại phải giao = ordered - issued, nhưng không cho âm.
            //Dùng Math.max(0, …) để:
            //Nếu lỡ dữ liệu issued > ordered (do ghép đơn, điều chỉnh…) thì vẫn chặn âm về 0 cho UI.
            int remain = Math.max(0, ordered - issued);
            //Ghi kết quả vào map remainingByPid theo productId.
            remainingByPid.put(pid, remain);
        }

        model.addAttribute("issuedByPid", issuedByPid);
        model.addAttribute("remainingByPid", remainingByPid);

        return "sale-order/sale-order-detail";
    }


    // ====== DELETE (chỉ cho PENDING) ======
    @PostMapping("/{id}/delete")
    public String deleteOrder(@PathVariable Integer id,
                              RedirectAttributes ra,
                              Model model) {
        try {
            SaleOrderResponseDTO dto = saleOrderService.getById(id);
            saleOrderService.deleteIfPending(id);
            ra.addFlashAttribute("toastSuccess", "Đã xoá đơn hàng " + dto.getOrderCode() + " thành công.");
        } catch (Exception ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/sale-orders";
    }
}
