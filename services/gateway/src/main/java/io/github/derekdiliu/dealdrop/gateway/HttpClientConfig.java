package io.github.derekdiliu.dealdrop.gateway;

import org.apache.hc.core5.util.TimeValue;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Forwards requests over a bounded, reused connection pool. Without a cap, a sale's burst is passed on
 * one-to-one: a thousand simultaneous requests open a thousand simultaneous connections to the backend,
 * overflowing its accept queue so connections are reset. With a cap, the excess waits here for a pooled
 * connection (cheap on virtual threads) and the backend sees a steady, bounded load.
 */
@Configuration(proxyBeanMethods = false)
class HttpClientConfig {

    // Matches Tomcat's default accept queue, so even a pool opened all at once cannot overflow it.
    private static final int MAX_CONNECTIONS_PER_SERVICE = 100;
    private static final int MAX_CONNECTIONS_TOTAL = 500;
    private static final long MAX_WAIT_FOR_CONNECTION_SECONDS = 10;

    @Bean
    ClientHttpRequestFactoryBuilder<?> clientHttpRequestFactoryBuilder() {
        return ClientHttpRequestFactoryBuilder.httpComponents()
                .withConnectionManagerCustomizer(pool -> pool
                        .setMaxConnPerRoute(MAX_CONNECTIONS_PER_SERVICE)
                        .setMaxConnTotal(MAX_CONNECTIONS_TOTAL))
                // Check every pooled connection before reuse (httpclient's default is only after 2 s idle).
                // A backend that restarts closes the connections it had open; a POST sent on one fails
                // and is not retried, so without the check each one costs a caller a 502.
                .withConnectionConfigCustomizer(connection -> connection
                        .setValidateAfterInactivity(TimeValue.ZERO_MILLISECONDS))
                .withDefaultRequestConfigCustomizer(request -> request
                        .setConnectionRequestTimeout(MAX_WAIT_FOR_CONNECTION_SECONDS, TimeUnit.SECONDS));
    }
}
