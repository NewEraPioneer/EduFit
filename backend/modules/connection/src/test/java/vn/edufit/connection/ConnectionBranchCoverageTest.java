package vn.edufit.connection;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import vn.edufit.connection.api.ConnectionFacade;
import vn.edufit.connection.application.dto.*;
import vn.edufit.connection.application.policy.ConnectionRequestInput;
import vn.edufit.connection.application.policy.ConnectionTransitions;
import vn.edufit.connection.application.port.ConnectionLock;
import vn.edufit.connection.application.service.*;
import vn.edufit.connection.infra.persistence.entity.*;
import vn.edufit.connection.infra.persistence.repository.*;
import vn.edufit.connection.web.ConnectionController;
import vn.edufit.connection.web.request.RevokeBody;
import vn.edufit.iam.api.AccountRole;
import vn.edufit.iam.api.AccountStatus;
import vn.edufit.iam.api.IamFacade;
import vn.edufit.iam.api.dto.UserSummaryView;
import vn.edufit.profile.api.ProfileFacade;
import vn.edufit.profile.api.dto.*;
import vn.edufit.shared.audit.AuditSink;
import vn.edufit.shared.auth.CurrentUser;
import vn.edufit.shared.exception.*;
import vn.edufit.shared.notification.NotificationSink;

/**
 * Dedicated test suite targeting edge cases, defensive checks, and sub-branches
 * to ensure robust execution and comprehensive JaCoCo branch coverage.
 */
class ConnectionBranchCoverageTest {
  private static final Instant NOW = Instant.parse("2026-10-15T00:00:00Z");
  private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

  private CurrentUser mockActor(UUID userId, String... roles) {
    CurrentUser actor = mock(CurrentUser.class);
    when(actor.getUserId()).thenReturn(userId);
    Set<String> roleSet = Set.of(roles);
    when(actor.hasRole(anyString())).thenAnswer(inv -> roleSet.contains(inv.getArgument(0)));
    return actor;
  }

  // =========================================================================
  // 1. ConnectionRequestInput Branch Coverage
  // =========================================================================
  @Test
  void requestInputValidatesSubConditionsAndModes() {
    ConnectionRequestInput legacy = new ConnectionRequestInput("legacy-compatible");
    assertThrows(InvalidOperationException.class, () -> legacy.validate(null, "ok"));
    assertThrows(InvalidOperationException.class, () -> legacy.validate(List.of(), "ok"));
    assertThrows(InvalidOperationException.class, () -> legacy.validate(Collections.singletonList(null), "ok"));
    assertThrows(InvalidOperationException.class, () -> legacy.validate(List.of("   "), "ok"));
    assertThrows(InvalidOperationException.class, () -> legacy.validate(List.of("x".repeat(101)), "ok"));
    assertThrows(InvalidOperationException.class, () -> legacy.validate(List.of("MONDAY 18:00-19:00"), "y".repeat(501)));

    // Unknown mode
    assertThrows(IllegalArgumentException.class, () -> new ConnectionRequestInput("unknown-mode"));

    // Strict mode validations
    ConnectionRequestInput strict = new ConnectionRequestInput("strict-v1");
    // Invalid regex pattern
    assertThrows(InvalidOperationException.class, () -> strict.validate(List.of("MONDAY 25:00-19:00"), null));
    // Start >= end
    assertThrows(InvalidOperationException.class, () -> strict.validate(List.of("MONDAY 19:00-18:00"), null));
    assertThrows(InvalidOperationException.class, () -> strict.validate(List.of("MONDAY 18:00-18:00"), null));
    // Overlapping intervals
    assertThrows(InvalidOperationException.class, () -> strict.validate(List.of("MONDAY 18:00-19:30", "MONDAY 19:00-20:00"), null));
    // Valid adjacent intervals
    assertDoesNotThrow(() -> strict.validate(List.of("MONDAY 18:00-19:00", "MONDAY 19:00-20:00"), null));
    // Valid intervals on different days
    assertDoesNotThrow(() -> strict.validate(List.of("TUESDAY 18:00-19:00", "MONDAY 18:00-19:00"), "Short message"));
  }

  // =========================================================================
  // 2. ConnectionTransitions Branch Coverage
  // =========================================================================
  @Test
  void transitionsCheckAllBranches() {
    assertFalse(ConnectionTransitions.cooldownActive(null, NOW));
    assertTrue(ConnectionTransitions.cooldownActive(NOW.plusSeconds(10), NOW));
    assertFalse(ConnectionTransitions.cooldownActive(NOW.minusSeconds(10), NOW));
    assertFalse(ConnectionTransitions.cooldownActive(NOW, NOW));

    assertTrue(ConnectionTransitions.isDue(NOW.minusSeconds(1), NOW));
    assertTrue(ConnectionTransitions.isDue(NOW, NOW));
    assertFalse(ConnectionTransitions.isDue(NOW.plusSeconds(1), NOW));

    assertDoesNotThrow(() -> ConnectionTransitions.requireState("PENDING", "PENDING"));
    assertThrows(ResourceConflictException.class, () -> ConnectionTransitions.requireState("ACCEPTED", "PENDING"));

    assertDoesNotThrow(() -> ConnectionTransitions.requirePending("PENDING", NOW.plusSeconds(60), NOW));
    assertThrows(ResourceConflictException.class, () -> ConnectionTransitions.requirePending("EXPIRED", NOW.plusSeconds(60), NOW));
    assertThrows(ResourceConflictException.class, () -> ConnectionTransitions.requirePending("PENDING", NOW.minusSeconds(60), NOW));
  }

  // =========================================================================
  // 3. ConnectionController Branch Coverage
  // =========================================================================
  @Test
  void controllerBranchesCovered() {
    ConnectionRequestService reqService = mock(ConnectionRequestService.class);
    LinkInvitationService invService = mock(LinkInvitationService.class);
    ParentStudentLinkService linkService = mock(ParentStudentLinkService.class);
    ConnectionQueryService queryService = mock(ConnectionQueryService.class);

    UUID userId = UUID.randomUUID();
    CurrentUser tutorActor = mockActor(userId, "TUTOR");
    CurrentUser studentActor = mockActor(userId, "STUDENT");

    @SuppressWarnings("unchecked")
    org.springframework.beans.factory.ObjectProvider<CurrentUser> userProvider = mock(org.springframework.beans.factory.ObjectProvider.class);
    when(userProvider.getObject()).thenReturn(tutorActor);

    ConnectionController controller = new ConnectionController(reqService, invService, linkService, queryService, userProvider);

    // Tutor calls requests and requestsPage
    when(queryService.tutorInbox(tutorActor)).thenReturn(List.of());
    when(queryService.tutorInboxPage(tutorActor, 0, 20)).thenReturn(PageResponse.of(List.of(), 0, 20, 0));
    assertNotNull(controller.requests(null));
    assertNotNull(controller.requestsPage(null, 0, 20));

    // Student calls requests and requestsPage
    when(userProvider.getObject()).thenReturn(studentActor);
    when(queryService.requests(studentActor, null)).thenReturn(List.of());
    when(queryService.requestsPage(studentActor, null, 0, 20)).thenReturn(PageResponse.of(List.of(), 0, 20, 0));
    assertNotNull(controller.requests(null));
    assertNotNull(controller.requestsPage(null, 0, 20));

    // Revoke with null body vs non-null body
    UUID linkId = UUID.randomUUID();
    assertDoesNotThrow(() -> controller.revoke(linkId, null));
    assertDoesNotThrow(() -> controller.revoke(linkId, new RevokeBody("Reason")));

    // Conflict transition branch
    when(invService.withdrawInvitation(any(), eq(linkId))).thenReturn(TransitionResult.conflict("EXPIRED", "Conflict error"));
    assertThrows(ResourceConflictException.class, () -> controller.withdraw(linkId));
  }

  // =========================================================================
  // 4. ConnectionQueryService Branch Coverage
  // =========================================================================
  @Test
  void queryServiceCoversRolesAccessAndPaginationBranches() {
    LinkInvitationRepository invRepo = mock(LinkInvitationRepository.class);
    ParentStudentLinkRepository linkRepo = mock(ParentStudentLinkRepository.class);
    ConnectionRequestRepository reqRepo = mock(ConnectionRequestRepository.class);
    ProfileFacade profile = mock(ProfileFacade.class);
    IamFacade iam = mock(IamFacade.class);
    ConnectionFacade facade = mock(ConnectionFacade.class);
    ConnectionExpiryService expiryService = mock(ConnectionExpiryService.class);

    ConnectionQueryService service = new ConnectionQueryService(invRepo, linkRepo, reqRepo, org.mockito.Mockito.mock(vn.edufit.connection.infra.persistence.repository.TutoringClassRepository.class), profile, iam, facade, expiryService, new vn.edufit.connection.application.policy.SlotConflictDetector(), ConnectionTestDefaults.noScheduleConflicts(), ConnectionTestDefaults.noScheduledSessions(), clock);

    UUID userId = UUID.randomUUID();
    UUID studentId = UUID.randomUUID();
    CurrentUser student = mockActor(userId, "STUDENT");
    CurrentUser parent = mockActor(userId, "PARENT");
    CurrentUser tutor = mockActor(userId, "TUTOR");
    CurrentUser admin = mockActor(userId, "ADMIN");
    CurrentUser stranger = mockActor(userId, "GUEST");

    // requireRole violations
    assertThrows(ForbiddenOperationException.class, () -> service.invitations(stranger));
    assertThrows(ForbiddenOperationException.class, () -> service.links(stranger));
    assertThrows(ForbiddenOperationException.class, () -> service.requests(stranger, studentId));
    assertThrows(ForbiddenOperationException.class, () -> service.tutorInbox(stranger));
    assertThrows(ForbiddenOperationException.class, () -> service.adminLinksPage(stranger, "test@test.com", 0, 20));

    // requireStudentAccess violations
    when(profile.getStudentIdByUserId(userId)).thenReturn(studentId);
    when(profile.findStudentById(studentId)).thenReturn(Optional.of(new StudentSummaryDto(studentId, UUID.randomUUID(), 4, null, null, true)));
    assertThrows(ForbiddenOperationException.class, () -> service.requests(student, studentId));
    when(facade.hasConfirmedParentLink(userId, studentId)).thenReturn(false);
    assertThrows(ForbiddenOperationException.class, () -> service.requests(parent, studentId));

    // Parent calls requests without studentId
    assertThrows(InvalidOperationException.class, () -> service.requests(parent, null));

    // Admin links page validation and empty lookups
    assertThrows(InvalidOperationException.class, () -> service.adminLinksPage(admin, null, 0, 20));
    assertThrows(InvalidOperationException.class, () -> service.adminLinksPage(admin, "  ", 0, 20));
    assertThrows(InvalidOperationException.class, () -> service.adminLinksPage(admin, "email@test.com", -1, 20));
    assertThrows(InvalidOperationException.class, () -> service.adminLinksPage(admin, "email@test.com", 0, 0));
    assertThrows(InvalidOperationException.class, () -> service.adminLinksPage(admin, "email@test.com", 0, 101));

    when(iam.findUserSummaryByEmail("unknown@test.com")).thenReturn(Optional.empty());
    PageResponse<ParentStudentLinkResult> emptyPage = service.adminLinksPage(admin, "unknown@test.com", 0, 20);
    assertTrue(emptyPage.items().isEmpty());

    // Admin lookup for STUDENT without student profile
    UUID stUser = UUID.randomUUID();
    when(iam.findUserSummaryByEmail("student_no_prof@test.com")).thenReturn(Optional.of(new UserSummaryView(stUser, "student_no_prof@test.com", "Name", AccountRole.STUDENT, AccountStatus.ACTIVE)));
    when(profile.findStudentByUserId(stUser)).thenReturn(Optional.empty());
    assertTrue(service.adminLinksPage(admin, "student_no_prof@test.com", 0, 20).items().isEmpty());

    // Admin lookup for TUTOR role
    UUID tutUser = UUID.randomUUID();
    when(iam.findUserSummaryByEmail("tutor@test.com")).thenReturn(Optional.of(new UserSummaryView(tutUser, "tutor@test.com", "Name", AccountRole.TUTOR, AccountStatus.ACTIVE)));
    assertTrue(service.adminLinksPage(admin, "tutor@test.com", 0, 20).items().isEmpty());

    // Tutor inbox with expired request and missing metadata
    UUID tutorId = UUID.randomUUID();
    UUID overdueReqId = UUID.randomUUID();
    when(profile.getTutorIdByUserId(userId)).thenReturn(tutorId);
    ConnectionRequest overdueReq = new ConnectionRequest(studentId, tutorId, UUID.randomUUID(), UUID.randomUUID(), List.of("Mon 18:00"), null, NOW.minus(ConnectionTransitions.REQUEST_TTL).minusSeconds(10));
    org.springframework.test.util.ReflectionTestUtils.setField(overdueReq, "id", overdueReqId);
    when(reqRepo.findByTutorIdOrderByCreatedAtDesc(tutorId)).thenReturn(List.of(overdueReq));
    when(profile.findLearningNeeds(any())).thenReturn(Map.of());
    assertThrows(ResourceConflictException.class, () -> service.tutorInbox(tutor));
    verify(expiryService).expireRequest(overdueReqId, NOW);
  }

  // =========================================================================
  // 5. ParentStudentLinkService Branch Coverage
  // =========================================================================
  @Test
  void parentStudentLinkServiceCoversRevocationBranches() {
    ParentStudentLinkRepository linkRepo = mock(ParentStudentLinkRepository.class);
    ProfileFacade profile = mock(ProfileFacade.class);
    IamFacade iam = mock(IamFacade.class);
    AuditSink audit = mock(AuditSink.class);
    ConnectionLock lock = mock(ConnectionLock.class);
    ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);

    ParentStudentLinkService service = new ParentStudentLinkService(linkRepo, profile, lock, audit, events, iam, clock);

    UUID userId = UUID.randomUUID();
    UUID linkId = UUID.randomUUID();
    CurrentUser parent = mockActor(userId, "PARENT");
    CurrentUser student = mockActor(userId, "STUDENT");
    CurrentUser admin = mockActor(userId, "ADMIN");
    CurrentUser stranger = mockActor(userId, "GUEST");

    // Stranger / non-allowed role cannot revoke (403)
    assertThrows(ForbiddenOperationException.class, () -> service.revokeLink(stranger, linkId, null));

    // Link not found (404)
    when(linkRepo.findById(linkId)).thenReturn(Optional.empty());
    assertThrows(EntityNotFoundException.class, () -> service.revokeLink(student, linkId, null));

    // Link not confirmed (409)
    when(iam.findUserSummaryById(userId)).thenReturn(Optional.of(new UserSummaryView(userId, "u@test.com", "U", AccountRole.STUDENT, AccountStatus.ACTIVE)));
    ParentStudentLink link = new ParentStudentLink(UUID.randomUUID(), UUID.randomUUID(), NOW);
    link.revoke(userId, "Already revoked", NOW);
    when(linkRepo.findById(linkId)).thenReturn(Optional.of(link));
    when(linkRepo.lockById(linkId)).thenReturn(Optional.of(link));
    assertThrows(ResourceConflictException.class, () -> service.revokeLink(student, linkId, null));

    // Student is not owner of link (403)
    ParentStudentLink activeLink = new ParentStudentLink(UUID.randomUUID(), UUID.randomUUID(), NOW);
    when(linkRepo.findById(linkId)).thenReturn(Optional.of(activeLink));
    when(linkRepo.lockById(linkId)).thenReturn(Optional.of(activeLink));
    when(iam.findUserSummaryById(userId)).thenReturn(Optional.of(new UserSummaryView(userId, "u@test.com", "U", AccountRole.STUDENT, AccountStatus.ACTIVE)));
    when(profile.findStudentById(activeLink.getStudentId())).thenReturn(Optional.of(new StudentSummaryDto(activeLink.getStudentId(), UUID.randomUUID(), 4, null, null, true)));
    assertThrows(ForbiddenOperationException.class, () -> service.revokeLink(student, linkId, null));

    // Parent is not owner of link (403)
    when(iam.findUserSummaryById(userId)).thenReturn(Optional.of(new UserSummaryView(userId, "p@test.com", "P", AccountRole.PARENT, AccountStatus.ACTIVE)));
    assertThrows(ForbiddenOperationException.class, () -> service.revokeLink(parent, linkId, null));

    // Owner parent succeeds
    UUID ownerParentId = activeLink.getParentUserId();
    CurrentUser ownerParent = mockActor(ownerParentId, "PARENT");
    when(iam.findUserSummaryById(ownerParentId)).thenReturn(Optional.of(new UserSummaryView(ownerParentId, "op@test.com", "OP", AccountRole.PARENT, AccountStatus.ACTIVE)));
    ParentStudentLink activeLink2 = new ParentStudentLink(ownerParentId, activeLink.getStudentId(), NOW);
    UUID linkId2 = UUID.randomUUID();
    when(linkRepo.findById(linkId2)).thenReturn(Optional.of(activeLink2));
    when(linkRepo.lockById(linkId2)).thenReturn(Optional.of(activeLink2));
    assertDoesNotThrow(() -> service.revokeLink(ownerParent, linkId2, null));
    assertEquals("REVOKED", activeLink2.getStatus());

    // Admin without reason (400)
    when(iam.findUserSummaryById(userId)).thenReturn(Optional.of(new UserSummaryView(userId, "admin@test.com", "Admin", AccountRole.ADMIN, AccountStatus.ACTIVE)));
    assertThrows(InvalidOperationException.class, () -> service.revokeLink(admin, linkId, "   "));

    // Admin with reason succeeds
    assertDoesNotThrow(() -> service.revokeLink(admin, linkId, "Valid admin revocation reason"));
    assertEquals("REVOKED", activeLink.getStatus());
  }

  // =========================================================================
  // 6. ConnectionRequestService Detailed Sub-branch Coverage
  // =========================================================================
  @Test
  void requestServiceDetailedBranchesCovered() {
    ConnectionRequestRepository reqRepo = mock(ConnectionRequestRepository.class);
    TutoringClassRepository classRepo = mock(TutoringClassRepository.class);
    ProfileFacade profile = mock(ProfileFacade.class);
    IamFacade iam = mock(IamFacade.class);
    ConnectionLock lock = mock(ConnectionLock.class);
    NotificationSink notifications = mock(NotificationSink.class);
    ConnectionFacade facade = mock(ConnectionFacade.class);
    ConnectionRequestInput input = new ConnectionRequestInput("legacy-compatible");
    ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    ConnectionExpiryService expiryService = mock(ConnectionExpiryService.class);

    ConnectionRequestService service = new ConnectionRequestService(reqRepo, classRepo, profile, iam, lock, notifications, facade, input, events, expiryService, new vn.edufit.connection.application.policy.SlotConflictDetector(), ConnectionTestDefaults.noScheduleConflicts(), ConnectionTestDefaults.noScheduledSessions(), clock);

    UUID userId = UUID.randomUUID();
    UUID studentId = UUID.randomUUID();
    UUID tutorId = UUID.randomUUID();
    UUID needId = UUID.randomUUID();
    CurrentUser student = mockActor(userId, "STUDENT");

    // Role check violation
    CurrentUser guest = mockActor(userId, "GUEST");
    assertThrows(ForbiddenOperationException.class, () -> service.request(guest, needId, tutorId, List.of("Mon 18:00"), null));

    // Inactive actor
    when(iam.findUserSummaryById(userId)).thenReturn(Optional.of(new UserSummaryView(userId, "s@test.com", "S", AccountRole.STUDENT, AccountStatus.DEACTIVATED)));
    assertThrows(ForbiddenOperationException.class, () -> service.request(student, needId, tutorId, List.of("Mon 18:00"), null));

    // Goal not found
    when(iam.findUserSummaryById(userId)).thenReturn(Optional.of(new UserSummaryView(userId, "s@test.com", "S", AccountRole.STUDENT, AccountStatus.ACTIVE)));
    when(profile.findLearningNeed(needId)).thenReturn(Optional.empty());
    assertThrows(EntityNotFoundException.class, () -> service.request(student, needId, tutorId, List.of("Mon 18:00"), null));

    // Goal student profile mismatch
    LearningNeedView goal = ConnectionTestFixtures.need(needId, studentId, userId, 1, 4, "ACTIVE");
    when(profile.findLearningNeed(needId)).thenReturn(Optional.of(goal));
    when(profile.findStudentById(studentId)).thenReturn(Optional.empty());
    assertThrows(ForbiddenOperationException.class, () -> service.request(student, needId, tutorId, List.of("Mon 18:00"), null));

    // Goal not ACTIVE
    when(profile.findStudentById(studentId)).thenReturn(Optional.of(new StudentSummaryDto(studentId, userId, 4, null, null, true)));
    LearningNeedView closedGoal = ConnectionTestFixtures.need(needId, studentId, userId, 1, 4, "CLOSED");
    when(profile.findLearningNeed(needId)).thenReturn(Optional.of(closedGoal));
    assertThrows(InvalidOperationException.class, () -> service.request(student, needId, tutorId, List.of("Mon 18:00"), null));

    // Tutor not found
    when(profile.findLearningNeed(needId)).thenReturn(Optional.of(goal));
    when(profile.findTutorById(tutorId)).thenReturn(Optional.empty());
    assertThrows(EntityNotFoundException.class, () -> service.request(student, needId, tutorId, List.of("Mon 18:00"), null));

    // Tutor not verified
    UUID tutorUserId = UUID.randomUUID();
    TutorSummaryDto unverifiedTutor = new TutorSummaryDto(tutorId, tutorUserId, "Tutor", null, null, null, null, false, null, null, null, null, "UNVERIFIED", NOW, null, 0);
    when(profile.findTutorById(tutorId)).thenReturn(Optional.of(unverifiedTutor));
    assertThrows(InvalidOperationException.class, () -> service.request(student, needId, tutorId, List.of("Mon 18:00"), null));

    // Tutor doesn't teach subject/level
    TutorSummaryDto verifiedTutor = new TutorSummaryDto(tutorId, tutorUserId, "Tutor", null, null, null, null, false, null, null, null, null, "VERIFIED", NOW, null, 0);
    when(profile.findTutorById(tutorId)).thenReturn(Optional.of(verifiedTutor));
    when(iam.findUserSummaryById(tutorUserId)).thenReturn(Optional.of(new UserSummaryView(tutorUserId, "t@test.com", "T", AccountRole.TUTOR, AccountStatus.ACTIVE)));
    when(profile.findTutorOfferings(tutorId)).thenReturn(java.util.List.of());
    assertThrows(InvalidOperationException.class, () -> service.request(student, needId, tutorId, List.of("Mon 18:00"), null));

    // Successful request creation with expiry check on overdue pending
    when(profile.findTutorOfferings(tutorId)).thenReturn(ConnectionTestFixtures.offerings(1, 4));
    UUID overdueId = UUID.randomUUID();
    ConnectionRequest overdue = new ConnectionRequest(studentId, UUID.randomUUID(), needId, userId, List.of("Mon 18:00"), null, NOW.minus(ConnectionTransitions.REQUEST_TTL).minusSeconds(10));
    org.springframework.test.util.ReflectionTestUtils.setField(overdue, "id", overdueId);
    when(reqRepo.findByStudentIdAndStatus(studentId, "PENDING")).thenReturn(List.of(overdue));
    when(reqRepo.save(any())).thenAnswer(inv -> {
      ConnectionRequest req = inv.getArgument(0);
      org.springframework.test.util.ReflectionTestUtils.setField(req, "id", UUID.randomUUID());
      return req;
    });

    TransitionResult<ConnectionRequestResult> res = service.request(student, needId, tutorId, List.of("Mon 18:00"), null);
    assertInstanceOf(TransitionResult.Success.class, res);
    verify(expiryService).expireRequest(overdueId, NOW);
  }

  // =========================================================================
  // 7. ConnectionExpiryService Sub-branch Coverage
  // =========================================================================
  @Test
  void expiryServiceCoversFailureAndLockingBranches() {
    LinkInvitationRepository invRepo = mock(LinkInvitationRepository.class);
    ConnectionRequestRepository reqRepo = mock(ConnectionRequestRepository.class);
    ProfileFacade profile = mock(ProfileFacade.class);
    ConnectionLock lock = mock(ConnectionLock.class);
    ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);

    ConnectionExpiryService service = new ConnectionExpiryService(invRepo, reqRepo, lock, profile, events, clock);

    // Expire non-pending invitation (no-op)
    UUID invId = UUID.randomUUID();
    LinkInvitation acceptedInv = new LinkInvitation(UUID.randomUUID(), UUID.randomUUID(), NOW);
    acceptedInv.accept(NOW);
    when(invRepo.findById(invId)).thenReturn(Optional.of(acceptedInv));
    when(invRepo.lockById(invId)).thenReturn(Optional.of(acceptedInv));
    assertFalse(service.expireInvitation(invId, NOW));

    // Expire already expired request (no-op)
    UUID reqId = UUID.randomUUID();
    ConnectionRequest expReq = new ConnectionRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), List.of("Mon 18:00"), null, NOW.minusSeconds(10));
    expReq.expire(NOW);
    when(reqRepo.findById(reqId)).thenReturn(Optional.of(expReq));
    when(reqRepo.lockById(reqId)).thenReturn(Optional.of(expReq));
    assertFalse(service.expireRequest(reqId, NOW));
  }

  // =========================================================================
  // 8. Additional Paging & Role Matrix Coverage
  // =========================================================================
  @Test
  void queryServiceCoversAdditionalPagingAndAdminBranches() {
    LinkInvitationRepository invRepo = mock(LinkInvitationRepository.class);
    ParentStudentLinkRepository linkRepo = mock(ParentStudentLinkRepository.class);
    ConnectionRequestRepository reqRepo = mock(ConnectionRequestRepository.class);
    ProfileFacade profile = mock(ProfileFacade.class);
    IamFacade iam = mock(IamFacade.class);
    ConnectionFacade facade = mock(ConnectionFacade.class);
    ConnectionExpiryService expiryService = mock(ConnectionExpiryService.class);

    ConnectionQueryService service = new ConnectionQueryService(invRepo, linkRepo, reqRepo, org.mockito.Mockito.mock(vn.edufit.connection.infra.persistence.repository.TutoringClassRepository.class), profile, iam, facade, expiryService, new vn.edufit.connection.application.policy.SlotConflictDetector(), ConnectionTestDefaults.noScheduleConflicts(), ConnectionTestDefaults.noScheduledSessions(), clock);

    UUID adminId = UUID.randomUUID();
    UUID parentId = UUID.randomUUID();
    UUID studentId = UUID.randomUUID();
    UUID studentUserId = UUID.randomUUID();

    CurrentUser admin = mockActor(adminId, "ADMIN");
    CurrentUser parent = mockActor(parentId, "PARENT");
    CurrentUser student = mockActor(studentUserId, "STUDENT");

    // Admin links page: parent found
    when(iam.findUserSummaryByEmail("parent@test.com")).thenReturn(Optional.of(new UserSummaryView(parentId, "parent@test.com", "Parent", AccountRole.PARENT, AccountStatus.ACTIVE)));
    ParentStudentLink link1 = new ParentStudentLink(parentId, studentId, NOW);
    when(linkRepo.findByParentUserIdAndStatusConfirmed(eq(parentId), any())).thenReturn(new PageImpl<>(List.of(link1), PageRequest.of(0, 20), 1));
    var adminRes1 = service.adminLinksPage(admin, "parent@test.com", 0, 20);
    assertEquals(1, adminRes1.items().size());

    // Admin links page: student found
    when(iam.findUserSummaryByEmail("student@test.com")).thenReturn(Optional.of(new UserSummaryView(studentUserId, "student@test.com", "Student", AccountRole.STUDENT, AccountStatus.ACTIVE)));
    when(profile.findStudentByUserId(studentUserId)).thenReturn(Optional.of(new StudentSummaryDto(studentId, studentUserId, 5, null, null, true)));
    when(linkRepo.findByStudentIdAndStatusConfirmed(eq(studentId), any())).thenReturn(new PageImpl<>(List.of(link1), PageRequest.of(0, 20), 1));
    var adminRes2 = service.adminLinksPage(admin, "student@test.com", 0, 20);
    assertEquals(1, adminRes2.items().size());

    // invitationsPage: with expired invitation
    UUID inviterId = UUID.randomUUID();
    LinkInvitation overdueInv = new LinkInvitation(inviterId, studentUserId, NOW.minus(ConnectionTransitions.INVITATION_TTL).minusSeconds(10));
    UUID overdueInvId = UUID.randomUUID();
    org.springframework.test.util.ReflectionTestUtils.setField(overdueInv, "id", overdueInvId);
    when(invRepo.findScopedInvitations(eq(studentUserId), any())).thenReturn(new PageImpl<>(List.of(overdueInv), PageRequest.of(0, 20), 1));
    when(iam.findUserSummariesByIds(any())).thenReturn(Map.of(inviterId, new UserSummaryView(inviterId, "p@test.com", "P", AccountRole.PARENT, AccountStatus.ACTIVE)));
    var invPage = service.invitationsPage(student, 0, 20);
    assertEquals(1, invPage.items().size());
    verify(expiryService).expireInvitation(overdueInvId, NOW);

    // invitationsPage: empty content
    when(invRepo.findScopedInvitations(eq(parentId), any())).thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));
    assertTrue(service.invitationsPage(parent, 0, 20).items().isEmpty());

    // linksPage: student and parent
    when(profile.getStudentIdByUserId(studentUserId)).thenReturn(studentId);
    when(linkRepo.findByStudentIdAndStatusConfirmed(eq(studentId), any())).thenReturn(new PageImpl<>(List.of(link1), PageRequest.of(0, 20), 1));
    assertEquals(1, service.linksPage(student, 0, 20).items().size());

    when(linkRepo.findByParentUserIdAndStatusConfirmed(eq(parentId), any())).thenReturn(new PageImpl<>(List.of(link1), PageRequest.of(0, 20), 1));
    assertEquals(1, service.linksPage(parent, 0, 20).items().size());

    // requestsPage: student and parent
    ConnectionRequest req1 = new ConnectionRequest(studentId, UUID.randomUUID(), UUID.randomUUID(), studentUserId, List.of("Mon 18:00"), null, NOW);
    org.springframework.test.util.ReflectionTestUtils.setField(req1, "id", UUID.randomUUID());
    when(profile.findStudentById(studentId)).thenReturn(Optional.of(new StudentSummaryDto(studentId, studentUserId, 5, null, null, true)));
    when(facade.hasConfirmedParentLink(parentId, studentId)).thenReturn(true);
    when(reqRepo.findByStudentIdScoped(eq(studentId), any())).thenReturn(new PageImpl<>(List.of(req1), PageRequest.of(0, 20), 1));

    assertEquals(1, service.requestsPage(student, null, 0, 20).items().size());
    assertEquals(1, service.requestsPage(parent, studentId, 0, 20).items().size());

    // tutorInboxPage: empty & non-empty
    UUID tutorUserId = UUID.randomUUID();
    UUID tutorId = UUID.randomUUID();
    CurrentUser tutor = mockActor(tutorUserId, "TUTOR");
    when(profile.getTutorIdByUserId(tutorUserId)).thenReturn(tutorId);
    when(reqRepo.findByTutorIdScoped(eq(tutorId), any())).thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));
    assertTrue(service.tutorInboxPage(tutor, 0, 20).items().isEmpty());

    when(reqRepo.findByTutorIdScoped(eq(tutorId), any())).thenReturn(new PageImpl<>(List.of(req1), PageRequest.of(0, 20), 1));
    when(profile.findLearningNeeds(any())).thenReturn(Map.of(req1.getNeedId(), ConnectionTestFixtures.need(req1.getNeedId(), studentId, studentUserId, 1, 5, "ACTIVE")));
    when(iam.findUserSummariesByIds(any())).thenReturn(Map.of(studentUserId, new UserSummaryView(studentUserId, "s@test.com", "S", AccountRole.STUDENT, AccountStatus.ACTIVE)));
    assertEquals(1, service.tutorInboxPage(tutor, 0, 20).items().size());
  }

  // =========================================================================
  // 9. Additional Fallback & Action Branches
  // =========================================================================
  @Test
  void linkInvitationServiceDelegatesExpiryAndCoversWithdrawBranches() {
    LinkInvitationRepository invRepo = mock(LinkInvitationRepository.class);
    ParentStudentLinkRepository linkRepo = mock(ParentStudentLinkRepository.class);
    IamFacade iam = mock(IamFacade.class);
    ProfileFacade profile = mock(ProfileFacade.class);
    ConnectionLock lock = mock(ConnectionLock.class);
    InvitationQuotaService quotaService = mock(InvitationQuotaService.class);
    ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);

    ConnectionExpiryService expiryService = mock(ConnectionExpiryService.class);
    LinkInvitationService service = new LinkInvitationService(invRepo, linkRepo, iam, profile, lock, quotaService, events, expiryService, clock);

    UUID inviter = UUID.randomUUID();
    UUID invited = UUID.randomUUID();
    CurrentUser actor = mockActor(inviter, "PARENT");
    when(iam.findUserSummaryById(inviter)).thenReturn(Optional.of(new UserSummaryView(inviter, "p@test.com", "Parent", AccountRole.PARENT, AccountStatus.ACTIVE)));

    // invite: lời mời quá hạn trong vòng lặp phải ủy quyền cho ConnectionExpiryService
    when(quotaService.consume(inviter)).thenReturn(new InvitationQuotaService.Consumption(true, NOW));
    when(iam.findUserSummaryByEmail("target@test.com")).thenReturn(Optional.of(new UserSummaryView(invited, "target@test.com", "Target", AccountRole.STUDENT, AccountStatus.ACTIVE)));
    LinkInvitation overdueInv = new LinkInvitation(inviter, invited, NOW.minus(ConnectionTransitions.INVITATION_TTL).minusSeconds(10));
    when(invRepo.findPendingPair(inviter, invited)).thenReturn(List.of(overdueInv));
    // Mô phỏng hiệu ứng lên DB của ConnectionExpiryService để lần truy vấn lại không còn lời mời PENDING
    when(expiryService.expireInvitation(overdueInv.getId(), NOW)).thenAnswer(call -> {
      overdueInv.expire(NOW);
      return true;
    });
    UUID studentId = UUID.randomUUID();
    when(profile.findStudentByUserId(invited)).thenReturn(Optional.of(new StudentSummaryDto(studentId, invited, 4, null, null, true)));
    when(linkRepo.existsByParentUserIdAndStudentIdAndStatus(inviter, studentId, "CONFIRMED")).thenReturn(false);
    when(invRepo.save(any())).thenAnswer(call -> {
      LinkInvitation inv = call.getArgument(0);
      org.springframework.test.util.ReflectionTestUtils.setField(inv, "id", UUID.randomUUID());
      return inv;
    });
    assertTrue(service.invite(actor, "target@test.com"));
    verify(expiryService).expireInvitation(overdueInv.getId(), NOW);

    // withdrawInvitation branches
    UUID invId = UUID.randomUUID();
    when(invRepo.findById(invId)).thenReturn(Optional.empty());
    assertThrows(EntityNotFoundException.class, () -> service.withdrawInvitation(actor, invId));

    LinkInvitation validInv = new LinkInvitation(inviter, invited, NOW);
    org.springframework.test.util.ReflectionTestUtils.setField(validInv, "id", invId);
    when(invRepo.findById(invId)).thenReturn(Optional.of(validInv));
    when(invRepo.lockById(invId)).thenReturn(Optional.of(validInv));

    UUID strangerId = UUID.randomUUID();
    CurrentUser stranger = mockActor(strangerId, "PARENT");
    when(iam.findUserSummaryById(strangerId)).thenReturn(Optional.of(new UserSummaryView(strangerId, "s@test.com", "S", AccountRole.PARENT, AccountStatus.ACTIVE)));
    assertThrows(ForbiddenOperationException.class, () -> service.withdrawInvitation(stranger, invId));

    // Overdue withdraw returns EXPIRED conflict
    LinkInvitation overdueWithdraw = new LinkInvitation(inviter, invited, NOW.minus(ConnectionTransitions.INVITATION_TTL).minusSeconds(10));
    org.springframework.test.util.ReflectionTestUtils.setField(overdueWithdraw, "id", invId);
    when(invRepo.findById(invId)).thenReturn(Optional.of(overdueWithdraw));
    when(invRepo.lockById(invId)).thenReturn(Optional.of(overdueWithdraw));
    var resExp = service.withdrawInvitation(actor, invId);
    assertInstanceOf(TransitionResult.Conflict.class, resExp);

    // Non-pending withdraw returns status conflict
    LinkInvitation nonPending = new LinkInvitation(inviter, invited, NOW);
    nonPending.accept(NOW);
    org.springframework.test.util.ReflectionTestUtils.setField(nonPending, "id", invId);
    when(invRepo.findById(invId)).thenReturn(Optional.of(nonPending));
    when(invRepo.lockById(invId)).thenReturn(Optional.of(nonPending));
    var resNonPending = service.withdrawInvitation(actor, invId);
    assertInstanceOf(TransitionResult.Conflict.class, resNonPending);

    // Valid withdraw
    LinkInvitation withdrawable = new LinkInvitation(inviter, invited, NOW);
    org.springframework.test.util.ReflectionTestUtils.setField(withdrawable, "id", invId);
    when(invRepo.findById(invId)).thenReturn(Optional.of(withdrawable));
    when(invRepo.lockById(invId)).thenReturn(Optional.of(withdrawable));
    var resOk = service.withdrawInvitation(actor, invId);
    assertInstanceOf(TransitionResult.Success.class, resOk);
    assertEquals("WITHDRAWN", withdrawable.getStatus());
  }

  @Test
  void requestServiceDelegatesExpiryAndCoversResponseBranches() {
    ConnectionRequestRepository reqRepo = mock(ConnectionRequestRepository.class);
    TutoringClassRepository classRepo = mock(TutoringClassRepository.class);
    ProfileFacade profile = mock(ProfileFacade.class);
    IamFacade iam = mock(IamFacade.class);
    ConnectionLock lock = mock(ConnectionLock.class);
    NotificationSink notifications = mock(NotificationSink.class);
    ConnectionFacade facade = mock(ConnectionFacade.class);
    ConnectionRequestInput input = new ConnectionRequestInput("legacy-compatible");
    ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);

    ConnectionExpiryService expiryService = mock(ConnectionExpiryService.class);
    ConnectionRequestService service = new ConnectionRequestService(reqRepo, classRepo, profile, iam, lock, notifications, facade, input, events, expiryService, new vn.edufit.connection.application.policy.SlotConflictDetector(), ConnectionTestDefaults.noScheduleConflicts(), ConnectionTestDefaults.noScheduledSessions(), clock);

    UUID tutorUserId = UUID.randomUUID();
    UUID tutorId = UUID.randomUUID();
    UUID studentId = UUID.randomUUID();
    UUID studentUserId = UUID.randomUUID();
    UUID reqId = UUID.randomUUID();
    UUID needId = UUID.randomUUID();

    CurrentUser tutor = mockActor(tutorUserId, "TUTOR");
    when(iam.findUserSummaryById(tutorUserId)).thenReturn(Optional.of(new UserSummaryView(tutorUserId, "t@test.com", "T", AccountRole.TUTOR, AccountStatus.ACTIVE)));
    when(profile.getTutorIdByUserId(tutorUserId)).thenReturn(tutorId);

    // respondRequest: reject with reason > 300 chars
    ConnectionRequest req = new ConnectionRequest(studentId, tutorId, needId, studentUserId, List.of("Mon 18:00"), null, NOW);
    org.springframework.test.util.ReflectionTestUtils.setField(req, "id", reqId);
    when(reqRepo.findById(reqId)).thenReturn(Optional.of(req));
    when(reqRepo.lockById(reqId)).thenReturn(Optional.of(req));
    when(profile.findTutorById(tutorId)).thenReturn(Optional.of(new TutorSummaryDto(tutorId, tutorUserId, "Tutor", null, null, null, null, false, null, null, null, null, "VERIFIED", NOW, null, 0)));
    when(profile.findStudentById(studentId)).thenReturn(Optional.of(new StudentSummaryDto(studentId, studentUserId, 4, null, null, true)));

    assertThrows(InvalidOperationException.class, () -> service.respondRequest(tutor, reqId, false, "a".repeat(301)));

    // respondRequest: reject valid
    var rejectRes = service.respondRequest(tutor, reqId, false, "Busy");
    assertInstanceOf(TransitionResult.Success.class, rejectRes);
    assertEquals("REJECTED", req.getStatus());

    // respondRequest: yêu cầu quá hạn phải ủy quyền cho ConnectionExpiryService (đường production)
    ConnectionRequest overdueReq = new ConnectionRequest(studentId, tutorId, needId, studentUserId, List.of("Mon 18:00"), null, NOW.minus(ConnectionTransitions.REQUEST_TTL).minusSeconds(10));
    org.springframework.test.util.ReflectionTestUtils.setField(overdueReq, "id", reqId);
    when(reqRepo.findById(reqId)).thenReturn(Optional.of(overdueReq));
    when(reqRepo.lockById(reqId)).thenReturn(Optional.of(overdueReq));
    var overdueRes = service.respondRequest(tutor, reqId, true, null);
    assertInstanceOf(TransitionResult.Conflict.class, overdueRes);
    verify(expiryService).expireRequest(reqId, NOW);
  }
}

