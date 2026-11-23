package com.creditintelligence.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.creditintelligence.bureau.api.BureauController;
import com.creditintelligence.bureau.application.BureauReport;
import com.creditintelligence.bureau.application.BureauService;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = BureauController.class, properties = {
        "demo.analyst.user=synthetic-analyst", "demo.analyst.password=synthetic-demo-password"})
@Import(DemoSecurityConfiguration.class)
class BureauHttpSecurityTest {
    private static final String ROUTE = "/api/v1/proposals/{id}/bureau-consultations";
    private final UUID proposal = UUID.randomUUID();
    @Autowired MockMvc mvc;
    @Autowired UserDetailsService users;
    @Autowired ObjectProvider<HttpSecurity> httpSecurity;
    @MockitoBean BureauService service;

    @Test
    void protectsTheBureauAndAcceptsOnlyTheConfiguredAnalystCredentials() throws Exception {
        mvc.perform(get(ROUTE + "/latest", proposal)).andExpect(status().isUnauthorized());
        mvc.perform(get(ROUTE + "/latest", proposal).header(HttpHeaders.AUTHORIZATION, credentials("wrong")))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);

        assertThat(users.loadUserByUsername("synthetic-analyst").getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_ANALYST");
    }

    @Test
    void authenticatedAnalystReceivesTheSavedSyntheticConsultationAndItsLatestSnapshot() throws Exception {
        var id = UUID.randomUUID();
        var report = new BureauReport("SYNTHETIC", "CREDIT_BUREAU", "MOCK", "AVAILABLE", null, "3.0.0",
                id.toString(), new BureauReport.Score(640, null, null, "SYNTHETIC"), null, List.of("Demo only"));
        when(service.consult(proposal, "CLEAN")).thenReturn(new BureauService.Consultation(id, report));
        when(service.latest(proposal)).thenReturn(new BureauService.Snapshot(id, report));
        var authorization = credentials("synthetic-demo-password");

        mvc.perform(post(ROUTE, proposal).header(HttpHeaders.AUTHORIZATION, authorization)
                        .contentType("application/json").content("{\"caseName\":\"CLEAN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.consultationId").value(id.toString()))
                .andExpect(jsonPath("$.report.environment").value("MOCK"));
        mvc.perform(get(ROUTE + "/latest", proposal).header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.report.score.value").value(640));
    }

    @Test
    void permitsUnauthenticatedHealthRouteToReachDispatcher() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isNotFound());
        verifyNoInteractions(service);
    }

    @Test
    void aFreshSecurityConfigurationBuildsTheFiltersAndEncodesAnalystCredentials() throws Exception {
        var configuration = new DemoSecurityConfiguration();
        var encoder = configuration.passwordEncoder();
        var analyst = configuration.userDetailsService("synthetic-other-analyst", "synthetic-other-password", encoder)
                .loadUserByUsername("synthetic-other-analyst");
        var filters = configuration.securityFilterChain(httpSecurity.getObject()).getFilters();

        assertThat(encoder.matches("synthetic-other-password", analyst.getPassword())).isTrue();
        assertThat(analyst.getPassword()).isNotEqualTo("synthetic-other-password");
        assertThat(analyst.getAuthorities()).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_ANALYST");
        assertThat(filters).anyMatch(BasicAuthenticationFilter.class::isInstance)
                .anyMatch(AuthorizationFilter.class::isInstance);
    }

    private static String credentials(String password) {
        return "Basic " + Base64.getEncoder().encodeToString(
                ("synthetic-analyst:" + password).getBytes(StandardCharsets.UTF_8));
    }
}
