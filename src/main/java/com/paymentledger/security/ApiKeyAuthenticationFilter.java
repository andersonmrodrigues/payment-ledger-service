package com.paymentledger.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates {@code Authorization: Bearer <api key>}. A request without the header continues
 * anonymously and the access rules decide; a wrong or malformed one is rejected right away.
 */
class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

  private static final String BEARER = "Bearer ";

  private final ApiKeys keys;
  private final ProblemResponses problems;

  ApiKeyAuthenticationFilter(ApiKeys keys, ProblemResponses problems) {
    this.keys = keys;
    this.problems = problems;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String header = request.getHeader(HttpHeaders.AUTHORIZATION);
    if (header == null) {
      chain.doFilter(request, response);
      return;
    }
    Optional<ApiClient> client =
        header.regionMatches(true, 0, BEARER, 0, BEARER.length())
            ? keys.authenticate(header.substring(BEARER.length()).trim())
            : Optional.empty();
    if (client.isEmpty()) {
      problems.unauthorized(request, response, new BadCredentialsException("Invalid API key"));
      return;
    }
    var authorities =
        client.get().roles().stream()
            .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
            .toList();
    var context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(
        new PreAuthenticatedAuthenticationToken(client.get(), null, authorities));
    SecurityContextHolder.setContext(context);
    chain.doFilter(request, response);
  }
}
