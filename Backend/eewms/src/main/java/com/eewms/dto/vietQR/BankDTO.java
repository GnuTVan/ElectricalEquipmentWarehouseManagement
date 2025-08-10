package com.eewms.dto.vietQR;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BankDTO {
    String code, name, shortName, bin, logoUrl;
}
