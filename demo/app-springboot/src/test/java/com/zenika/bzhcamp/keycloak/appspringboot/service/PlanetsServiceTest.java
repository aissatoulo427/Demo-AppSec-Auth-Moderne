package com.zenika.bzhcamp.keycloak.appspringboot.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires du service, sans appel réseau : l'ExchangeFunction du WebClient
 * est remplacée par une réponse JSON simulée du service de planètes.
 */
class PlanetsServiceTest {

    private static final String ENDPOINT = "http://planets.test/planets";

    private final AtomicReference<ClientRequest> lastRequest = new AtomicReference<>();

    private final WebClient webClient = WebClient.builder()
            .exchangeFunction(request -> {
                lastRequest.set(request);
                return Mono.just(ClientResponse.create(HttpStatus.OK)
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .body("[\"Tatooine\",\"Naboo\"]")
                        .build());
            })
            .build();

    private final PlanetsService planetsService = new PlanetsService(webClient, ENDPOINT);

    @Test
    void getPlanets_shouldReturnPlanetsFromRemoteService() {
        List<String> planets = planetsService.getPlanets();

        assertThat(planets).containsExactly("Tatooine", "Naboo");
    }

    @Test
    void getPlanets_shouldCallConfiguredEndpoint() {
        planetsService.getPlanets();

        assertThat(lastRequest.get().url()).hasToString(ENDPOINT);
    }
}
