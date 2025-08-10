package com.eewms.services.impl;

import com.eewms.config.TaxLookupProperties;
import com.eewms.dto.VietQR.BankDTO;
import com.eewms.dto.VietQR.BizInfo;
import com.eewms.dto.VietQR.TaxLookupResponse;
import com.eewms.dto.VietQR.VietqrBankResponse;
import com.eewms.services.IVietqrService;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class VietqrServiceImpl implements IVietqrService {

    private final WebClient webClient;
    private final TaxLookupProperties props;

    @Value("${vietqr.base-url:https://api.vietqr.io}")
    private String baseUrl;

    @Value("${vietqr.api-key:}")
    private String apiKey;
    private static final long BANK_CACHE_TTL_MIN = 30;
    private volatile List<BankDTO> bankCache = List.of();
    private volatile Instant bankCacheAt = Instant.EPOCH;

    private static final Map<String, CacheEntry> CACHE = new ConcurrentHashMap<>();

    @Override
    public TaxLookupResponse lookupByTaxCode(String taxCode) {
        String mst = normalize(taxCode);
        if (mst.isEmpty() || !mst.matches("\\d{8,14}")) {
            return TaxLookupResponse.builder().found(false).message("Mã số thuế không hợp lệ").build();
        }

        BizInfo cached = getFromCache(mst);
        if (cached != null) {
            return TaxLookupResponse.builder().found(true).message("OK (cache)").data(cached).build();
        }

        try {
            BizInfo info = callVietqr(mst);
            if (info == null) {
                return TaxLookupResponse.builder().found(false).message("Không tìm thấy theo MST").build();
            }
            putToCache(mst, info);
            return TaxLookupResponse.builder().found(true).message("OK").data(info).build();
        } catch (Exception e) {
            return TaxLookupResponse.builder().found(false).message("Không truy cập được dịch vụ tra cứu").build();
        }
    }


    private BizInfo callVietqr(String mst) {
        String url = props.getBaseUrl() + "/business/" + mst;
        VietqrResponse res = webClient.get()
                .uri(url)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, r -> Mono.error(new RuntimeException("Provider 4xx")))
                .onStatus(HttpStatusCode::is5xxServerError, r -> Mono.error(new RuntimeException("Provider 5xx")))
                .bodyToMono(VietqrResponse.class)
                .block();
        if (res == null || res.data == null) return null;

        return BizInfo.builder()
                .taxCode(nz(res.data.taxCode))
                .name(nz(res.data.name))
                .shortName(nz(res.data.shortName))
                .address(nz(res.data.address))
                .provinceCode(nz(res.data.provinceCode))
                .representative(nz(res.data.representative))
                .status(nz(res.data.status))
                .build();
    }

    private String normalize(String v) {
        return v == null ? "" : v.replaceAll("[^0-9]", "");
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private record CacheEntry(BizInfo data, long ts) {
    }

    private BizInfo getFromCache(String key) {
        CacheEntry ce = CACHE.get(key);
        if (ce == null) return null;
        long ttlMs = Duration.ofMinutes(Math.max(1, props.getCacheTtlMinutes())).toMillis();
        if (Instant.now().toEpochMilli() - ce.ts > ttlMs) {
            CACHE.remove(key);
            return null;
        }
        return ce.data;
    }

    private void putToCache(String key, BizInfo data) {
        CACHE.put(key, new CacheEntry(data, Instant.now().toEpochMilli()));
    }

    // DTO map từ VietQR
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class VietqrResponse {
        @JsonProperty("data")
        Data data;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class Data {
        @JsonProperty("taxCode")
        String taxCode;
        @JsonProperty("name")
        String name;
        @JsonProperty("shortName")
        String shortName;
        @JsonProperty("address")
        String address;
        @JsonProperty("provinceCode")
        String provinceCode;
        @JsonProperty("representative")
        String representative;
        @JsonProperty("status")
        String status;
    }

    @Override
    public List<BankDTO> getBanks() {
        return getBanks(false);
    }

    @Override
    public synchronized List<BankDTO> getBanks(boolean force) {
        boolean fresh = Duration.between(bankCacheAt, Instant.now()).toMinutes() < BANK_CACHE_TTL_MIN;
        if (!force && fresh && !bankCache.isEmpty()) return bankCache;

        bankCache = fetchBanks();
        bankCacheAt = Instant.now();
        return bankCache;
    }

    private List<BankDTO> fetchBanks(){
        try{
            VietqrBankResponse res = webClient.get()
                    .uri(baseUrl + "/v2/banks")
                    .headers(h -> { if (apiKey!=null && !apiKey.isBlank()) h.set("x-api-key", apiKey); })
                    .retrieve()
                    .bodyToMono(VietqrBankResponse.class)
                    .block();
            if (res==null || res.getData()==null) return List.of();
            return res.getData().stream().map(i -> BankDTO.builder()
                    .code(nz(i.getCode())).name(nz(i.getName()))
                    .shortName(nz(i.getShortName())).bin(nz(i.getBin()))
                    .logoUrl(nz(i.getLogo()))
                    .build()).toList();
        }catch(Exception e){ return List.of(); }
    }
}
