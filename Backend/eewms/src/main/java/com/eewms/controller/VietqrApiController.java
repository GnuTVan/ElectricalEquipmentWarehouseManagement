package com.eewms.controller;

import com.eewms.dto.vietQR.BankDTO;
import com.eewms.dto.vietQR.TaxLookupResponse;
import com.eewms.services.IVietqrService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.text.Normalizer;
import java.util.Locale;

import java.util.List;


@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class VietqrApiController {

    private final IVietqrService vietqrService;

    @GetMapping("/tax-lookup/{taxCode}")
    public ResponseEntity<TaxLookupResponse> lookup(@PathVariable String taxCode) {
        return ResponseEntity.ok(vietqrService.lookupByTaxCode(taxCode));
    }

    private static String norm(String s){
        if (s == null) return "";
        String n = Normalizer.normalize(s, Normalizer.Form.NFD);
        n = n.replaceAll("\\p{M}+", ""); // bỏ dấu
        return n.toLowerCase(Locale.ROOT).trim();
    }

    @GetMapping("/banks")
    public ResponseEntity<List<BankDTO>> banks(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "20") int limit) {

        String k = norm(q);
        int safeLimit = Math.max(1, Math.min(limit, 200));

        List<BankDTO> data = vietqrService.getBanks().stream()
                .filter(b -> k.isEmpty()
                        || norm(b.getName()).contains(k)
                        || norm(b.getShortName()).contains(k)
                        || norm(b.getCode()).contains(k)
                        || norm(b.getBin()).contains(k))
                .limit(safeLimit)
                .toList();

        return ResponseEntity.ok(data);
    }
}
