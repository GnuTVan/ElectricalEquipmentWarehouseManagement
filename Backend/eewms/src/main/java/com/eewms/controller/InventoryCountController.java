package com.eewms.controller;

import com.eewms.dto.inventory.InventoryCountDTO;
import com.eewms.entities.User;
import com.eewms.entities.Warehouse;
import com.eewms.entities.WarehouseStaff;
import com.eewms.repository.UserRepository;
import com.eewms.repository.WarehouseRepository;
import com.eewms.services.IInventoryCountService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/inventory/count")
@RequiredArgsConstructor
public class InventoryCountController {

    private final IInventoryCountService inventoryCountService;
    private final UserRepository userRepository;
    private final WarehouseRepository warehouseRepository;

    // LIST: danh sách phiếu
    @GetMapping
    public String list(Model model,
                       @RequestParam(required = false) Integer warehouseId,
                       @RequestParam(required = false) String status,
                       @RequestParam(required = false) Integer staffId,
                       @RequestParam(required = false) String keyword,
                       Authentication authentication) {

        // Lấy role
        boolean isManager = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_MANAGER"));

        // Lấy user hiện tại
        String username = authentication.getName();
        User currentUser = userRepository.findByUsername(username).orElseThrow();

        List<InventoryCountDTO> counts;
        if (isManager) {
            counts = inventoryCountService.getAll();

            // Tìm kho mà manager này quản lý
            List<Warehouse> warehouses = warehouseRepository.findBySupervisor_Id(currentUser.getId());
            Warehouse wh = warehouses.isEmpty() ? null : warehouses.get(0);
            if (wh != null) {
                model.addAttribute("warehouseId", wh.getId());
            }
        } else {
            counts = inventoryCountService.getByStaffId(currentUser.getId());
        }

        model.addAttribute("counts", counts);
        return "inventory/inventory-order-list";
    }


    @GetMapping("/{id}")
    public String counting(@PathVariable("id") Integer id, Model model, Authentication authentication) {
        InventoryCountDTO dto = inventoryCountService.getById(id);

        boolean isManager = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_MANAGER"));

        String username = authentication.getName();
        User currentUser = userRepository.findByUsername(username).orElseThrow();

        if (!isManager && !dto.getStaffId().equals(currentUser.getId())) {
            return "redirect:/inventory/count";
        }

        model.addAttribute("count", dto);

        if (!isManager && dto.getStatus().name().equals("IN_PROGRESS")) {
            return "inventory/inventory-counting";
        }

        return "inventory/inventory-review";
    }


    // REVIEW (manager xem + duyệt)
    @GetMapping("/{id}/review")
    public String review(@PathVariable("id") Integer id, Model model) {
        InventoryCountDTO dto = inventoryCountService.getById(id);
        model.addAttribute("count", dto);
        return "inventory/inventory-review";
    }

    // CREATE form
    @GetMapping("/create")
    public String createForm(@RequestParam("warehouseId") Integer warehouseId,
                             Model model,
                             Authentication auth) {
        // Lấy user hiện tại
        User current = userRepository.findByUsername(auth.getName()).orElseThrow();

        // Lọc staff theo warehouseId
        List<User> staffList = userRepository.findStaffByWarehouseId(warehouseId);

        model.addAttribute("warehouseId", warehouseId);
        model.addAttribute("staffList", staffList);
        return "inventory/inventory-count-create";
    }

    // HANDLE CREATE (POST)
    @PostMapping("/create")
    public String createSubmit(@RequestParam("warehouseId") Integer warehouseId,
                               @RequestParam("staffId") Integer staffId,
                               @RequestParam(value="note", required=false) String note,
                               Authentication auth,
                               RedirectAttributes ra) {
        try {
            User current = userRepository.findByUsername(auth.getName()).orElseThrow();
            InventoryCountDTO dto = inventoryCountService.create(warehouseId, staffId, note, current);
            ra.addFlashAttribute("message","Tạo phiếu thành công: " + dto.getCode());
            return "redirect:/inventory/count";
        } catch (Exception e) {
            ra.addFlashAttribute("error","Lỗi: " + e.getMessage());
            return "redirect:/inventory/count/create?warehouseId=" + warehouseId;
        }
    }

    // SAVE DRAFT (staff bấm Lưu nháp)
    @PostMapping("/{id}/draft")
    public String saveDraft(@PathVariable("id") Integer id,
                            @RequestParam("countedQtys") List<Integer> countedQtys,
                            @RequestParam("notes") List<String> notes) {
        inventoryCountService.saveDraft(id, countedQtys, notes);
        return "redirect:/inventory/count/" + id;
    }

    // SUBMIT (staff bấm Nộp kết quả)
    @PostMapping("/{id}/submit")
    public String submit(@PathVariable("id") Integer id,
                         @RequestParam("countedQtys") List<Integer> countedQtys,
                         @RequestParam("notes") List<String> notes) {
        inventoryCountService.submit(id, countedQtys, notes);
        return "redirect:/inventory/count";
    }

    // APPROVE (manager duyệt phiếu)
    @PostMapping("/{id}/approve")
    public String approve(@PathVariable("id") Integer id,
                          @RequestParam("approveNote") String approveNote) {
        inventoryCountService.approve(id, approveNote);
        return "redirect:/inventory/count";
    }
    @PostMapping("/{id}/reopen")
    public String reopen(@PathVariable("id") Integer id,
                         @RequestParam(value = "approveNote", required = false) String approveNote) {
        inventoryCountService.reopen(id, approveNote);
        return "redirect:/inventory/count";
    }
    // DELETE phiếu
    @PostMapping("/{id}/delete")
    public String delete(@PathVariable("id") Integer id) {
        inventoryCountService.delete(id);
        return "redirect:/inventory/count";
    }
}
