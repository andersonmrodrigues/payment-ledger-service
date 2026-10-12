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

  // allowEmptyShould is temporary: contexts are added phase by phase and the flag is removed once
  // every rule has classes to check.

  @ArchTest
  static final ArchRule domainIsFrameworkFree =
      noClasses()
          .that()
          .resideInAPackage("..domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "org.springframework..", "..infrastructure..", "..application..", "..web..")
          .allowEmptyShould(true);

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
              "com.paymentledger.outbox..")
          .allowEmptyShould(true);

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
              "com.paymentledger.reconciliation..")
          .allowEmptyShould(true);

  @ArchTest
  static final ArchRule paymentDoesNotDependOnReconciliation =
      noClasses()
          .that()
          .resideInAPackage("com.paymentledger.payment..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("com.paymentledger.reconciliation..")
          .allowEmptyShould(true);
}
