package com.eewms.services.impl;

import com.eewms.dto.ComboDTO;
import com.eewms.dto.ComboDetailDTO;
import com.eewms.dto.ComboMapper;
import com.eewms.dto.ComboRequest;
import com.eewms.entities.Combo;
import com.eewms.entities.ComboDetail;
import com.eewms.entities.Product;
import com.eewms.repository.ComboRepository;
import com.eewms.repository.ProductRepository;
import com.eewms.services.IComboService;
import com.eewms.utils.NameUtils;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ComboServiceImpl implements IComboService {

    private final ComboRepository comboRepository;
    private final ProductRepository productRepository;

    @Override
    @Transactional
    public ComboDTO create(ComboRequest req) {
        String code = (req.getCode() == null || req.getCode().isBlank())
                ? generateNextCode()
                : NameUtils.normalizeCode(req.getCode());
        String name = NameUtils.normalizeName(req.getName());

        if (comboRepository.existsByCodeIgnoreCase(code)) {
            throw new IllegalArgumentException("Mã combo đã tồn tại: " + code);
        }
        // === THÊM: check tên ===
        if (comboRepository.existsByNameIgnoreCase(name)) {
            throw new IllegalArgumentException("Tên combo đã tồn tại: " + name);
        }

        Combo combo = upsertToEntity(new Combo(), req, code, name);
        return ComboMapper.toDTO(comboRepository.save(combo));
    }

    @Override
    @Transactional
    public ComboDTO update(Long id, @Valid ComboRequest req) {
        Combo combo = comboRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy combo: " + id));

        // code
        String code;
        if (req.getCode() == null || req.getCode().isBlank()) {
            code = combo.getCode();
        } else {
            code = NameUtils.normalizeCode(req.getCode());
            if (!combo.getCode().equalsIgnoreCase(code)
                    && comboRepository.existsByCodeIgnoreCaseAndIdNot(code, id)) {
                throw new IllegalArgumentException("Mã combo đã tồn tại: " + code);
            }
        }

        // name
        String name = NameUtils.normalizeName(req.getName());
        if (!combo.getName().equalsIgnoreCase(name)
                && comboRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new IllegalArgumentException("Tên combo đã tồn tại: " + name);
        }

        Combo updated = upsertToEntity(combo, req, code, name);
        return ComboMapper.toDTO(comboRepository.save(updated));
    }

    private Combo upsertToEntity(Combo combo, ComboRequest req, String code, String name) {
        final int MAX_QTY = 999;

        // --- Gộp số lượng theo productId từ request + validate 1..999 ---
        Map<Integer, Integer> merged = new LinkedHashMap<>();
        if (req.getDetails() != null) {
            for (ComboRequest.Item it : req.getDetails()) {
                if (it == null || it.getProductId() == null) continue;

                Integer qObj = it.getQuantity();
                int q = (qObj == null) ? 0 : qObj;

                if (q < 1 || q > MAX_QTY) {
                    throw new IllegalArgumentException(
                            "Số lượng phải từ 1 đến " + MAX_QTY + " cho productId=" + it.getProductId());
                }
                merged.merge(it.getProductId(), q, Integer::sum);
            }
        }
        if (merged.isEmpty()) {
            throw new IllegalArgumentException("Combo phải có ít nhất 1 sản phẩm");
        }

        // Sau khi gộp, đảm bảo tổng cho mỗi product không vượt MAX_QTY
        for (var e : merged.entrySet()) {
            if (e.getValue() > MAX_QTY) {
                throw new IllegalArgumentException(
                        "Tổng số lượng cho productId=" + e.getKey() + " vượt quá " + MAX_QTY);
            }
        }

        // --- Load products & validate tồn tại ---
        List<Integer> pids = new ArrayList<>(merged.keySet());
        List<Product> products = productRepository.findAllById(pids);
        if (products.size() != pids.size()) {
            throw new IllegalArgumentException("Có productId không tồn tại trong hệ thống");
        }
        Map<Integer, Product> byId = products.stream()
                .collect(Collectors.toMap(Product::getId, p -> p));

        // --- Build details (chỉ lấy product ACTIVE) ---
        List<ComboDetail> details = new ArrayList<>();
        for (var e : merged.entrySet()) {
            Product p = byId.get(e.getKey());
            if (p == null || p.getStatus() != Product.ProductStatus.ACTIVE) continue;

            ComboDetail d = new ComboDetail();
            d.setCombo(combo);
            d.setProduct(p);
            d.setQuantity(e.getValue());
            details.add(d);
        }
        if (details.isEmpty()) {
            throw new IllegalArgumentException("Tất cả sản phẩm trong combo đều không hợp lệ/không ACTIVE");
        }

        // --- Set fields chính ---
        combo.setCode(code);
        combo.setName(name);
        combo.setDescription(req.getDescription());
        combo.setStatus(req.getStatus() == null ? Combo.ComboStatus.ACTIVE : req.getStatus());

        // --- Replace children an toàn ---
        if (combo.getId() != null && combo.getDetails() != null) {
            combo.getDetails().clear();
            combo.getDetails().addAll(details);
        } else {
            combo.setDetails(details);
        }
        return combo;
    }


    @Override
    public ComboDTO getById(Long id) {
        Combo c = comboRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy combo: " + id));
        return ComboMapper.toDTO(c);
    }

    @Override
    public List<ComboDTO> getAll() {
        return comboRepository.findAll().stream()
                .map(ComboMapper::toDTO)
                .toList();
    }

    @Override
    public List<ComboDTO> searchByKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) return getAll();
        return comboRepository
                .findByNameContainingIgnoreCaseOrCodeContainingIgnoreCase(keyword, keyword)
                .stream().map(ComboMapper::toDTO).toList();
    }

    @Override
    @Transactional
    public void updateStatus(Long id, Combo.ComboStatus status) {
        Combo c = comboRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy combo: " + id));
        c.setStatus(status);
        comboRepository.save(c);
    }
    @Override
    public List<ComboDTO> getAllActive() {
        return comboRepository.findByStatus(Combo.ComboStatus.ACTIVE)
                .stream().map(ComboMapper::toDTO).toList();
    }
    @Override
    public List<ComboDetailDTO> expandAsComboDetailDTO(List<Long> comboIds) {
        if (comboIds == null || comboIds.isEmpty()) return List.of();

        // 1) Đếm số lần cho từng comboId (giữ duplicates)
        Map<Long, Integer> timesByComboId = new LinkedHashMap<>();
        for (Long id : comboIds) {
            timesByComboId.merge(id, 1, Integer::sum);
        }

        // 2) Chỉ fetch theo tập unique id
        List<Combo> combos = comboRepository.findAllById(timesByComboId.keySet()).stream()
                .filter(c -> c.getStatus() == Combo.ComboStatus.ACTIVE)
                .toList();

        // 3) Gộp theo productId, nhưng quantity = d.quantity * số lần chọn combo đó
        Map<Integer, ComboDetailDTO> acc = new LinkedHashMap<>();

        for (Combo c : combos) {
            int times = timesByComboId.getOrDefault(c.getId(), 1);
            if (c.getDetails() == null) continue;

            for (ComboDetail d : c.getDetails()) {
                Product p = d.getProduct();
                if (p == null) continue;
                if (p.getStatus() != Product.ProductStatus.ACTIVE) continue;

                int pid = p.getId();
                int addQty = (d.getQuantity() == null ? 0 : d.getQuantity()) * times;

                ComboDetailDTO cur = acc.get(pid);
                if (cur == null) {
                    cur = ComboDetailDTO.builder()
                            .comboId(c.getId()) // (không còn nhiều ý nghĩa vì đã gộp theo sản phẩm)
                            .productId(pid)
                            .productName(p.getName())
                            .quantity(addQty)
                            .price(p.getListingPrice())            // BigDecimal
                            .availableQuantity(p.getQuantity())    // tồn kho hiện tại
                            .build();
                    acc.put(pid, cur);
                } else {
                    cur.setQuantity(cur.getQuantity() + addQty);
                }
            }
        }
        return new ArrayList<>(acc.values());
    }

    /**
     * Sinh mã code mới tự động dạng CB001, CB002...
     */
    private String generateNextCode() {
        String prefix = "CB";
        String maxCode = comboRepository.findMaxCodeLike(prefix + "%");
        int nextNumber = 1;
        if (maxCode != null) {
            try { nextNumber = Integer.parseInt(maxCode.replace(prefix, "")) + 1; }
            catch (NumberFormatException ignored) {}
        }
        // Lặp đến khi không đụng unique
        String code;
        do {
            code = String.format("%s%03d", prefix, nextNumber++);
        } while (comboRepository.existsByCodeIgnoreCase(code));
        return code;
    }


    private String normalizeCode(String code) {
        return code == null ? "" : code.trim().toUpperCase();
    }
    private String normalizeName(String name) {
        return name == null ? "" : name.trim();
    }
}