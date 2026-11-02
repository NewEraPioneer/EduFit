package vn.edufit.connection;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.edufit.connection.api.ConnectionFacade;
import vn.edufit.connection.application.service.ConnectionQueryService;
import vn.edufit.connection.infra.persistence.entity.*;
import vn.edufit.connection.infra.persistence.repository.*;
import vn.edufit.iam.api.*;
import vn.edufit.iam.api.dto.UserSummaryView;
import vn.edufit.profile.api.ProfileFacade;
import vn.edufit.profile.api.dto.*;
import vn.edufit.shared.auth.CurrentUser;
import vn.edufit.shared.exception.*;

@ExtendWith(MockitoExtension.class)
class ConnectionQueryServiceTest {
  @Mock LinkInvitationRepository invitations;
  @Mock ParentStudentLinkRepository links;
  @Mock ConnectionRequestRepository requests;
  @Mock TutoringClassRepository classes;
  @Mock ProfileFacade profile;
  @Mock IamFacade iam;
  @Mock ConnectionFacade facade;
  ConnectionQueryService service;

  @BeforeEach void setup() {
    service = new ConnectionQueryService(invitations, links, requests, classes, profile, iam, facade, ConnectionTestDefaults.expiryMock(), new vn.edufit.connection.application.policy.SlotConflictDetector(), ConnectionTestDefaults.noScheduleConflicts(), ConnectionTestDefaults.noScheduledSessions(), Clock.systemUTC());
    lenient().when(profile.findTutorById(any())).thenAnswer(invocation ->
        java.util.Optional.of(ConnectionTestFixtures.tutorSummary(invocation.getArgument(0))));
    lenient().when(profile.findVerifiedTutorDetail(any())).thenAnswer(invocation ->
        java.util.Optional.of(ConnectionTestFixtures.tutorProfile(invocation.getArgument(0))));
  }

  private CurrentUser actor(UUID id, String role) {
    CurrentUser actor = mock(CurrentUser.class);
    lenient().when(actor.getUserId()).thenReturn(id);
    when(actor.hasRole(anyString())).thenAnswer(i -> role.equals(i.getArgument(0)));
    return actor;
  }

  @ParameterizedTest @ValueSource(longs = {-1, 0, 1})
  void invitationExpiryUsesInjectedClockAtDeadline(long offset) {
    Instant deadline = Instant.parse("2026-10-25T00:00:00Z");
    UUID user = UUID.randomUUID();
    var invitation = new LinkInvitation(user, UUID.randomUUID(), deadline.minusSeconds(14 * 86400));
    Clock clock = mock(Clock.class);
    when(clock.instant()).thenReturn(deadline.plusNanos(offset));
    service = new ConnectionQueryService(invitations, links, requests, org.mockito.Mockito.mock(vn.edufit.connection.infra.persistence.repository.TutoringClassRepository.class), profile, iam, facade, ConnectionTestDefaults.expiryMock(), new vn.edufit.connection.application.policy.SlotConflictDetector(), ConnectionTestDefaults.noScheduleConflicts(), ConnectionTestDefaults.noScheduledSessions(), clock);
    when(invitations.findByInviterUserIdOrInvitedUserIdOrderByCreatedAtDesc(user, user))
        .thenReturn(List.of(invitation));
    service.invitations(actor(user, "PARENT"));
    assertEquals(offset < 0 ? "PENDING" : "EXPIRED", invitation.getStatus());
    verify(clock, times(1)).instant();
  }

  @Test void studentListIgnoresForeignScopeAndReturnsOnlyOwnedStudent() {
    UUID user = UUID.randomUUID(), own = UUID.randomUUID(), foreign = UUID.randomUUID();
    when(profile.getStudentIdByUserId(user)).thenReturn(own);
    when(profile.findStudentById(own)).thenReturn(Optional.of(new StudentSummaryDto(own, user, 4, null, null, true)));
    assertTrue(service.requests(actor(user, "STUDENT"), foreign).isEmpty());
    verify(requests).findByStudentIdOrderByCreatedAtDesc(own);
    verify(requests, never()).findByStudentIdOrderByCreatedAtDesc(foreign);
  }

  @Test void revokedParentCannotReadRequests() {
    UUID parent = UUID.randomUUID(), student = UUID.randomUUID();
    when(facade.hasConfirmedParentLink(parent, student)).thenReturn(false);
    assertThrows(ForbiddenOperationException.class, () -> service.requests(actor(parent, "PARENT"), student));
    verifyNoInteractions(requests);
  }

  @Test void confirmedParentReadsOnlyRequestedStudent() {
    UUID parent = UUID.randomUUID(), student = UUID.randomUUID();
    when(facade.hasConfirmedParentLink(parent, student)).thenReturn(true);
    assertTrue(service.requests(actor(parent, "PARENT"), student).isEmpty());
    verify(requests).findByStudentIdOrderByCreatedAtDesc(student);
  }

  @Test void parentMustSupplyStudentScope() {
    assertThrows(InvalidOperationException.class, () -> service.requests(actor(UUID.randomUUID(), "PARENT"), null));
    verifyNoInteractions(requests, facade);
  }

  @ParameterizedTest @ValueSource(strings = {"ADMIN", "TUTOR"})
  void learnerQueryRejectsOtherRoles(String role) {
    assertThrows(ForbiddenOperationException.class, () -> service.requests(actor(UUID.randomUUID(), role), UUID.randomUUID()));
    verifyNoInteractions(requests, profile, facade);
  }

  @Test void tutorInboxRequiresTutorRole() {
    assertThrows(ForbiddenOperationException.class, () -> service.tutorInbox(actor(UUID.randomUUID(), "PARENT")));
    verifyNoInteractions(requests, profile, iam);
  }

  @ParameterizedTest @ValueSource(strings = {"PARENT", "STUDENT"})
  void tutorInboxScopesExpiresAndMapsBatchMetadata(String sender) {
    UUID user = UUID.randomUUID(), tutor = UUID.randomUUID(), student = UUID.randomUUID(), goal = UUID.randomUUID(), sentBy = UUID.randomUUID();
    var request = new ConnectionRequest(student, tutor, goal, sentBy, List.of("Tue 19:00"), "hello", Instant.now().minusSeconds(15 * 86400));
    when(profile.getTutorIdByUserId(user)).thenReturn(tutor);
    when(requests.findByTutorIdOrderByCreatedAtDesc(tutor)).thenReturn(List.of(request));
    when(profile.findLearningNeeds(List.of(goal))).thenReturn(Map.of(
        goal, ConnectionTestFixtures.need(goal, student, UUID.randomUUID(), 1, 4, "ACTIVE")));
    when(iam.findUserSummariesByIds(List.of(sentBy))).thenReturn(Map.of(
        sentBy, new UserSummaryView(sentBy, "hidden@test.invalid", "Hidden name", AccountRole.valueOf(sender), AccountStatus.ACTIVE)));

    var result = service.tutorInbox(actor(user, "TUTOR"));
    assertEquals(1, result.size());
    var item = result.getFirst();
    assertEquals("EXPIRED", item.status());
    assertEquals("EXPIRED", request.getStatus());
    assertEquals("GRADE10_EXAM", item.need().goalTagCode());
    assertEquals(1, item.need().subjectId());
    assertEquals(4, item.need().educationLevelId());
    assertEquals(List.of("Tue 19:00"), item.desiredSlots());
    assertEquals("hello", item.message());
    assertEquals(sender, item.senderRole());
    // Gói dạy của gia sư khớp lớp, mục tiêu, tỉnh luyện đề, ngân sách và lịch của nhu cầu
    assertTrue(item.fitChecks().stream().allMatch(vn.edufit.profile.api.fit.FitCheck::matched), item.fitChecks().toString());
    verify(requests).findByTutorIdOrderByCreatedAtDesc(tutor);
    verify(requests, never()).findByStudentIdOrderByCreatedAtDesc(any());
  }

  @Test void tutorInboxMissingGoalThrowsConflict409() {
    UUID user = UUID.randomUUID(), tutor = UUID.randomUUID(), student = UUID.randomUUID(), goal = UUID.randomUUID(), sentBy = UUID.randomUUID();
    var request = new ConnectionRequest(student, tutor, goal, sentBy, List.of("Tue 19:00"), "hello", Instant.now());
    when(profile.getTutorIdByUserId(user)).thenReturn(tutor);
    when(requests.findByTutorIdOrderByCreatedAtDesc(tutor)).thenReturn(List.of(request));
    when(profile.findLearningNeeds(List.of(goal))).thenReturn(Map.of());
    when(iam.findUserSummariesByIds(List.of(sentBy))).thenReturn(Map.of(
        sentBy, new UserSummaryView(sentBy, "h@t.i", "Name", AccountRole.STUDENT, AccountStatus.ACTIVE)));

    var ex = assertThrows(ResourceConflictException.class, () -> service.tutorInbox(actor(user, "TUTOR")));
    assertEquals(ErrorCode.CONFLICT_DETECTED, ex.getErrorCode());
  }

  @Test void tutorInboxMissingSenderThrowsConflict409() {
    UUID user = UUID.randomUUID(), tutor = UUID.randomUUID(), student = UUID.randomUUID(), goal = UUID.randomUUID(), sentBy = UUID.randomUUID();
    var request = new ConnectionRequest(student, tutor, goal, sentBy, List.of("Tue 19:00"), "hello", Instant.now());
    when(profile.getTutorIdByUserId(user)).thenReturn(tutor);
    when(requests.findByTutorIdOrderByCreatedAtDesc(tutor)).thenReturn(List.of(request));
    when(profile.findLearningNeeds(List.of(goal))).thenReturn(Map.of(
        goal, ConnectionTestFixtures.need(goal, student, UUID.randomUUID(), 1, 4, "ACTIVE")));
    when(iam.findUserSummariesByIds(List.of(sentBy))).thenReturn(Map.of());

    var ex = assertThrows(ResourceConflictException.class, () -> service.tutorInbox(actor(user, "TUTOR")));
    assertEquals(ErrorCode.CONFLICT_DETECTED, ex.getErrorCode());
  }

  @Test void tutorInboxBatchesDistinctIdsWithoutNPlusOne() {
    UUID user = UUID.randomUUID(), tutor = UUID.randomUUID(), student = UUID.randomUUID();
    UUID goal1 = UUID.randomUUID(), goal2 = UUID.randomUUID();
    UUID sender1 = UUID.randomUUID(), sender2 = UUID.randomUUID();
    var r1 = new ConnectionRequest(student, tutor, goal1, sender1, List.of("Tue 19:00"), null, Instant.now());
    var r2 = new ConnectionRequest(student, tutor, goal1, sender2, List.of("Wed 19:00"), null, Instant.now());
    var r3 = new ConnectionRequest(student, tutor, goal2, sender1, List.of("Thu 19:00"), null, Instant.now());

    when(profile.getTutorIdByUserId(user)).thenReturn(tutor);
    when(requests.findByTutorIdOrderByCreatedAtDesc(tutor)).thenReturn(List.of(r1, r2, r3));
    when(profile.findLearningNeeds(anyList())).thenReturn(Map.of(
        goal1, ConnectionTestFixtures.need(goal1, student, UUID.randomUUID(), 1, 4, "ACTIVE"),
        goal2, ConnectionTestFixtures.need(goal2, student, UUID.randomUUID(), 2, 4, "ACTIVE")));
    when(iam.findUserSummariesByIds(anyList())).thenReturn(Map.of(
        sender1, new UserSummaryView(sender1, "s1@test.invalid", "Sender One", AccountRole.STUDENT, AccountStatus.ACTIVE),
        sender2, new UserSummaryView(sender2, "s2@test.invalid", "Sender Two", AccountRole.PARENT, AccountStatus.ACTIVE)));

    var result = service.tutorInbox(actor(user, "TUTOR"));
    assertEquals(3, result.size());
    verify(profile, times(1)).findLearningNeeds(argThat(list -> list.size() == 2 && list.contains(goal1) && list.contains(goal2)));
    verify(iam, times(1)).findUserSummariesByIds(argThat(list -> list.size() == 2 && list.contains(sender1) && list.contains(sender2)));
  }

  @Test void invitationsHydrateInviterNameAndRoleViaBatch() {
    UUID user = UUID.randomUUID(), inviter1 = UUID.randomUUID(), inviter2 = UUID.randomUUID();
    var inv1 = new LinkInvitation(inviter1, user, Instant.now().minusSeconds(3600));
    var inv2 = new LinkInvitation(inviter2, user, Instant.now().minusSeconds(7200));
    when(invitations.findByInviterUserIdOrInvitedUserIdOrderByCreatedAtDesc(user, user)).thenReturn(List.of(inv1, inv2));
    when(iam.findUserSummariesByIds(anyList())).thenReturn(Map.of(
        inviter1, new UserSummaryView(inviter1, "p@test.invalid", "Parent One", AccountRole.PARENT, AccountStatus.ACTIVE)));
    // inviter2 is deleted/missing in IAM

    var list = service.invitations(actor(user, "STUDENT"));
    assertEquals(2, list.size());
    assertEquals("Parent One", list.get(0).inviterName());
    assertEquals("PARENT", list.get(0).inviterRole());
    assertNull(list.get(1).inviterName());
    assertNull(list.get(1).inviterRole());
    verify(iam, times(1)).findUserSummariesByIds(argThat(ids -> ids.size() == 2 && ids.contains(inviter1) && ids.contains(inviter2)));
  }

  @Test void invitationsUseOnlyActorAsBothParticipants() {
    UUID user = UUID.randomUUID();
    var invitation = new LinkInvitation(user, UUID.randomUUID(), Instant.now().minusSeconds(15 * 86400));
    when(invitations.findByInviterUserIdOrInvitedUserIdOrderByCreatedAtDesc(user, user)).thenReturn(List.of(invitation));
    when(iam.findUserSummariesByIds(anyList())).thenReturn(Map.of(
        user, new UserSummaryView(user, "user@test.invalid", "User Name", AccountRole.PARENT, AccountStatus.ACTIVE)));
    var res = service.invitations(actor(user, "PARENT"));
    assertEquals("EXPIRED", res.getFirst().status());
    assertEquals("User Name", res.getFirst().inviterName());
    assertEquals("PARENT", res.getFirst().inviterRole());
    verify(invitations).findByInviterUserIdOrInvitedUserIdOrderByCreatedAtDesc(user, user);
  }

  @ParameterizedTest @ValueSource(strings = {"PARENT", "STUDENT"})
  void linksAlwaysUseConfirmedOwnedScope(String role) {
    UUID user = UUID.randomUUID(), student = UUID.randomUUID();
    if (role.equals("STUDENT")) when(profile.getStudentIdByUserId(user)).thenReturn(student);
    assertTrue(service.links(actor(user, role)).isEmpty());
    if (role.equals("PARENT")) verify(links).findByParentUserIdAndStatus(user, "CONFIRMED");
    else verify(links).findByStudentIdAndStatus(student, "CONFIRMED");
  }

  @Test void invitationsPageValidatesBoundsAndHydratesData() {
    UUID user = UUID.randomUUID(), inviter = UUID.randomUUID();
    var inv = new LinkInvitation(inviter, user, Instant.now());
    when(invitations.findScopedInvitations(eq(user), any(org.springframework.data.domain.Pageable.class)))
        .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(inv), org.springframework.data.domain.PageRequest.of(0, 10), 25));
    when(iam.findUserSummariesByIds(List.of(inviter))).thenReturn(Map.of(
        inviter, new UserSummaryView(inviter, "p@t.i", "Parent Name", AccountRole.PARENT, AccountStatus.ACTIVE)));

    var page = service.invitationsPage(actor(user, "STUDENT"), 0, 10);
    assertEquals(1, page.items().size());
    assertEquals(0, page.page());
    assertEquals(10, page.size());
    assertEquals(25, page.totalElements());
    assertEquals(3, page.totalPages());
    assertEquals("Parent Name", page.items().getFirst().inviterName());
    assertEquals("PARENT", page.items().getFirst().inviterRole());
  }

  @Test void linksPageReturnsPagedConfirmedLinks() {
    UUID user = UUID.randomUUID(), student = UUID.randomUUID();
    var link = new ParentStudentLink(user, student, Instant.now());
    when(links.findByParentUserIdAndStatusConfirmed(eq(user), any(org.springframework.data.domain.Pageable.class)))
        .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(link), org.springframework.data.domain.PageRequest.of(0, 20), 1));

    var page = service.linksPage(actor(user, "PARENT"), 0, 20);
    assertEquals(1, page.items().size());
    assertEquals(0, page.page());
    assertEquals(20, page.size());
    assertEquals(1, page.totalElements());
    assertEquals(1, page.totalPages());
  }

  @Test void requestsPageReturnsPagedRequestsForLearner() {
    UUID user = UUID.randomUUID(), student = UUID.randomUUID(), tutor = UUID.randomUUID(), goal = UUID.randomUUID();
    when(profile.getStudentIdByUserId(user)).thenReturn(student);
    when(profile.findStudentById(student)).thenReturn(Optional.of(new StudentSummaryDto(student, user, 4, null, null, true)));
    var req = new ConnectionRequest(student, tutor, goal, user, List.of("Tue 19:00"), null, Instant.now().plusSeconds(3600));
    when(requests.findByStudentIdScoped(eq(student), any(org.springframework.data.domain.Pageable.class)))
        .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(req), org.springframework.data.domain.PageRequest.of(0, 10), 15));

    var page = service.requestsPage(actor(user, "STUDENT"), null, 0, 10);
    assertEquals(1, page.items().size());
    assertEquals(0, page.page());
    assertEquals(10, page.size());
    assertEquals(15, page.totalElements());
    assertEquals(2, page.totalPages());
  }

  @Test void tutorInboxPageReturnsPagedInboxWithMetadata() {
    UUID user = UUID.randomUUID(), tutor = UUID.randomUUID(), student = UUID.randomUUID(), goal = UUID.randomUUID(), sentBy = UUID.randomUUID();
    when(profile.getTutorIdByUserId(user)).thenReturn(tutor);
    var req = new ConnectionRequest(student, tutor, goal, sentBy, List.of("Tue 19:00"), null, Instant.now().plusSeconds(3600));
    when(requests.findByTutorIdScoped(eq(tutor), any(org.springframework.data.domain.Pageable.class)))
        .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(req), org.springframework.data.domain.PageRequest.of(0, 20), 1));
    when(profile.findLearningNeeds(List.of(goal))).thenReturn(Map.of(
        goal, ConnectionTestFixtures.need(goal, student, UUID.randomUUID(), 1, 4, "ACTIVE")));
    when(iam.findUserSummariesByIds(List.of(sentBy))).thenReturn(Map.of(
        sentBy, new UserSummaryView(sentBy, "p@t.i", "Parent", AccountRole.PARENT, AccountStatus.ACTIVE)));

    var page = service.tutorInboxPage(actor(user, "TUTOR"), 0, 20);
    assertEquals(1, page.items().size());
    assertEquals(0, page.page());
    assertEquals(20, page.size());
    assertEquals(1, page.totalElements());
    assertEquals(1, page.totalPages());
    assertEquals("PARENT", page.items().getFirst().senderRole());
    assertEquals("ACTIVE", page.items().getFirst().need().status());
  }

  @ParameterizedTest @ValueSource(ints = {-1, -10})
  void paginationRejectsNegativePage(int page) {
    assertThrows(InvalidOperationException.class, () -> service.invitationsPage(actor(UUID.randomUUID(), "PARENT"), page, 20));
  }

  @ParameterizedTest @ValueSource(ints = {0, -1, 101, 200})
  void paginationRejectsInvalidSize(int size) {
    assertThrows(InvalidOperationException.class, () -> service.invitationsPage(actor(UUID.randomUUID(), "PARENT"), 0, size));
  }

  @Test void paginationHandlesPageBeyondEndGracefully() {
    UUID user = UUID.randomUUID();
    when(invitations.findScopedInvitations(eq(user), any(org.springframework.data.domain.Pageable.class)))
        .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(), org.springframework.data.domain.PageRequest.of(5, 10), 0));

    var page = service.invitationsPage(actor(user, "PARENT"), 5, 10);
    assertTrue(page.items().isEmpty());
    assertEquals(5, page.page());
    assertEquals(10, page.size());
    assertEquals(0, page.totalElements());
    assertEquals(0, page.totalPages());
  }

  @Test void adminLinksPageRequiresAdminRole() {
    UUID user = UUID.randomUUID();
    assertThrows(ForbiddenOperationException.class, () -> service.adminLinksPage(actor(user, "PARENT"), "test@example.com", 0, 20));
    assertThrows(ForbiddenOperationException.class, () -> service.adminLinksPage(actor(user, "STUDENT"), "test@example.com", 0, 20));
    assertThrows(ForbiddenOperationException.class, () -> service.adminLinksPage(actor(user, "TUTOR"), "test@example.com", 0, 20));
  }

  @ParameterizedTest @ValueSource(strings = {"", "   "})
  @org.junit.jupiter.params.provider.NullSource
  void adminLinksPageRequiresNonBlankEmail(String email) {
    UUID admin = UUID.randomUUID();
    assertThrows(InvalidOperationException.class, () -> service.adminLinksPage(actor(admin, "ADMIN"), email, 0, 20));
  }

  @Test void adminLinksPageReturnsEmptyWhenEmailNotFound() {
    UUID admin = UUID.randomUUID();
    when(iam.findUserSummaryByEmail("missing@example.com")).thenReturn(Optional.empty());

    var page = service.adminLinksPage(actor(admin, "ADMIN"), "missing@example.com", 0, 20);
    assertTrue(page.items().isEmpty());
    assertEquals(0, page.totalElements());
  }

  @Test void adminLinksPageLookupByParentEmailReturnsConfirmedLinks() {
    UUID admin = UUID.randomUUID(), parentId = UUID.randomUUID(), studentId = UUID.randomUUID();
    when(iam.findUserSummaryByEmail("parent@example.com")).thenReturn(Optional.of(
        new vn.edufit.iam.api.dto.UserSummaryView(parentId, "parent@example.com", "Parent Name",
            vn.edufit.iam.api.AccountRole.PARENT, vn.edufit.iam.api.AccountStatus.ACTIVE)
    ));
    var link = new ParentStudentLink(parentId, studentId, Instant.now());
    when(links.findByParentUserIdAndStatusConfirmed(eq(parentId), any(org.springframework.data.domain.Pageable.class)))
        .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(link), org.springframework.data.domain.PageRequest.of(0, 20), 1));

    var page = service.adminLinksPage(actor(admin, "ADMIN"), "parent@example.com", 0, 20);
    assertEquals(1, page.items().size());
    assertEquals(parentId, page.items().getFirst().parentUserId());
    assertEquals(studentId, page.items().getFirst().studentId());
  }

  @Test void adminLinksPageLookupByStudentEmailReturnsConfirmedLinks() {
    UUID admin = UUID.randomUUID(), studentUserId = UUID.randomUUID(), studentId = UUID.randomUUID(), parentId = UUID.randomUUID();
    when(iam.findUserSummaryByEmail("student@example.com")).thenReturn(Optional.of(
        new vn.edufit.iam.api.dto.UserSummaryView(studentUserId, "student@example.com", "Student Name",
            vn.edufit.iam.api.AccountRole.STUDENT, vn.edufit.iam.api.AccountStatus.ACTIVE)
    ));
    when(profile.findStudentByUserId(studentUserId)).thenReturn(Optional.of(
        new StudentSummaryDto(studentId, studentUserId, 3, null, null, true)
    ));
    var link = new ParentStudentLink(parentId, studentId, Instant.now());
    when(links.findByStudentIdAndStatusConfirmed(eq(studentId), any(org.springframework.data.domain.Pageable.class)))
        .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(link), org.springframework.data.domain.PageRequest.of(0, 20), 1));

    var page = service.adminLinksPage(actor(admin, "ADMIN"), "student@example.com", 0, 20);
    assertEquals(1, page.items().size());
    assertEquals(parentId, page.items().getFirst().parentUserId());
    assertEquals(studentId, page.items().getFirst().studentId());
  }

  @Test void adminLinksPageLookupByOtherRoleReturnsEmptyPage() {
    UUID admin = UUID.randomUUID(), tutorUserId = UUID.randomUUID();
    when(iam.findUserSummaryByEmail("tutor@example.com")).thenReturn(Optional.of(
        new vn.edufit.iam.api.dto.UserSummaryView(tutorUserId, "tutor@example.com", "Tutor Name",
            vn.edufit.iam.api.AccountRole.TUTOR, vn.edufit.iam.api.AccountStatus.ACTIVE)
    ));

    var page = service.adminLinksPage(actor(admin, "ADMIN"), "tutor@example.com", 0, 20);
    assertTrue(page.items().isEmpty());
    assertEquals(0, page.totalElements());
  }

  @Test
  void tutorInboxHydratesPedagogicalFieldsAndFlagsSlotConflict() {
    UUID user = UUID.randomUUID(), tutor = UUID.randomUUID(), student = UUID.randomUUID(), goal = UUID.randomUUID(), sentBy = UUID.randomUUID();
    var request = new ConnectionRequest(student, tutor, goal, sentBy, List.of("Tue 19:00"), "Cần gia sư tận tâm", Instant.now().plusSeconds(86400));
    when(profile.getTutorIdByUserId(user)).thenReturn(tutor);
    when(requests.findByTutorIdOrderByCreatedAtDesc(tutor)).thenReturn(List.of(request));

    // Active class with overlapping slot
    UUID activeReqId = UUID.randomUUID();
    var activeReq = new ConnectionRequest(UUID.randomUUID(), tutor, UUID.randomUUID(), UUID.randomUUID(),
        List.of("TUESDAY 18:30-19:30"), null, Instant.now());
    org.springframework.test.util.ReflectionTestUtils.setField(activeReq, "id", activeReqId);
    var activeClass = new TutoringClass(activeReq, 200000L, Instant.now());
    when(classes.findByTutorIdAndStatus(tutor, "ACTIVE")).thenReturn(List.of(activeClass));
    when(requests.findAllById(List.of(activeReqId))).thenReturn(List.of(activeReq));

    var goalDto = ConnectionTestFixtures.needNamed(goal, student, sentBy, "Toán học", "Lớp 12");
    when(profile.findLearningNeeds(List.of(goal))).thenReturn(Map.of(goal, goalDto));
    when(iam.findUserSummariesByIds(List.of(sentBy))).thenReturn(Map.of(
        sentBy, new UserSummaryView(sentBy, "student@edufit.vn", "Nguyễn Văn Minh", AccountRole.STUDENT, AccountStatus.ACTIVE)
    ));

    var result = service.tutorInbox(actor(user, "TUTOR"));
    assertEquals(1, result.size());
    var item = result.getFirst();

    // Hộp thư hiển thị đủ nhu cầu học và gói dạy dùng để so
    assertEquals("Nguyễn Minh", item.studentDisplayName()); // BR-37: middle name masked
    assertEquals("Toán học", item.need().subjectName());
    assertEquals("Lớp 12", item.need().educationLevelName());
    assertEquals("Cầu Giấy", item.need().districtName());
    assertEquals("Toán", item.offeringName());

    // Verify slot conflict flags
    assertTrue(item.hasSlotConflict());
    assertEquals(List.of("Tue 19:00"), item.conflictingSlots());

    // Verify response mapping
    var response = vn.edufit.connection.web.response.TutorInboxResponse.of(item);
    assertEquals("Nguyễn Minh", response.studentDisplayName());
    assertTrue(response.hasSlotConflict());
    assertEquals("Cầu Giấy", response.need().districtName());
    assertEquals("Hà Nội", response.need().provinceName());
  }

  @Test
  void tutorInboxStrictlyEnforcesBR37Privacy() {
    UUID user = UUID.randomUUID(), tutor = UUID.randomUUID(), student = UUID.randomUUID(), goal = UUID.randomUUID(), sentBy = UUID.randomUUID();
    var request = new ConnectionRequest(student, tutor, goal, sentBy, List.of("WEDNESDAY 18:00-19:00"), null, Instant.now().plusSeconds(86400));
    when(profile.getTutorIdByUserId(user)).thenReturn(tutor);
    when(requests.findByTutorIdOrderByCreatedAtDesc(tutor)).thenReturn(List.of(request));
    when(classes.findByTutorIdAndStatus(tutor, "ACTIVE")).thenReturn(List.of());

    var goalDto = ConnectionTestFixtures.needNamed(goal, student, sentBy, "Vật lý", "Lớp 10");
    when(profile.findLearningNeeds(List.of(goal))).thenReturn(Map.of(goal, goalDto));
    when(iam.findUserSummariesByIds(List.of(sentBy))).thenReturn(Map.of(
        sentBy, new UserSummaryView(sentBy, "secret_email@edufit.vn", "Trần Thị Diệu Hoa", AccountRole.PARENT, AccountStatus.ACTIVE)
    ));

    var result = service.tutorInbox(actor(user, "TUTOR"));
    var item = result.getFirst();

    // Verify BR-37: full legal middle names, email, phone must not be exposed
    assertEquals("Trần Hoa", item.studentDisplayName()); // Only first and last name exposed
    assertFalse(item.studentDisplayName().contains("Thị Diệu"));
    assertFalse(item.hasSlotConflict());
    assertTrue(item.conflictingSlots().isEmpty());
  }

  @Test
  void classes_withScheduleStatusProvider_populatesScheduleStatusAndCount() {
    UUID user = UUID.randomUUID(), tutor = UUID.randomUUID(), student = UUID.randomUUID(), goal = UUID.randomUUID();
    UUID classId = UUID.randomUUID();
    var req = new ConnectionRequest(student, tutor, goal, user, List.of("MON 18:00-19:00"), null, Instant.now().plusSeconds(86400));
    var tutoringClass = new TutoringClass(req, 200000L, Instant.now());
    // Use reflection to set ID on entity if protected
    org.springframework.test.util.ReflectionTestUtils.setField(tutoringClass, "id", classId);

    when(profile.getTutorIdByUserId(user)).thenReturn(tutor);
    when(classes.findByTutorId(tutor)).thenReturn(List.of(tutoringClass));

    var goalDto = ConnectionTestFixtures.needNamed(goal, student, user, "Toán học", "Lớp 12");
    when(profile.findLearningNeeds(List.of(goal))).thenReturn(Map.of(goal, goalDto));
    when(profile.findStudentById(student)).thenReturn(Optional.empty());
    when(profile.findTutorById(tutor)).thenReturn(Optional.empty());

    var mockScheduleProvider = mock(vn.edufit.connection.api.ClassScheduleStatusProvider.class);
    when(mockScheduleProvider.getScheduleInfoBatch(List.of(classId))).thenReturn(Map.of(
        classId, new vn.edufit.connection.api.ClassScheduleStatusProvider.ClassScheduleInfo("SCHEDULED", 2, 4, 6)
    ));

    var customService = new ConnectionQueryService(invitations, links, requests, classes, profile, iam, facade, ConnectionTestDefaults.expiryMock(), new vn.edufit.connection.application.policy.SlotConflictDetector(), ConnectionTestDefaults.noScheduleConflicts(), mockScheduleProvider, Clock.systemUTC());

    var results = customService.classes(actor(user, "TUTOR"));
    assertEquals(1, results.size());
    var cls = results.getFirst();
    assertEquals("SCHEDULED", cls.scheduleStatus());
    assertEquals(2, cls.sessionCount());
    assertEquals(2, cls.completedCount());
    assertEquals(4, cls.scheduledCount());
    assertEquals(6, cls.totalCount());
  }

  @Test
  void classes_withoutScheduleStatusProvider_fallsBackToProposedAndZero() {
    UUID user = UUID.randomUUID(), tutor = UUID.randomUUID(), student = UUID.randomUUID(), goal = UUID.randomUUID();
    UUID classId = UUID.randomUUID();
    var req = new ConnectionRequest(student, tutor, goal, user, List.of("MON 18:00-19:00"), null, Instant.now().plusSeconds(86400));
    var tutoringClass = new TutoringClass(req, 200000L, Instant.now());
    org.springframework.test.util.ReflectionTestUtils.setField(tutoringClass, "id", classId);

    when(profile.getTutorIdByUserId(user)).thenReturn(tutor);
    when(classes.findByTutorId(tutor)).thenReturn(List.of(tutoringClass));

    var goalDto = ConnectionTestFixtures.needNamed(goal, student, user, "Toán học", "Lớp 12");
    when(profile.findLearningNeeds(List.of(goal))).thenReturn(Map.of(goal, goalDto));
    when(profile.findStudentById(student)).thenReturn(Optional.empty());
    when(profile.findTutorById(tutor)).thenReturn(Optional.empty());

    var results = service.classes(actor(user, "TUTOR"));
    assertEquals(1, results.size());
    var cls = results.getFirst();
    assertEquals("PROPOSED", cls.scheduleStatus());
    assertEquals(0, cls.sessionCount());
    assertEquals(0, cls.completedCount());
    assertEquals(0, cls.scheduledCount());
    assertEquals(0, cls.totalCount());
  }
}
