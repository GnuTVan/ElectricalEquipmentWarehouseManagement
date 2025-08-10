package com.eewms.services;

import com.eewms.dto.tax.TaxLookupResponse;

public interface ITaxLookupService {
    TaxLookupResponse lookupByTaxCode(String taxCode);
}
