package com.eewms.utils;

public class NameUtils {

    /**
     * Chuẩn hóa code:
     * - Trim khoảng trắng
     * - Viết hoa toàn bộ
     */
    public static String normalizeCode(String code) {
        return code == null ? "" : code.trim().toUpperCase();
    }

    /**
     * Chuẩn hóa tên:
     * - Loại ký tự đặc biệt (chỉ giữ chữ, số, khoảng trắng)
     * - Gộp khoảng trắng thừa
     * - Viết hoa chữ cái đầu mỗi từ
     */
    public static String normalizeName(String name) {
        if (name == null) return "";

        // Thêm khoảng trắng trước chữ hoa nếu trước đó là chữ thường hoặc số
        // Ví dụ: "Brandingnewlife" -> "Brandingnewlife" (chưa tách)
        // "BrandingNewlife" -> "Branding Newlife"
        name = name.replaceAll("([\\p{Ll}\\p{N}])([\\p{Lu}])", "$1 $2");

        // Bỏ khoảng trắng đầu/cuối và gộp khoảng trắng liên tiếp
        name = name.trim().replaceAll("\\s+", " ");

        // Chỉ giữ lại chữ cái, số, khoảng trắng
        name = name.replaceAll("[^\\p{L}\\p{N} ]", "");

        // Gộp lại khoảng trắng lần nữa
        name = name.trim().replaceAll("\\s+", " ");

        // Viết hoa chữ cái đầu mỗi từ
        String[] words = name.toLowerCase().split(" ");
        StringBuilder formatted = new StringBuilder();
        for (String w : words) {
            if (!w.isEmpty()) {
                formatted.append(Character.toUpperCase(w.charAt(0)))
                        .append(w.substring(1))
                        .append(" ");
            }
        }
        return formatted.toString().trim();
    }
}
