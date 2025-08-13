package com.eewms.utils;

import com.eewms.dto.ComboDTO;
import com.eewms.dto.ComboDetailDTO;
import com.eewms.services.IComboService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@RequiredArgsConstructor
public class ComboJsonHelper {

    private final IComboService comboService;
    private final ObjectMapper objectMapper;

    /** Trả JSON gộp:
     * {
     *   "items":  { "1": [ { "productId": 101, "quantity": 2 }, ... ], ... },
     *   "labels": { "1": "CB001 - Gói A", "5": "CB009 - Bộ phụ kiện", ... }
     * }
     * Dùng ComboDTO để tránh lazy.
     */
    public String buildCombosPayloadJson() {
        try {
            List<ComboDTO> combos = comboService.getAllActive();

            Map<String, Object> payload = new LinkedHashMap<>();
            Map<String, List<Map<String, Object>>> items = new LinkedHashMap<>();
            Map<String, String> labels = new LinkedHashMap<>();

            for (ComboDTO c : combos) {
                String key = String.valueOf(c.getId());
                labels.put(key, (c.getCode() != null && !c.getCode().isBlank())
                        ? (c.getCode() + " - " + c.getName())
                        : c.getName());

                List<Map<String, Object>> arr = new ArrayList<>();
                if (c.getDetails() != null) {
                    for (ComboDetailDTO d : c.getDetails()) {
                        Map<String, Object> m = new HashMap<>();
                        m.put("productId", d.getProductId());   // Integer
                        m.put("quantity", d.getQuantity());     // Integer
                        // Nếu combo có giá item riêng, thêm: m.put("itemPrice", d.getPrice());
                        arr.add(m);
                    }
                }
                items.put(key, arr);
            }
            payload.put("items", items);
            payload.put("labels", labels);
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            return "{\"items\":{},\"labels\":{}}";
        }
    }
}
