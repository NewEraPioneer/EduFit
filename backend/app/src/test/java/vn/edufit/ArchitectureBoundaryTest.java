package vn.edufit;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Discovery and Connection interface and boundary contracts must not leak adapter dependencies. */
@AnalyzeClasses(packages = "vn.edufit", importOptions = ImportOption.DoNotIncludeTests.class)
public class ArchitectureBoundaryTest {
  @ArchTest
  static final ArchRule discovery_domain_is_pure_java = classes()
      .that().resideInAPackage("vn.edufit.discovery.domain..")
      .should().onlyDependOnClassesThat().resideInAnyPackage("java..", "vn.edufit.discovery.domain..")
      .allowEmptyShould(false);

  @ArchTest
  static final ArchRule discovery_application_does_not_depend_on_web = noClasses()
      .that().resideInAPackage("vn.edufit.discovery.application..")
      .should().dependOnClassesThat().resideInAPackage("vn.edufit.discovery.web..")
      .allowEmptyShould(false);

  @ArchTest
  static final ArchRule discovery_api_does_not_expose_implementation = classes()
      .that().resideInAPackage("vn.edufit.discovery.api..")
      .should().onlyDependOnClassesThat()
      .resideInAnyPackage("java..", "vn.edufit.discovery.api..", "vn.edufit.shared..")
      .allowEmptyShould(false);

  @ArchTest
  static final ArchRule outsiders_use_discovery_public_api = noClasses()
      .that().resideOutsideOfPackage("vn.edufit.discovery..")
      .should().dependOnClassesThat().resideInAnyPackage("vn.edufit.discovery.domain..",
          "vn.edufit.discovery.application..", "vn.edufit.discovery.infra..", "vn.edufit.discovery.web..")
      .allowEmptyShould(false);

  @ArchTest
  static final ArchRule discovery_uses_only_public_edufit_dependencies = classes()
      .that().resideInAPackage("vn.edufit.discovery..")
      .should().onlyDependOnClassesThat(new DescribedPredicate<JavaClass>("are own types, shared kernel or public module/platform APIs") {
        @Override public boolean test(JavaClass target) {
          String pkg = target.getPackageName();
          if (!pkg.startsWith("vn.edufit.")) return true;
          if (pkg.startsWith("vn.edufit.discovery.") || pkg.startsWith("vn.edufit.shared.")) return true;
          String[] parts = pkg.split("\\.");
          return parts.length >= 4 && parts[3].equals("api");
        }
      }).allowEmptyShould(false);

  @ArchTest
  static final ArchRule connection_application_does_not_depend_on_web = noClasses()
      .that().resideInAPackage("vn.edufit.connection.application..")
      .should().dependOnClassesThat().resideInAPackage("vn.edufit.connection.web..")
      .allowEmptyShould(false);

  @ArchTest
  static final ArchRule connection_web_does_not_depend_on_infra = noClasses()
      .that().resideInAPackage("vn.edufit.connection.web..")
      .should().dependOnClassesThat().resideInAPackage("vn.edufit.connection.infra..")
      .allowEmptyShould(false);

  @ArchTest
  static final ArchRule connection_api_does_not_expose_implementation = classes()
      .that().resideInAPackage("vn.edufit.connection.api..")
      .should().onlyDependOnClassesThat()
      .resideInAnyPackage("java..", "vn.edufit.connection.api..", "vn.edufit.shared..")
      .allowEmptyShould(false);

  @ArchTest
  static final ArchRule outsiders_use_connection_public_api = noClasses()
      .that().resideOutsideOfPackage("vn.edufit.connection..")
      .should().dependOnClassesThat().resideInAnyPackage(
          "vn.edufit.connection.application..", "vn.edufit.connection.infra..", "vn.edufit.connection.web..")
      .allowEmptyShould(false);

  @ArchTest
  static final ArchRule connection_uses_only_public_edufit_dependencies = classes()
      .that().resideInAPackage("vn.edufit.connection..")
      .should().onlyDependOnClassesThat(new DescribedPredicate<JavaClass>("are own types, shared kernel or public module/platform APIs") {
        @Override public boolean test(JavaClass target) {
          String pkg = target.getPackageName();
          if (!pkg.startsWith("vn.edufit.")) return true;
          if (pkg.startsWith("vn.edufit.connection.") || pkg.startsWith("vn.edufit.shared.")) return true;
          String[] parts = pkg.split("\\.");
          return parts.length >= 4 && parts[3].equals("api");
        }
      }).allowEmptyShould(false);

  @ArchTest
  static final ArchRule verification_uses_only_public_edufit_dependencies = classes()
      .that().resideInAPackage("vn.edufit.verification..")
      .should().onlyDependOnClassesThat(new DescribedPredicate<JavaClass>("are own types, shared kernel or public module/platform APIs") {
        @Override public boolean test(JavaClass target) {
          String pkg = target.getPackageName();
          if (!pkg.startsWith("vn.edufit.")) return true;
          if (pkg.startsWith("vn.edufit.verification.") || pkg.startsWith("vn.edufit.shared.")) return true;
          String[] parts = pkg.split("\\.");
          return parts.length >= 4 && parts[3].equals("api");
        }
      }).allowEmptyShould(false);

  @ArchTest
  static final ArchRule scheduling_domain_is_pure_java = classes()
      .that().resideInAPackage("vn.edufit.scheduling.domain..")
      .should().onlyDependOnClassesThat().resideInAnyPackage("java..", "vn.edufit.scheduling.domain..", "vn.edufit.shared..")
      .allowEmptyShould(false);

  @ArchTest
  static final ArchRule scheduling_application_does_not_depend_on_web = noClasses()
      .that().resideInAPackage("vn.edufit.scheduling.application..")
      .should().dependOnClassesThat().resideInAPackage("vn.edufit.scheduling.web..")
      .allowEmptyShould(false);

  @ArchTest
  static final ArchRule scheduling_web_does_not_depend_on_infra = noClasses()
      .that().resideInAPackage("vn.edufit.scheduling.web..")
      .should().dependOnClassesThat().resideInAPackage("vn.edufit.scheduling.infra..")
      .allowEmptyShould(false);

  @ArchTest
  static final ArchRule scheduling_web_does_not_depend_on_domain_repository = noClasses()
      .that().resideInAPackage("vn.edufit.scheduling.web..")
      .should().dependOnClassesThat().resideInAPackage("vn.edufit.scheduling.domain.repository..")
      .allowEmptyShould(false);

  @ArchTest
  static final ArchRule scheduling_api_does_not_expose_implementation = classes()
      .that().resideInAPackage("vn.edufit.scheduling.api..")
      .should().onlyDependOnClassesThat()
      .resideInAnyPackage("java..", "vn.edufit.scheduling.api..", "vn.edufit.shared..")
      .allowEmptyShould(false);

  @ArchTest
  static final ArchRule outsiders_use_scheduling_public_api = noClasses()
      .that().resideOutsideOfPackage("vn.edufit.scheduling..")
      .should().dependOnClassesThat().resideInAnyPackage(
          "vn.edufit.scheduling.domain..", "vn.edufit.scheduling.application..",
          "vn.edufit.scheduling.infra..", "vn.edufit.scheduling.web..")
      .allowEmptyShould(false);

  @ArchTest
  static final ArchRule scheduling_uses_only_public_edufit_dependencies = classes()
      .that().resideInAPackage("vn.edufit.scheduling..")
      .should().onlyDependOnClassesThat(new DescribedPredicate<JavaClass>("are own types, shared kernel or public module/platform APIs") {
        @Override public boolean test(JavaClass target) {
          String pkg = target.getPackageName();
          if (!pkg.startsWith("vn.edufit.")) return true;
          if (pkg.startsWith("vn.edufit.scheduling.") || pkg.startsWith("vn.edufit.shared.")) return true;
          String[] parts = pkg.split("\\.");
          return parts.length >= 4 && parts[3].equals("api");
        }
      }).allowEmptyShould(false);

  @ArchTest
  static final ArchRule progress_domain_is_pure_java = classes()
      .that().resideInAPackage("vn.edufit.progress.domain..")
      .should().onlyDependOnClassesThat().resideInAnyPackage("java..", "vn.edufit.progress.domain..", "vn.edufit.shared..")
      .allowEmptyShould(false);

  @ArchTest
  static final ArchRule progress_application_does_not_depend_on_web = noClasses()
      .that().resideInAPackage("vn.edufit.progress.application..")
      .should().dependOnClassesThat().resideInAPackage("vn.edufit.progress.web..")
      .allowEmptyShould(false);

  @ArchTest
  static final ArchRule progress_web_does_not_depend_on_infra = noClasses()
      .that().resideInAPackage("vn.edufit.progress.web..")
      .should().dependOnClassesThat().resideInAPackage("vn.edufit.progress.infra..")
      .allowEmptyShould(false);

  @ArchTest
  static final ArchRule progress_web_does_not_depend_on_domain_repository = noClasses()
      .that().resideInAPackage("vn.edufit.progress.web..")
      .should().dependOnClassesThat().resideInAPackage("vn.edufit.progress.domain.repository..")
      .allowEmptyShould(false);

  @ArchTest
  static final ArchRule progress_api_does_not_expose_implementation = classes()
      .that().resideInAPackage("vn.edufit.progress.api..")
      .should().onlyDependOnClassesThat()
      .resideInAnyPackage("java..", "vn.edufit.progress.api..", "vn.edufit.shared..")
      .allowEmptyShould(false);

  @ArchTest
  static final ArchRule outsiders_use_progress_public_api = noClasses()
      .that().resideOutsideOfPackage("vn.edufit.progress..")
      .should().dependOnClassesThat().resideInAnyPackage(
          "vn.edufit.progress.domain..", "vn.edufit.progress.application..",
          "vn.edufit.progress.infra..", "vn.edufit.progress.web..")
      .allowEmptyShould(false);

  @ArchTest
  static final ArchRule progress_uses_only_public_edufit_dependencies = classes()
      .that().resideInAPackage("vn.edufit.progress..")
      .should().onlyDependOnClassesThat(new DescribedPredicate<JavaClass>("are own types, shared kernel or public module/platform APIs") {
        @Override public boolean test(JavaClass target) {
          String pkg = target.getPackageName();
          if (!pkg.startsWith("vn.edufit.")) return true;
          if (pkg.startsWith("vn.edufit.progress.") || pkg.startsWith("vn.edufit.shared.")) return true;
          String[] parts = pkg.split("\\.");
          return parts.length >= 4 && parts[3].equals("api");
        }
      }).allowEmptyShould(false);

  @ArchTest
  static final ArchRule review_uses_only_public_edufit_dependencies = classes()
      .that().resideInAPackage("vn.edufit.review..")
      .should().onlyDependOnClassesThat(new DescribedPredicate<JavaClass>("are own types, shared kernel or public module/platform APIs") {
        @Override public boolean test(JavaClass target) {
          String pkg = target.getPackageName();
          if (!pkg.startsWith("vn.edufit.")) return true;
          if (pkg.startsWith("vn.edufit.review.") || pkg.startsWith("vn.edufit.shared.")) return true;
          String[] parts = pkg.split("\\.");
          return parts.length >= 4 && parts[3].equals("api");
        }
      }).allowEmptyShould(false);

  @ArchTest
  static final ArchRule notification_uses_only_public_edufit_dependencies = classes()
      .that().resideInAPackage("vn.edufit.notification..")
      .should().onlyDependOnClassesThat(new DescribedPredicate<JavaClass>("are own types, shared kernel or public module/platform APIs") {
        @Override public boolean test(JavaClass target) {
          String pkg = target.getPackageName();
          if (!pkg.startsWith("vn.edufit.")) return true;
          if (pkg.startsWith("vn.edufit.notification.") || pkg.startsWith("vn.edufit.shared.")) return true;
          String[] parts = pkg.split("\\.");
          return parts.length >= 4 && parts[3].equals("api");
        }
      }).allowEmptyShould(false);

  @ArchTest
  static void production_import_contains_expected_layers(JavaClasses imported) {
    for (String name : new String[] {
        "vn.edufit.discovery.application.matching.MatchScorer",
        "vn.edufit.discovery.application.service.TutorMatchingService",
        "vn.edufit.discovery.application.service.DiscoveryFacadeImpl",
        "vn.edufit.discovery.api.DiscoveryFacade",
        "vn.edufit.discovery.web.DiscoveryController",
        "vn.edufit.connection.api.ConnectionFacade",
        "vn.edufit.connection.api.event.LinkInvitationChangedEvent",
        "vn.edufit.connection.api.event.ConnectionRequestChangedEvent",
        "vn.edufit.connection.application.service.ConnectionFacadeImpl",
        "vn.edufit.connection.application.service.LinkInvitationService",
        "vn.edufit.connection.application.service.ConnectionRequestService",
        "vn.edufit.connection.application.service.ParentStudentLinkService",
        "vn.edufit.connection.application.service.ConnectionExpiryService",
        "vn.edufit.connection.application.service.ConnectionQueryService",
        "vn.edufit.connection.web.ConnectionController",
        "vn.edufit.connection.infra.persistence.repository.LinkInvitationRepository",
        "vn.edufit.connection.infra.persistence.repository.ConnectionRequestRepository",
        "vn.edufit.connection.infra.persistence.repository.ParentStudentLinkRepository",
        "vn.edufit.connection.infra.persistence.repository.TutoringClassRepository",
        "vn.edufit.connection.infra.scheduling.ConnectionExpiryJob",
        "vn.edufit.profile.application.service.ProfileFacadeImpl",
        "vn.edufit.verification.application.service.AdminVerificationService",
        "vn.edufit.verification.application.service.TutorVerificationService",
        "vn.edufit.scheduling.api.SessionFacade",
        "vn.edufit.scheduling.application.service.SessionFacadeImpl",
        "vn.edufit.scheduling.application.service.ProposeSessionService",
        "vn.edufit.scheduling.application.service.BatchProposeSessionService",
        "vn.edufit.scheduling.application.service.BatchRespondSessionService",
        "vn.edufit.scheduling.application.service.RescheduleClassScheduleService",
        "vn.edufit.scheduling.application.service.SchedulingSecurityResolver",
        "vn.edufit.scheduling.application.service.GetSessionDetailService",
        "vn.edufit.scheduling.domain.model.TutoringSession",
        "vn.edufit.scheduling.domain.policy.ConflictPolicy",
        "vn.edufit.scheduling.web.SessionController",
        "vn.edufit.scheduling.infra.adapter.ConnectionValidationAdapter",
        "vn.edufit.scheduling.infra.adapter.SessionScheduleConflictCheckerAdapter",
        "vn.edufit.scheduling.infra.persistence.repository.SpringDataTutoringSessionRepository",
        "vn.edufit.progress.api.ProgressFacade",
        "vn.edufit.progress.application.service.ProgressFacadeImpl",
        "vn.edufit.progress.application.service.LearningPlanService",
        "vn.edufit.progress.application.service.ProgressDashboardService",
        "vn.edufit.progress.application.service.SessionNoteService",
        "vn.edufit.progress.domain.model.LearningPlan",
        "vn.edufit.progress.domain.policy.ProgressCalculator",
        "vn.edufit.progress.web.ProgressPlanController",
        "vn.edufit.progress.web.ProgressDashboardController",
        "vn.edufit.progress.infra.persistence.repository.SpringDataLearningPlanRepository",
        "vn.edufit.review.api.ReviewFacade",
        "vn.edufit.review.application.service.ReviewFacadeImpl",
        "vn.edufit.review.application.service.ReviewService",
        "vn.edufit.review.web.ReviewController",
        "vn.edufit.notification.application.service.NotificationService",
        "vn.edufit.notification.application.listener.NotificationEventListener",
        "vn.edufit.notification.web.NotificationController"}) {
      assertTrue(imported.contain(name), "Architecture import must include " + name);
    }

    long connectionClassesCount = imported.stream()
        .filter(c -> c.getPackageName().startsWith("vn.edufit.connection"))
        .count();
    assertTrue(connectionClassesCount >= 20, "Architecture import must include non-empty connection classes, found: " + connectionClassesCount);

    long schedulingClassesCount = imported.stream()
        .filter(c -> c.getPackageName().startsWith("vn.edufit.scheduling"))
        .count();
    assertTrue(schedulingClassesCount >= 20, "Architecture import must include non-empty scheduling classes, found: " + schedulingClassesCount);

    long progressClassesCount = imported.stream()
        .filter(c -> c.getPackageName().startsWith("vn.edufit.progress"))
        .count();
    assertTrue(progressClassesCount >= 20, "Architecture import must include non-empty progress classes, found: " + progressClassesCount);

    long reviewClassesCount = imported.stream()
        .filter(c -> c.getPackageName().startsWith("vn.edufit.review"))
        .count();
    assertTrue(reviewClassesCount >= 10, "Architecture import must include non-empty review classes, found: " + reviewClassesCount);

    long notificationClassesCount = imported.stream()
        .filter(c -> c.getPackageName().startsWith("vn.edufit.notification"))
        .count();
    assertTrue(notificationClassesCount >= 8, "Architecture import must include non-empty notification classes, found: " + notificationClassesCount);
  }
}
