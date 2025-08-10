package com.eewms.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Configuration
public class WebClientConfig {

    @Bean
    public WebClient webClient(TaxLookupProperties props) {
        int ms = Math.max(props.getTimeoutMs(), 2000); // safety floor
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, ms)
                .responseTimeout(Duration.ofMillis(ms))
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(ms, TimeUnit.MILLISECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(ms, TimeUnit.MILLISECONDS)));

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}
