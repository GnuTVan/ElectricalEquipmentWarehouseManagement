package com.eewms.controller;

import com.eewms.dto.ProductLiteDTO;
import com.eewms.dto.inventory.StockFlatDTO;
import com.eewms.entities.InventoryTransfer;
import com.eewms.entities.InventoryTransferItem;
import com.eewms.entities.User;
import com.eewms.entities.Warehouse;
import com.eewms.repository.ProductRepository;
import com.eewms.repository.ProductWarehouseStockRepository;
import com.eewms.repository.WarehouseRepository;
import com.eewms.services.IInventoryTransferService;
import com.eewms.services.IUserService;
import com.eewms.services.IWarehouseService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Controller quản lý phiếu chuyển kho.
 *
 * Quy tắc phân quyền (tham chiếu transferPermission):
 * - STAFF kho NGUỒN   -> được EXPORT.
 * - STAFF kho ĐÍCH   -> được IMPORT.
 * - MANAGER kho NGUỒN -> được duyệt FROM (approve-from).
 * - MANAGER kho ĐÍCH -> được duyệt TO (approve-to).
 */
@Controller
@RequestMapping("/inventory-transfers")
@RequiredArgsConstructor
public class InventoryTransferController {

    private final IInventoryTransferService transferService;
    private final WarehouseRepository warehouseRepository;
    private final IUserService userService;
    private final IWarehouseService warehouseService;
    private final ProductRepository productRepository;
    private final ProductWarehouseStockRepository pwsRepository;

    // ------------------ Common dropdowns ------------------

    /** Preload danh sách tất cả kho cho form/search */
    @ModelAttribute("allWarehouses")
    public List<Warehouse> allWarehouses() {
        return warehouseRepository.findAll();
    }

    /** Preload enum status để render filter */
    @ModelAttribute("allStatuses")
    public InventoryTransfer.Status[] allStatuses() {
        return InventoryTransfer.Status.values();
    }

    // ------------------ List page ------------------

    @GetMapping
    @PreAuthorize("hasAnyRole('STAFF','MANAGER')")
    public String list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) InventoryTransfer.Status status,
            @RequestParam(required = false) Integer fromWarehouseId,
            @RequestParam(required = false) Integer toWarehouseId,
            @RequestParam(required = false) LocalDateTime fromDate,
            @RequestParam(required = false) LocalDateTime toDate,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable,
            Model model
    ) {
        Page<InventoryTransfer> page = transferService.search(
                keyword, status, fromDate, toDate, fromWarehouseId, toWarehouseId, pageable
        );
        model.addAttribute("page", page);
        model.addAttribute("keyword", keyword);
        model.addAttribute("statusFilter", status);
        model.addAttribute("fromWarehouseId", fromWarehouseId);
        model.addAttribute("toWarehouseId", toWarehouseId);
        model.addAttribute("fromDate", fromDate);
        model.addAttribute("toDate", toDate);
        return "inventory-transfer/transfer-list";
    }

    // ------------------ New form ------------------

    @GetMapping("/new")
    @PreAuthorize("hasAnyRole('STAFF','MANAGER')")
    public String createForm(Model model) {
        User currentUser = userService.getCurrentUser();

        // Gợi ý kho đích mặc định (kho mà user đang là staff/manager)
        Long wid = warehouseService.findPrimaryWarehouseIdByUser(currentUser.getId());
        Warehouse toWarehouse = (wid != null) ? warehouseService.getById(wid.intValue()) : null;

        // Tạo phiếu nháp ban đầu
        InventoryTransfer form = InventoryTransfer.builder()
                .status(InventoryTransfer.Status.DRAFT)
                .createdBy(currentUser)
                .toWarehouse(toWarehouse)
                .build();
        form.getItems().add(InventoryTransferItem.builder().build()); // add 1 dòng trắng

        // preload danh mục SP & tồn kho
        List<ProductLiteDTO> productsLite = productRepository.findAllLite();
        List<StockFlatDTO> stocksFlat = pwsRepository.findAllFlat();

        model.addAttribute("transfer", form);
        model.addAttribute("toWarehouseFixed", toWarehouse);     // hiển thị readonly
        model.addAttribute("fromWarehouses", warehouseService.getAll());
        model.addAttribute("productsLite", productsLite);
        model.addAttribute("stocksFlat", stocksFlat);

        return "inventory-transfer/transfer-form";
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('STAFF','MANAGER')")
    public String saveDraft(@ModelAttribute("transfer") @Valid InventoryTransfer transfer,
                            BindingResult binding, Model model, RedirectAttributes ra) {

        User currentUser = userService.getCurrentUser();
        transfer.setCreatedBy(currentUser);

        // Nếu chưa set status -> mặc định nháp
        if (transfer.getStatus() == null) {
            transfer.setStatus(InventoryTransfer.Status.DRAFT);
        }

        // Validate danh sách item: bỏ rỗng, load lại Product đầy đủ, vá đơn vị đo
        if (transfer.getItems() != null) {
            transfer.getItems().removeIf(it ->
                    it == null
                            || it.getProduct() == null
                            || it.getProduct().getId() == null
                            || it.getQuantity() == null
                            || it.getQuantity().doubleValue() <= 0
            );
            for (InventoryTransferItem it : transfer.getItems()) {
                Integer pid = it.getProduct().getId().intValue();
                var p = productRepository.findById(pid)
                        .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy sản phẩm id=" + pid));
                it.setProduct(p);
                if (it.getUnitName() == null || it.getUnitName().isBlank()) {
                    it.setUnitName(p.getUnit().getName());
                }
            }
        }

        // Nếu bind lỗi -> quay lại form với preload
        if (binding.hasErrors()) {
            model.addAttribute("fromWarehouses", warehouseService.getAll());
            model.addAttribute("productsLite", productRepository.findAllLite());
            model.addAttribute("stocksFlat", pwsRepository.findAllFlat());
            model.addAttribute("toWarehouseFixed",
                    (transfer.getToWarehouse() != null && transfer.getToWarehouse().getId() != null)
                            ? warehouseService.getById(transfer.getToWarehouse().getId())
                            : null);
            return "inventory-transfer/transfer-form";
        }

        // Lưu
        var saved = transferService.createDraft(transfer);
        ra.addFlashAttribute("toastr_success", "Đã lưu phiếu nháp: " + saved.getCode());
        return "redirect:/inventory-transfers/" + saved.getId();
    }

    // ------------------ Detail page ------------------

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('STAFF','MANAGER')")
    public String detail(@PathVariable Long id, Model model, RedirectAttributes ra) {
        try {
            InventoryTransfer tr = transferService.get(id);
            model.addAttribute("transfer", tr);
            return "inventory-transfer/transfer-detail";
        } catch (EntityNotFoundException ex) {
            ra.addFlashAttribute("toastr_error", "Không tìm thấy phiếu.");
            return "redirect:/inventory-transfers";
        }
    }

    // ------------------ Actions ------------------

    /** MANAGER của kho NGUỒN duyệt */
    @PostMapping("/{id}/approve-from")
    @PreAuthorize("@transferPermission.canApproveFrom(#id)")
    public String approveFrom(@PathVariable Long id, RedirectAttributes ra) {
        try {
            Integer uid = userService.getCurrentUser().getId().intValue();
            var tr = transferService.approveFrom(id, uid);
            ra.addFlashAttribute("toastr_success", "Kho nguồn đã duyệt. Trạng thái: " + tr.getStatus().getLabel());
        } catch (Exception e) {
            ra.addFlashAttribute("toastr_error", e.getMessage());
        }
        return "redirect:/inventory-transfers/" + id;
    }

    /** MANAGER của kho ĐÍCH duyệt */
    @PostMapping("/{id}/approve-to")
    @PreAuthorize("@transferPermission.canApproveTo(#id)")
    public String approveTo(@PathVariable Long id, RedirectAttributes ra) {
        try {
            Integer uid = userService.getCurrentUser().getId().intValue();
            var tr = transferService.approveTo(id, uid);
            ra.addFlashAttribute("toastr_success", "Kho đích đã duyệt. Trạng thái: " + tr.getStatus().getLabel());
        } catch (Exception e) {
            ra.addFlashAttribute("toastr_error", e.getMessage());
        }
        return "redirect:/inventory-transfers/" + id;
    }

    /** STAFF của kho NGUỒN được export */
    @PostMapping("/{id}/export")
    @PreAuthorize("@transferPermission.canExport(#id)")
    public String export(@PathVariable Long id, RedirectAttributes ra) {
        try {
            Integer uid = userService.getCurrentUser().getId().intValue();
            var tr = transferService.export(id, uid);
            ra.addFlashAttribute("toastr_success", "Đã xuất kho nguồn. Trạng thái: " + tr.getStatus().getLabel());
        } catch (Exception e) {
            ra.addFlashAttribute("toastr_error", e.getMessage());
        }
        return "redirect:/inventory-transfers/" + id;
    }

    /** STAFF của kho ĐÍCH được import */
    @PostMapping("/{id}/import")
    @PreAuthorize("@transferPermission.canImport(#id)")
    public String importTo(@PathVariable Long id, RedirectAttributes ra) {
        try {
            Integer uid = userService.getCurrentUser().getId().intValue();
            var tr = transferService.importTo(id, uid);
            ra.addFlashAttribute("toastr_success", "Đã nhập kho đích. Trạng thái: " + tr.getStatus().getLabel());
        } catch (Exception e) {
            ra.addFlashAttribute("toastr_error", e.getMessage());
        }
        return "redirect:/inventory-transfers/" + id;
    }

    /** Huỷ phiếu: mở tuỳ chính sách, tạm để MANAGER/STAFF nào cũng cancel */
    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('STAFF','MANAGER')")
    public String cancel(@PathVariable Long id, RedirectAttributes ra) {
        try {
            transferService.cancel(id, null);
            ra.addFlashAttribute("toastr_success", "Đã hủy phiếu.");
        } catch (Exception e) {
            ra.addFlashAttribute("toastr_error", e.getMessage());
        }
        return "redirect:/inventory-transfers";
    }
}
