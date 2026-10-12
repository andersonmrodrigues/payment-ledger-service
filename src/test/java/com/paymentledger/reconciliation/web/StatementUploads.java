package com.paymentledger.reconciliation.web;

import java.nio.charset.StandardCharsets;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;

public final class StatementUploads {

  private StatementUploads() {}

  /** A multipart body carrying {@code csv} as the {@code statement} file. */
  public static HttpEntity<LinkedMultiValueMap<String, Object>> statement(String csv) {
    var file =
        new ByteArrayResource(csv.getBytes(StandardCharsets.UTF_8)) {
          @Override
          public String getFilename() {
            return "statement.csv";
          }
        };
    var body = new LinkedMultiValueMap<String, Object>();
    body.add("statement", file);
    var headers = new HttpHeaders();
    headers.setContentType(MediaType.MULTIPART_FORM_DATA);
    return new HttpEntity<>(body, headers);
  }
}
