package com.eewms.services;

import com.eewms.dto.VietQR.BankDTO;
import com.eewms.dto.VietQR.TaxLookupResponse;

import java.util.List;

public interface IVietqrService {
    // Tìm kiếm thông tin doanh nghiệp theo mã số thuế
    TaxLookupResponse lookupByTaxCode(String taxCode);

    //
    List<BankDTO> getBanks();                 // NEW
    List<BankDTO> getBanks(boolean force);
}
