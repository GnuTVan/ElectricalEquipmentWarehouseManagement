package com.eewms.utils;

import com.eewms.constant.ItemOrigin;
import com.eewms.entities.Combo;
import com.eewms.entities.ComboDetail;
import com.eewms.entities.Product;
import com.eewms.entities.SaleOrderDetail;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Gom nhiều combo (có thể trùng) → gộp thành map productId -> SaleOrderDetail (origin = COMBO).
 * - SL cộng dồn nếu 1 sản phẩm xuất hiện ở nhiều combo/lần chọn.
 * - Giá: luôn lấy từ Product.listingPrice (bỏ qua ComboDetail.price), mặc định 0 nếu null.
 * - KHÔNG set sale_order — service set khi lưu.
 */
public final class ComboUtils {

    private ComboUtils() {}

    public static Map<Integer, SaleOrderDetail> expandFromEntities(List<Combo> combos) {
        Map<Integer, SaleOrderDetail> acc = new LinkedHashMap<>();
        if (combos == null || combos.isEmpty()) return acc;

        for (Combo combo : combos) {
            if (combo == null || combo.getDetails() == null) continue;

            for (ComboDetail cd : combo.getDetails()) {
                if (cd == null) continue;
                Product p = cd.getProduct();
                if (p == null || p.getId() == null) continue;

                final int pid = p.getId();
                final int qty = Optional.ofNullable(cd.getQuantity()).orElse(0);

                // ✅ LUÔN lấy giá theo listingPrice của Product (bỏ qua ComboDetail.price)
                final BigDecimal price = Optional.ofNullable(p.getListingPrice()).orElse(BigDecimal.ZERO);

                SaleOrderDetail line = acc.get(pid);
                if (line == null) {
                    line = new SaleOrderDetail();
                    line.setProduct(p);
                    line.setOrderedQuantity(qty);
                    line.setPrice(price);
                    line.setOrigin(ItemOrigin.COMBO);  // đánh dấu nguồn
                    acc.put(pid, line);
                } else {
                    line.setOrderedQuantity(line.getOrderedQuantity() + qty);
                    // giữ nguyên price lần đầu (đều là listingPrice nên ok)
                }
            }
        }
        return acc;
    }
}
