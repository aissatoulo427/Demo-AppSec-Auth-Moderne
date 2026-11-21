package com.zenika.bzhcamp.keycloak.appspringboot.web;

import com.zenika.bzhcamp.keycloak.appspringboot.service.PlanetsService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.ui.ExtendedModelMap;

import java.security.Principal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires du contrôleur, sans contexte Spring : le contrôleur est instancié
 * directement (injection par constructeur), seul PlanetsService est simulé.
 */
class PlanetsControllerTest {

    private final PlanetsService planetsService = mock(PlanetsService.class);
    private final PlanetsController controller = new PlanetsController(planetsService);

    @Test
    void landing_shouldReturnLandingView() {
        assertThat(controller.landing()).isEqualTo("landing");
    }

    @Test
    void about_shouldReturnAboutView() {
        assertThat(controller.about()).isEqualTo("about");
    }

    @Test
    void handleLogout_shouldRedirectToRoot() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();

        assertThat(controller.handleLogout(request)).isEqualTo("redirect:/");
    }

    @Test
    void returnNoFavicon_shouldReturn204() {
        ResponseEntity<Void> response = controller.returnNoFavicon();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(response.getBody()).isNull();
    }

    @Test
    void handlePlanetsRequest_shouldFillModelAndReturnPlanetsView() {
        Principal principal = () -> "luke";
        ExtendedModelMap model = new ExtendedModelMap();
        when(planetsService.getPlanets()).thenReturn(List.of("Tatooine", "Naboo"));

        String view = controller.handlePlanetsRequest(principal, model);

        assertThat(view).isEqualTo("planets");
        assertThat(model.getAttribute("principal")).isSameAs(principal);
        assertThat(model.getAttribute("planets")).isEqualTo(List.of("Tatooine", "Naboo"));
    }
}
