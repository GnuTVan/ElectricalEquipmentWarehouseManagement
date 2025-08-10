package com.eewms.dto.VietQR;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class VietqrBankResponse {
    private int code;
    private String desc;
    private List<VietqrBankItem> data;
}
