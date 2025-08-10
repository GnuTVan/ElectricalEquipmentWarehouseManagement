package com.eewms.controller;

import com.eewms.dto.tax.TaxLookupResponse;
import com.eewms.services.ITaxLookupService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ApiPublicController {

    private final ITaxLookupService taxLookupService;

    @GetMapping("/tax-lookup/{taxCode}")
    public ResponseEntity<TaxLookupResponse> lookup(@PathVariable String taxCode) {
        return ResponseEntity.ok(taxLookupService.lookupByTaxCode(taxCode));
    }
}
