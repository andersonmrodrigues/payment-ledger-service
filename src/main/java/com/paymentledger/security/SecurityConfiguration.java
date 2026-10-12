package com.paymentledger.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

/**
 * Stateless API key authentication. Health and metrics stay open: they are served on the management
 * port, which is meant to stay off the public network.
 */
@Configuration
class SecurityConfiguration {

  private static final Logger log = LoggerFactory.getLogger(SecurityConfiguration.class);

  @Bean
  SecurityFilterChain api(HttpSecurity http, SecurityProperties properties, ObjectMapper json)
      throws Exception {
    var keys = new ApiKeys(properties);
    if (keys.isEmpty()) {
      log.warn("No API clients are configured (security.clients); every API call will get 401");
    }
    var problems = new ProblemResponses(json);
    return http.csrf(AbstractHttpConfigurer::disable)
        .httpBasic(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .logout(AbstractHttpConfigurer::disable)
        .requestCache(AbstractHttpConfigurer::disable)
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .addFilterBefore(
            new ApiKeyAuthenticationFilter(keys, problems), AnonymousAuthenticationFilter.class)
        .authorizeHttpRequests(
            requests ->
                requests
                    .requestMatchers("/actuator/health/**", "/actuator/prometheus", "/error")
                    .permitAll()
                    .requestMatchers("/payments/**")
                    .hasRole(Role.PAYMENTS.name())
                    .requestMatchers("/reconciliations/**", "/outbox/**")
                    .hasRole(Role.BACK_OFFICE.name())
                    .anyRequest()
                    .denyAll())
        .exceptionHandling(
            errors ->
                errors
                    .authenticationEntryPoint(problems::unauthorized)
                    .accessDeniedHandler(problems::forbidden))
        .build();
  }
}
