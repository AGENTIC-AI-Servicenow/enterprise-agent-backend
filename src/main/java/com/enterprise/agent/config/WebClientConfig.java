package com.enterprise.agent.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Configuration
public class WebClientConfig {

    @Value("${servicenow.base-url}")
    private String serviceNowBaseUrl;

    public WebClientConfig() {
    }

    @Bean
    public WebClient.Builder webClientBuilder() {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000) // 10s connection
                .responseTimeout(Duration.ofSeconds(30)) // 30s response timeout
                .doOnConnected(conn ->
                        conn.addHandlerLast(new ReadTimeoutHandler(30, TimeUnit.SECONDS))
                                .addHandlerLast(new WriteTimeoutHandler(30, TimeUnit.SECONDS)));

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient));
    }

    @Bean
    public WebClient serviceNowWebClient(WebClient.Builder webClientBuilder,
                                          ServiceNowWebClientFilter serviceNowWebClientFilter) {
        return webClientBuilder
                .baseUrl(serviceNowBaseUrl)
                .filter(serviceNowWebClientFilter)
                .build();
    }
}
