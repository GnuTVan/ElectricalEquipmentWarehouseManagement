package com.eewms.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "taxlookup")
public class TaxLookupProperties {
    private String provider;          // vietqr
    private String baseUrl;           // https://api.vietqr.io/v2
    private int timeoutMs;            // 4000
    private int cacheTtlMinutes;      // 1440
}
