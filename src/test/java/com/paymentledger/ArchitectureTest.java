package com.paymentledger;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(
    packages = "com.paymentledger",
    importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

  @ArchTest
  static final ArchRule domainIsFrameworkFree =
      noClasses()
          .that()
          .resideInAPackage("..domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "org.springframework..", "..infrastructure..", "..application..", "..web..");

  @ArchTest
  static final ArchRule ledgerDependsOnNoOtherContext =
      noClasses()
          .that()
          .resideInAPackage("com.paymentledger.ledger..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "com.paymentledger.payment..",
              "com.paymentledger.reconciliation..",
              "com.paymentledger.outbox..",
              "com.paymentledger.idempotency..",
              "com.paymentledger.security");

  @ArchTest
  static final ArchRule outboxDependsOnNoOtherContext =
      noClasses()
          .that()
          .resideInAPackage("com.paymentledger.outbox..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "com.paymentledger.ledger..",
              "com.paymentledger.payment..",
              "com.paymentledger.reconciliation..",
              "com.paymentledger.idempotency..",
              "com.paymentledger.security");

  @ArchTest
  static final ArchRule idempotencyDependsOnNoContext =
      noClasses()
          .that()
          .resideInAPackage("com.paymentledger.idempotency..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "com.paymentledger.ledger..",
              "com.paymentledger.payment..",
              "com.paymentledger.reconciliation..",
              "com.paymentledger.outbox..",
              "com.paymentledger.security");

  @ArchTest
  static final ArchRule paymentDoesNotDependOnReconciliation =
      noClasses()
          .that()
          .resideInAPackage("com.paymentledger.payment..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("com.paymentledger.reconciliation..");

  @ArchTest
  static final ArchRule onlyControllersSeeTheCaller =
      noClasses()
          .that()
          .resideInAnyPackage("..domain..", "..application..", "..infrastructure..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("com.paymentledger.security..");

  @ArchTest
  static final ArchRule securityDependsOnNoContext =
      noClasses()
          .that()
          .resideInAPackage("com.paymentledger.security..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "com.paymentledger.ledger..",
              "com.paymentledger.payment..",
              "com.paymentledger.reconciliation..",
              "com.paymentledger.outbox..",
              "com.paymentledger.idempotency..");
}
