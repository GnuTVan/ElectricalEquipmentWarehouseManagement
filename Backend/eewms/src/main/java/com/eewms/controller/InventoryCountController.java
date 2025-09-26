package com.eewms.controller;

import com.eewms.dto.inventory.InventoryCountDTO;
import com.eewms.entities.User;
import com.eewms.repository.UserRepository;
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

        // Lấy userId từ DB theo username
        String username = authentication.getName();
        User currentUser = userRepository.findByUsername(username).orElseThrow();

        List<InventoryCountDTO> counts;
        if (isManager) {
            counts = inventoryCountService.getAll();
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
    public String createForm(Model model) {
        // Lấy toàn bộ staff trong hệ thống để chọn
        List<User> staffList = userRepository.findAll();
        model.addAttribute("staffList", staffList);
        return "inventory/inventory-count-create";
    }

    // HANDLE CREATE (POST)
    @PostMapping("/create")
    public String createSubmit(@RequestParam("staffId") Integer staffId,
                               @RequestParam(value = "note", required = false) String note,
                               RedirectAttributes redirectAttributes) {
        try {
            InventoryCountDTO dto = inventoryCountService.create(staffId, note);
            redirectAttributes.addFlashAttribute("message",
                    "Tạo phiếu kiểm kê thành công: " + dto.getCode());
            return "redirect:/inventory/count";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Lỗi: " + e.getMessage());
            return "redirect:/inventory/count/create";
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
