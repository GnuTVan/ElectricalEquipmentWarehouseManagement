package com.eewms.exception;

public class RegexPattern {

    /** Tên: chỉ cho phép chữ cái, khoảng trắng, dấu nháy (O'Connor), có dấu tiếng Việt */
    public static final String NAME = "^[\\p{L}\\s']+$";

    /** Số điện thoại Việt Nam: bắt đầu bằng 0, theo sau là 9 hoặc 10 số */
    public static final String PHONE = "^0\\d{9,10}$";

    /** Email chuẩn (nên dùng @Email nhưng để phòng mở rộng) */
    public static final String EMAIL = "^[\\w.%+-]+@[\\w.-]+\\.[A-Za-z]{2,6}$";

    /** Mã số thuế: đúng 10 hoặc 13 chữ số */
    public static final String TAX_CODE = "^\\d{10}(\\d{3})?$";

    /** Tên ngân hàng: chữ + số + khoảng trắng, không ký tự đặc biệt */
    public static final String BANK_NAME = "^[\\p{L}\\d\\s]+$";

}