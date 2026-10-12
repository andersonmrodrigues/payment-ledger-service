package com.paymentledger.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;

/** 401 and 403 as RFC 9457 problem details, like every other error of the API. */
class ProblemResponses {

  private final ObjectMapper json;

  ProblemResponses(ObjectMapper json) {
    this.json = json;
  }

  void unauthorized(
      HttpServletRequest request, HttpServletResponse response, AuthenticationException e)
      throws IOException {
    response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
    write(request, response, HttpStatus.UNAUTHORIZED, "A valid API key is required");
  }

  void forbidden(HttpServletRequest request, HttpServletResponse response, AccessDeniedException e)
      throws IOException {
    write(request, response, HttpStatus.FORBIDDEN, "This API key may not call this endpoint");
  }

  private void write(
      HttpServletRequest request, HttpServletResponse response, HttpStatus status, String detail)
      throws IOException {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setInstance(URI.create(request.getRequestURI()));
    response.setStatus(status.value());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    json.writeValue(response.getOutputStream(), problem);
  }
}
