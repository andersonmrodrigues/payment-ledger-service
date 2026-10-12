package com.paymentledger.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.paymentledger.security.SecurityProperties.Client;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ApiKeysTest {

  // SHA-256 of "checkout-test-key" and "back-office-test-key"
  private static final String CHECKOUT_SHA =
      "b980c273cf5c68e1acd15f8ceb3f90793d8a504132d4b7f65d721ee2253f2e2d";
  private static final String BACK_OFFICE_SHA =
      "e52f867269d5a795a8a9710253e8cee4aa38c1a66602a00aad0f4825ad8dbde8";

  private final ApiKeys keys =
      new ApiKeys(
          new SecurityProperties(
              List.of(
                  new Client("checkout", List.of(CHECKOUT_SHA), Set.of(Role.PAYMENTS)),
                  new Client("back-office", List.of(BACK_OFFICE_SHA), Set.of(Role.BACK_OFFICE)))));

  @Test
  void findsTheClientAKeyBelongsTo() {
    assertThat(keys.authenticate("checkout-test-key"))
        .contains(new ApiClient("checkout", Set.of(Role.PAYMENTS)));
    assertThat(keys.authenticate("back-office-test-key"))
        .contains(new ApiClient("back-office", Set.of(Role.BACK_OFFICE)));
  }

  @Test
  void rejectsUnknownKeysAndTheHashItself() {
    assertThat(keys.authenticate("checkout-test-key ")).isEmpty();
    assertThat(keys.authenticate("")).isEmpty();
    assertThat(keys.authenticate(CHECKOUT_SHA)).isEmpty();
  }

  @Test
  void aClientWithTwoKeysAcceptsBothWhileOneIsRotatedOut() {
    var rotating =
        new ApiKeys(
            new SecurityProperties(
                List.of(
                    new Client(
                        "checkout",
                        List.of(CHECKOUT_SHA, BACK_OFFICE_SHA),
                        Set.of(Role.PAYMENTS)))));

    assertThat(rotating.authenticate("checkout-test-key")).map(ApiClient::id).contains("checkout");
    assertThat(rotating.authenticate("back-office-test-key"))
        .map(ApiClient::id)
        .contains("checkout");
  }

  @Test
  void noClientsMeansNoKeyIsValid() {
    var none = new ApiKeys(new SecurityProperties(null));

    assertThat(none.isEmpty()).isTrue();
    assertThat(none.authenticate("checkout-test-key")).isEmpty();
  }

  @Test
  void refusesAConfigurationThatCouldNotWorkAsIntended() {
    assertThatThrownBy(() -> new Client("checkout", List.of("not-a-hash"), Set.of(Role.PAYMENTS)))
        .hasMessageContaining("key-sha256");
    assertThatThrownBy(
            () ->
                new Client("checkout", List.of(CHECKOUT_SHA.toUpperCase()), Set.of(Role.PAYMENTS)))
        .hasMessageContaining("key-sha256");
    assertThatThrownBy(() -> new Client("checkout", List.of(), Set.of(Role.PAYMENTS)))
        .hasMessageContaining("key-sha256");
    assertThatThrownBy(() -> new Client("checkout", List.of(CHECKOUT_SHA), Set.of()))
        .hasMessageContaining("role");
    assertThatThrownBy(() -> new Client("check out", List.of(CHECKOUT_SHA), Set.of(Role.PAYMENTS)))
        .hasMessageContaining("id");
    assertThatThrownBy(
            () ->
                new SecurityProperties(
                    List.of(
                        new Client("a", List.of(CHECKOUT_SHA), Set.of(Role.PAYMENTS)),
                        new Client("a", List.of(BACK_OFFICE_SHA), Set.of(Role.PAYMENTS)))))
        .hasMessageContaining("Duplicate client id");
    assertThatThrownBy(
            () ->
                new SecurityProperties(
                    List.of(
                        new Client("a", List.of(CHECKOUT_SHA), Set.of(Role.PAYMENTS)),
                        new Client("b", List.of(CHECKOUT_SHA), Set.of(Role.BACK_OFFICE)))))
        .hasMessageContaining("reuses a key");
  }
}
