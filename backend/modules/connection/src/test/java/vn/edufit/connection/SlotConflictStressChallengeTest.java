package vn.edufit.connection;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import vn.edufit.connection.api.ConnectionFacade;
import vn.edufit.connection.api.TutorScheduleConflictChecker;
import vn.edufit.connection.application.dto.TransitionResult;
import vn.edufit.connection.application.policy.ConnectionRequestInput;
import vn.edufit.connection.application.policy.SlotConflictDetector;
import vn.edufit.connection.application.port.ConnectionLock;
import vn.edufit.connection.application.service.ConnectionRequestService;
import vn.edufit.connection.infra.persistence.entity.ConnectionRequest;
import vn.edufit.connection.infra.persistence.entity.TutoringClass;
import vn.edufit.connection.infra.persistence.repository.ConnectionRequestRepository;
import vn.edufit.connection.infra.persistence.repository.TutoringClassRepository;
import vn.edufit.iam.api.AccountRole;
import vn.edufit.iam.api.AccountStatus;
import vn.edufit.iam.api.IamFacade;
import vn.edufit.iam.api.dto.UserSummaryView;
import vn.edufit.profile.api.ProfileFacade;
import vn.edufit.profile.api.dto.LearningNeedView;
import vn.edufit.profile.api.dto.StudentSummaryDto;
import vn.edufit.profile.api.dto.TutorSummaryDto;
import vn.edufit.shared.auth.CurrentUser;
import vn.edufit.shared.exception.ErrorCode;
import vn.edufit.shared.exception.ResourceConflictException;

/**
 * Adversarial Empirical Challenge Suite for SlotConflictDetector
 * and Gatekeeper Dual Source Conflict Check.
 */
class SlotConflictStressChallengeTest {

  private SlotConflictDetector detector;

  @BeforeEach
  void setUp() {
    detector = new SlotConflictDetector();
  }

  // =========================================================================
  // SECTION 1: Exact Boundary Overlaps & Edge-Case Intervals
  // =========================================================================
  @Nested
  @DisplayName("1. Boundary Overlaps and Interval Arithmetic")
  class BoundaryOverlapTests {

    @Test
    @DisplayName("Adjacent boundary: 08:00-10:00 vs 10:00-12:00 must NOT conflict")
    void adjacentEndMatchesStartDoesNotConflict() {
      List<String> cand = List.of("MONDAY 08:00-10:00");
      List<String> exist = List.of("MONDAY 10:00-12:00");

      assertFalse(detector.hasConflict(cand, exist), "08:00-10:00 adjacent to 10:00-12:00 must not conflict");
      assertTrue(detector.findConflictingSlots(cand, exist).isEmpty());
    }

    @Test
    @DisplayName("Adjacent boundary reverse: 10:00-12:00 vs 08:00-10:00 must NOT conflict")
    void adjacentStartMatchesEndDoesNotConflict() {
      List<String> cand = List.of("MONDAY 10:00-12:00");
      List<String> exist = List.of("MONDAY 08:00-10:00");

      assertFalse(detector.hasConflict(cand, exist), "10:00-12:00 adjacent to 08:00-10:00 must not conflict");
      assertTrue(detector.findConflictingSlots(cand, exist).isEmpty());
    }

    @Test
    @DisplayName("1 minute overlap: 08:00-10:00 vs 09:59-11:00 MUST conflict")
    void oneMinuteOverlapConflicts() {
      List<String> cand = List.of("MONDAY 09:59-11:00");
      List<String> exist = List.of("MONDAY 08:00-10:00");

      assertTrue(detector.hasConflict(cand, exist), "09:59-11:00 overlaps 08:00-10:00 by 1 minute and must conflict");
      assertEquals(List.of("MONDAY 09:59-11:00"), detector.findConflictingSlots(cand, exist));
    }

    @Test
    @DisplayName("1 minute gap: 08:00-10:00 vs 10:01-12:00 must NOT conflict")
    void oneMinuteGapDoesNotConflict() {
      List<String> cand = List.of("MONDAY 10:01-12:00");
      List<String> exist = List.of("MONDAY 08:00-10:00");

      assertFalse(detector.hasConflict(cand, exist), "1-minute gap must not conflict");
      assertTrue(detector.findConflictingSlots(cand, exist).isEmpty());
    }

    @Test
    @DisplayName("Exact identical interval: 08:00-10:00 vs 08:00-10:00 MUST conflict")
    void identicalIntervalConflicts() {
      List<String> cand = List.of("MONDAY 08:00-10:00");
      List<String> exist = List.of("MONDAY 08:00-10:00");

      assertTrue(detector.hasConflict(cand, exist));
      assertEquals(List.of("MONDAY 08:00-10:00"), detector.findConflictingSlots(cand, exist));
    }

    @Test
    @DisplayName("Candidate subset: 08:30-09:30 inside 08:00-10:00 MUST conflict")
    void candidateSubsetOfExistingConflicts() {
      List<String> cand = List.of("MONDAY 08:30-09:30");
      List<String> exist = List.of("MONDAY 08:00-10:00");

      assertTrue(detector.hasConflict(cand, exist));
      assertEquals(List.of("MONDAY 08:30-09:30"), detector.findConflictingSlots(cand, exist));
    }

    @Test
    @DisplayName("Candidate superset: 08:00-10:00 enclosing 08:30-09:30 MUST conflict")
    void candidateSupersetOfExistingConflicts() {
      List<String> cand = List.of("MONDAY 08:00-10:00");
      List<String> exist = List.of("MONDAY 08:30-09:30");

      assertTrue(detector.hasConflict(cand, exist));
      assertEquals(List.of("MONDAY 08:00-10:00"), detector.findConflictingSlots(cand, exist));
    }

    @Test
    @DisplayName("Midnight start boundary: 00:00-01:00 vs 01:00-02:00 must NOT conflict")
    void midnightBoundaryAdjacentDoesNotConflict() {
      List<String> cand = List.of("SUNDAY 00:00-01:00");
      List<String> exist = List.of("SUNDAY 01:00-02:00");

      assertFalse(detector.hasConflict(cand, exist));
    }

    @Test
    @DisplayName("End of day boundary: 22:00-23:00 vs 23:00-23:59 must NOT conflict")
    void endOfDayBoundaryAdjacentDoesNotConflict() {
      List<String> cand = List.of("FRIDAY 22:00-23:00");
      List<String> exist = List.of("FRIDAY 23:00-23:59");

      assertFalse(detector.hasConflict(cand, exist));
    }

    @Test
    @DisplayName("End of day 1-minute overlap: 23:00-23:59 vs 23:58-23:59 MUST conflict")
    void endOfDayOneMinuteOverlapConflicts() {
      List<String> cand = List.of("FRIDAY 23:58-23:59");
      List<String> exist = List.of("FRIDAY 23:00-23:59");

      assertTrue(detector.hasConflict(cand, exist));
      assertEquals(List.of("FRIDAY 23:58-23:59"), detector.findConflictingSlots(cand, exist));
    }
  }

  // =========================================================================
  // SECTION 2: Slot String Formats, Cases, Whitespace, Dash & Invalid Strings
  // =========================================================================
  @Nested
  @DisplayName("2. Format Tolerance, Casing, Whitespace & Sanitization")
  class FormatToleranceTests {

    @Test
    @DisplayName("Case-insensitivity: lowercase canonical 'monday 08:00-10:00' conflicts with uppercase 'MONDAY 09:00-11:00'")
    void lowercaseCanonicalMatches() {
      List<String> cand = List.of("monday 08:00-10:00");
      List<String> exist = List.of("MONDAY 09:00-11:00");

      assertTrue(detector.hasConflict(cand, exist));
      assertEquals(List.of("monday 08:00-10:00"), detector.findConflictingSlots(cand, exist));
    }

    @Test
    @DisplayName("Abbreviated day matches full day: 'MON 08:00-10:00' conflicts with 'MONDAY 09:00-11:00'")
    void abbreviatedDayMatchesFullDay() {
      List<String> cand = List.of("MON 08:00-10:00");
      List<String> exist = List.of("MONDAY 09:00-11:00");

      assertTrue(detector.hasConflict(cand, exist));
      assertEquals(List.of("MON 08:00-10:00"), detector.findConflictingSlots(cand, exist));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "WED 08:00-10:00",
        "WEDNESDAY 08:00-10:00",
        "wed 08:00-10:00",
        "wednesday 08:00-10:00"
    })
    @DisplayName("All day-name variations correctly resolve DayOfWeek")
    void dayNameVariations(String slot) {
      var parsed = detector.parse(slot);
      assertNotNull(parsed);
      assertEquals(DayOfWeek.WEDNESDAY, parsed.day());
      assertEquals(480, parsed.startMinutes());
      assertEquals(600, parsed.endMinutes());
    }

    @Test
    @DisplayName("Dash variations: Hyphen (-), En-dash (–), Em-dash (—)")
    void dashVariationsParsedIdentically() {
      var hyphen = detector.parse("THU 14:00-16:00");
      var enDash = detector.parse("THU 14:00–16:00");
      var emDash = detector.parse("THU 14:00—16:00");

      assertNotNull(hyphen);
      assertNotNull(enDash);
      assertNotNull(emDash);

      assertEquals(hyphen.day(), enDash.day());
      assertEquals(hyphen.startMinutes(), enDash.startMinutes());
      assertEquals(hyphen.endMinutes(), enDash.endMinutes());
      assertEquals(enDash.startMinutes(), emDash.startMinutes());
      assertEquals(enDash.endMinutes(), emDash.endMinutes());

      assertTrue(detector.hasConflict(List.of("THU 14:00–16:00"), List.of("THURSDAY 15:00-17:00")));
    }

    @Test
    @DisplayName("Whitespace variations: excessive leading/trailing and inner spaces")
    void whitespaceTolerant() {
      var parsed = detector.parse("   FRIDAY    08:00   -    10:00   ");
      assertNotNull(parsed);
      assertEquals(DayOfWeek.FRIDAY, parsed.day());
      assertEquals(480, parsed.startMinutes());
      assertEquals(600, parsed.endMinutes());

      assertTrue(detector.hasConflict(
          List.of("   FRIDAY    08:00   -    10:00   "),
          List.of("FRI 09:00-11:00")
      ));
    }

    @Test
    @DisplayName("Single digit hour format: '8:00-10:00' and '08:00-9:00'")
    void singleDigitHourSupported() {
      var parsed1 = detector.parse("SATURDAY 8:00-10:00");
      var parsed2 = detector.parse("SAT 08:00-9:00");

      assertNotNull(parsed1);
      assertNotNull(parsed2);
      assertEquals(480, parsed1.startMinutes());
      assertEquals(600, parsed1.endMinutes());
      assertEquals(480, parsed2.startMinutes());
      assertEquals(540, parsed2.endMinutes());

      assertTrue(detector.hasConflict(List.of("SATURDAY 8:00-10:00"), List.of("SAT 08:00-9:00")));
    }

    @Test
    @DisplayName("Vietnamese format variations: Thứ 2 to Thứ 7, Chủ Nhật, CN")
    void vietnameseFormatsParsed() {
      var t2 = detector.parse("Thứ 2 · 08:00–10:00");
      var t3 = detector.parse("Thứ 3 • 14:00-16:00");
      var t7 = detector.parse("Thứ 7, 09:00-11:00");
      var cn1 = detector.parse("Chủ Nhật 18:00-20:00");
      var cn2 = detector.parse("CN 18:30-20:30");

      assertNotNull(t2);
      assertEquals(DayOfWeek.MONDAY, t2.day());
      assertNotNull(t3);
      assertEquals(DayOfWeek.TUESDAY, t3.day());
      assertNotNull(t7);
      assertEquals(DayOfWeek.SATURDAY, t7.day());
      assertNotNull(cn1);
      assertEquals(DayOfWeek.SUNDAY, cn1.day());
      assertNotNull(cn2);
      assertEquals(DayOfWeek.SUNDAY, cn2.day());

      // Cross-language overlap: Vietnamese Thứ 2 vs English MONDAY
      assertTrue(detector.hasConflict(List.of("Thứ 2 · 08:00–10:00"), List.of("MONDAY 09:00-11:00")));
      // Sunday overlap: Chủ Nhật vs CN
      assertTrue(detector.hasConflict(List.of("Chủ Nhật 18:00-20:00"), List.of("CN 18:30-20:30")));
    }

    @Test
    @DisplayName("Unaccented Vietnamese input behavior (Thu 2 vs Thứ 2)")
    void unaccentedVietnameseInputBehavior() {
      var accented = detector.parse("Thứ 2 08:00-10:00");
      assertNotNull(accented);
      assertEquals(DayOfWeek.MONDAY, accented.day());

      var unaccented = detector.parse("Thu 2 08:00-10:00");
      assertNotNull(unaccented);
      assertNull(unaccented.day(), "Unaccented Thu 2 is not matched by Thứ regex pattern");
      assertEquals("Thu 2 08:00-10:00", unaccented.raw());

      // But exact string match works for unaccented strings
      assertTrue(detector.hasConflict(List.of("Thu 2 08:00-10:00"), List.of("thu 2 08:00-10:00")));
    }

    @Test
    @DisplayName("Deduplication of candidate and existing slots")
    void duplicateSlotsHandling() {
      List<String> cand = List.of("MONDAY 08:00-10:00", "MONDAY 08:00-10:00", "TUESDAY 10:00-12:00");
      List<String> exist = List.of("MONDAY 09:00-11:00", "MONDAY 09:00-11:00");

      List<String> conflicts = detector.findConflictingSlots(cand, exist);
      // Must be deduplicated in returned list
      assertEquals(1, conflicts.size());
      assertEquals("MONDAY 08:00-10:00", conflicts.getFirst());
    }

    @Test
    @DisplayName("Vietnamese point-in-time default 60 minutes")
    void vietnamesePointInTimeDefaultDuration() {
      var t2Point = detector.parse("Thứ 2 18:00");
      assertNotNull(t2Point);
      assertEquals(DayOfWeek.MONDAY, t2Point.day());
      assertEquals(18 * 60, t2Point.startMinutes());
      assertEquals(19 * 60, t2Point.endMinutes());

      assertTrue(detector.hasConflict(List.of("Thứ 2 18:00"), List.of("MONDAY 18:30-19:30")));
      assertFalse(detector.hasConflict(List.of("Thứ 2 18:00"), List.of("MONDAY 19:00-20:00")));
    }

    @Test
    @DisplayName("Legacy point-in-time format vs canonical intervals")
    void legacyPointInTimeTests() {
      var legacy = detector.parse("Tue 19:00");
      assertNotNull(legacy);
      assertEquals(DayOfWeek.TUESDAY, legacy.day());
      assertEquals(19 * 60, legacy.startMinutes());
      assertEquals(20 * 60, legacy.endMinutes());

      // Overlap with 18:30-19:30
      assertTrue(detector.hasConflict(List.of("Tue 19:00"), List.of("TUESDAY 18:30-19:30")));
      // Adjacent before: 18:00-19:00 -> NO conflict
      assertFalse(detector.hasConflict(List.of("Tue 19:00"), List.of("TUESDAY 18:00-19:00")));
      // Adjacent after: 20:00-21:00 -> NO conflict
      assertFalse(detector.hasConflict(List.of("Tue 19:00"), List.of("TUESDAY 20:00-21:00")));
    }

    @Test
    @DisplayName("Invalid, blank, and null strings handling")
    void invalidStringsSafeHandling() {
      assertNull(detector.parse(null));
      assertNull(detector.parse(""));
      assertNull(detector.parse("   "));

      // Unparseable custom string
      var invalid = detector.parse("CUSTOM_CUSTOM_SLOT");
      assertNotNull(invalid);
      assertNull(invalid.day());
      assertEquals("CUSTOM_CUSTOM_SLOT", invalid.raw());

      // Exact string equality fallback works
      assertTrue(detector.hasConflict(List.of("CUSTOM_CUSTOM_SLOT"), List.of("custom_custom_slot")));
      // Non-equal invalid strings do not falsely conflict
      assertFalse(detector.hasConflict(List.of("CUSTOM_SLOT_A"), List.of("CUSTOM_SLOT_B")));
      // Invalid string does not conflict with canonical valid slot
      assertFalse(detector.hasConflict(List.of("CUSTOM_SLOT_A"), List.of("MONDAY 08:00-10:00")));
    }

    @Test
    @DisplayName("Null and empty candidate or existing lists return empty conflict list safely")
    void nullAndEmptyListSafety() {
      assertFalse(detector.hasConflict(null, List.of("MONDAY 08:00-10:00")));
      assertFalse(detector.hasConflict(List.of("MONDAY 08:00-10:00"), null));
      assertFalse(detector.hasConflict(List.of(), List.of("MONDAY 08:00-10:00")));
      assertFalse(detector.hasConflict(List.of("MONDAY 08:00-10:00"), List.of()));

      List<String> listWithNulls = new ArrayList<>();
      listWithNulls.add(null);
      listWithNulls.add("MONDAY 08:00-10:00");
      assertTrue(detector.hasConflict(listWithNulls, List.of("MONDAY 09:00-11:00")));
    }
  }

  // =========================================================================
  // SECTION 3: Gatekeeper Dual Source Conflict Check in ConnectionRequestService
  // =========================================================================
  @Nested
  @DisplayName("3. Gatekeeper Dual Source Conflict Check with Multiple Active Classes")
  class GatekeeperDualSourceTests {

    final UUID student = UUID.randomUUID();
    final UUID studentUser = UUID.randomUUID();
    final UUID tutor = UUID.randomUUID();
    final UUID tutorUser = UUID.randomUUID();
    final UUID goal = UUID.randomUUID();
    final UUID requestId = UUID.randomUUID();
    final Instant now = Instant.parse("2026-10-15T00:00:00Z");

    final ConnectionRequestRepository requests = mock(ConnectionRequestRepository.class);
    final TutoringClassRepository classes = mock(TutoringClassRepository.class);
    final ProfileFacade profile = mock(ProfileFacade.class);
    final IamFacade iam = mock(IamFacade.class);
    final ConnectionFacade facade = mock(ConnectionFacade.class);
    final ConnectionLock lock = mock(ConnectionLock.class);
    final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    final TutorScheduleConflictChecker scheduleChecker = mock(TutorScheduleConflictChecker.class);

    ConnectionRequestService service;

    @BeforeEach
    void setupService() {
      when(profile.findStudentById(student))
          .thenReturn(Optional.of(new StudentSummaryDto(student, studentUser, 4, null, null, true)));
      when(profile.findLearningNeed(goal))
          .thenReturn(Optional.of(ConnectionTestFixtures.need(goal, student, studentUser, 1, 4, "ACTIVE")));
      when(profile.findTutorById(tutor))
          .thenReturn(Optional.of(new TutorSummaryDto(tutor, tutorUser, "Tutor", null, null, null, null, false, null, null, null, null, "VERIFIED", now, null, 0)));
      when(profile.findTutorOfferings(tutor)).thenReturn(ConnectionTestFixtures.offerings(1, 4));
      when(profile.getTutorIdByUserId(tutorUser)).thenReturn(tutor);

      when(iam.findUserSummaryById(tutorUser))
          .thenReturn(Optional.of(new UserSummaryView(tutorUser, "tutor@test.invalid", "Tutor", AccountRole.TUTOR, AccountStatus.ACTIVE)));

      when(classes.save(any(TutoringClass.class))).thenAnswer(call -> {
        TutoringClass c = call.getArgument(0);
        ReflectionTestUtils.setField(c, "id", UUID.randomUUID());
        return c;
      });

      service = new ConnectionRequestService(requests, classes, profile, iam, lock, facade, new ConnectionRequestInput("legacy-compatible"), events, ConnectionTestDefaults.expiryMock(), detector, scheduleChecker, ConnectionTestDefaults.noScheduledSessions(), Clock.fixed(now, ZoneOffset.UTC));
    }

    private CurrentUser tutorActor() {
      CurrentUser actor = mock(CurrentUser.class);
      when(actor.getUserId()).thenReturn(tutorUser);
      when(actor.hasRole("TUTOR")).thenReturn(true);
      when(actor.hasRole("STUDENT")).thenReturn(false);
      when(actor.hasRole("PARENT")).thenReturn(false);
      return actor;
    }

    private ConnectionRequest setupPendingRequest(List<String> desiredSlots) {
      ConnectionRequest req = new ConnectionRequest(student, tutor, goal, studentUser, desiredSlots, "Hi", now);
      ReflectionTestUtils.setField(req, "id", requestId);
      when(requests.findById(requestId)).thenReturn(Optional.of(req));
      when(requests.lockById(requestId)).thenReturn(Optional.of(req));
      return req;
    }

    private List<TutoringClass> setupThreeActiveClasses() {
      // Class 1: Monday 08:00-10:00
      UUID req1Id = UUID.randomUUID();
      ConnectionRequest req1 = new ConnectionRequest(UUID.randomUUID(), tutor, UUID.randomUUID(), UUID.randomUUID(),
          List.of("MONDAY 08:00-10:00"), null, now);
      ReflectionTestUtils.setField(req1, "id", req1Id);
      TutoringClass class1 = new TutoringClass(req1, 200000L, now);

      // Class 2: Wednesday 14:00-16:00
      UUID req2Id = UUID.randomUUID();
      ConnectionRequest req2 = new ConnectionRequest(UUID.randomUUID(), tutor, UUID.randomUUID(), UUID.randomUUID(),
          List.of("WEDNESDAY 14:00-16:00"), null, now);
      ReflectionTestUtils.setField(req2, "id", req2Id);
      TutoringClass class2 = new TutoringClass(req2, 200000L, now);

      // Class 3: Friday 18:00-20:00
      UUID req3Id = UUID.randomUUID();
      ConnectionRequest req3 = new ConnectionRequest(UUID.randomUUID(), tutor, UUID.randomUUID(), UUID.randomUUID(),
          List.of("FRIDAY 18:00-20:00"), null, now);
      ReflectionTestUtils.setField(req3, "id", req3Id);
      TutoringClass class3 = new TutoringClass(req3, 200000L, now);

      List<TutoringClass> activeClasses = List.of(class1, class2, class3);
      when(classes.findByTutorIdAndStatus(tutor, "ACTIVE")).thenReturn(activeClasses);
      when(requests.findAllById(List.of(req1Id, req2Id, req3Id))).thenReturn(List.of(req1, req2, req3));

      return activeClasses;
    }

    @Test
    @DisplayName("Multiple active classes: Candidate does NOT conflict with any -> Accept SUCCEEDS")
    void acceptWithMultipleActiveClassesSucceedsWhenNoOverlap() {
      setupThreeActiveClasses();
      var req = setupPendingRequest(List.of("TUESDAY 08:00-10:00", "THURSDAY 14:00-16:00"));
      when(scheduleChecker.hasConflictWithScheduledSessions(any(), any())).thenReturn(false);

      var result = service.respondRequest(tutorActor(), requestId, true, null);

      assertInstanceOf(TransitionResult.Success.class, result);
      assertEquals("ACCEPTED", req.getStatus());
      verify(classes).save(any(TutoringClass.class));
    }

    @Test
    @DisplayName("Multiple active classes: Candidate conflicts with Class 1 (Monday) -> HTTP 409 thrown atomically")
    void acceptWithMultipleActiveClassesConflictsWithFirstClass() {
      setupThreeActiveClasses();
      var req = setupPendingRequest(List.of("MONDAY 09:00-11:00")); // overlaps Class 1 (08:00-10:00)

      var ex = assertThrows(ResourceConflictException.class, () ->
          service.respondRequest(tutorActor(), requestId, true, null));

      assertEquals(ErrorCode.SLOT_CONFLICT_WITH_ACTIVE_SCHEDULE, ex.getErrorCode());
      assertTrue(ex.getMessage().contains("MONDAY 09:00-11:00"));
      assertEquals("PENDING", req.getStatus());
      verify(classes, never()).save(any());
    }

    @Test
    @DisplayName("Multiple active classes: Candidate conflicts with Class 3 (Friday) -> HTTP 409 thrown atomically")
    void acceptWithMultipleActiveClassesConflictsWithThirdClass() {
      setupThreeActiveClasses();
      var req = setupPendingRequest(List.of("FRIDAY 19:00-21:00")); // overlaps Class 3 (18:00-20:00)

      var ex = assertThrows(ResourceConflictException.class, () ->
          service.respondRequest(tutorActor(), requestId, true, null));

      assertEquals(ErrorCode.SLOT_CONFLICT_WITH_ACTIVE_SCHEDULE, ex.getErrorCode());
      assertTrue(ex.getMessage().contains("FRIDAY 19:00-21:00"));
      assertEquals("PENDING", req.getStatus());
      verify(classes, never()).save(any());
    }

    @Test
    @DisplayName("Multiple active classes: Candidate overlaps Class 1 AND Class 3 -> Both conflicting slots reported in error")
    void acceptWithMultipleActiveClassesReportsAllConflictingSlots() {
      setupThreeActiveClasses();
      var req = setupPendingRequest(List.of("MONDAY 09:00-11:00", "FRIDAY 19:00-21:00", "SATURDAY 10:00-12:00"));

      var ex = assertThrows(ResourceConflictException.class, () ->
          service.respondRequest(tutorActor(), requestId, true, null));

      assertEquals(ErrorCode.SLOT_CONFLICT_WITH_ACTIVE_SCHEDULE, ex.getErrorCode());
      assertTrue(ex.getMessage().contains("MONDAY 09:00-11:00"));
      assertTrue(ex.getMessage().contains("FRIDAY 19:00-21:00"));
      assertFalse(ex.getMessage().contains("SATURDAY 10:00-12:00"), "Non-conflicting slot must not be in error message");
      assertEquals("PENDING", req.getStatus());
      verify(classes, never()).save(any());
    }

    @Test
    @DisplayName("Multiple active classes: Candidate touches exact boundary of Class 1 -> MUST SUCCEED without false conflict")
    void acceptWithExactBoundaryDoesNotConflict() {
      setupThreeActiveClasses();
      // Class 1 is MONDAY 08:00-10:00. Candidate is MONDAY 10:00-12:00 (exact boundary).
      var req = setupPendingRequest(List.of("MONDAY 10:00-12:00"));
      when(scheduleChecker.hasConflictWithScheduledSessions(any(), any())).thenReturn(false);

      var result = service.respondRequest(tutorActor(), requestId, true, null);

      assertInstanceOf(TransitionResult.Success.class, result);
      assertEquals("ACCEPTED", req.getStatus());
      verify(classes).save(any(TutoringClass.class));
    }

    @Test
    @DisplayName("Multiple active classes: Null connectionRequestId or null desiredSlots handled gracefully")
    void activeClassesWithNullReqIdHandledGracefully() {
      UUID req1Id = UUID.randomUUID();
      ConnectionRequest req1 = new ConnectionRequest(UUID.randomUUID(), tutor, UUID.randomUUID(), UUID.randomUUID(),
          List.of("MONDAY 08:00-10:00"), null, now);
      ReflectionTestUtils.setField(req1, "id", req1Id);
      TutoringClass class1 = new TutoringClass(req1, 200000L, now);

      // Defective class with null connectionRequestId
      TutoringClass classWithoutReq = new TutoringClass(req1, 200000L, now);
      ReflectionTestUtils.setField(classWithoutReq, "connectionRequestId", null);

      when(classes.findByTutorIdAndStatus(tutor, "ACTIVE")).thenReturn(List.of(class1, classWithoutReq));
      when(requests.findAllById(List.of(req1Id))).thenReturn(List.of(req1));

      var req = setupPendingRequest(List.of("TUESDAY 08:00-10:00"));
      when(scheduleChecker.hasConflictWithScheduledSessions(any(), any())).thenReturn(false);

      assertDoesNotThrow(() -> service.respondRequest(tutorActor(), requestId, true, null));
      assertEquals("ACCEPTED", req.getStatus());
    }

    @Test
    @DisplayName("Dual Source: Active classes clear, BUT SPI scheduleConflictChecker reports conflict -> HTTP 409 thrown")
    void dualSourceScheduledSessionConflictThrows409() {
      setupThreeActiveClasses();
      var req = setupPendingRequest(List.of("TUESDAY 08:00-10:00")); // no conflict with active classes
      when(scheduleChecker.hasConflictWithScheduledSessions(tutor, List.of("TUESDAY 08:00-10:00"))).thenReturn(true);

      var ex = assertThrows(ResourceConflictException.class, () ->
          service.respondRequest(tutorActor(), requestId, true, null));

      assertEquals(ErrorCode.SLOT_CONFLICT_WITH_ACTIVE_SCHEDULE, ex.getErrorCode());
      assertTrue(ex.getMessage().contains("buổi học đã được lên lịch"));
      assertEquals("PENDING", req.getStatus());
      verify(classes, never()).save(any());
    }

    @Test
    @DisplayName("Tutor REJECT with conflicting slots succeeds without throwing conflict exception")
    void tutorRejectWithConflictingSlotsSucceeds() {
      setupThreeActiveClasses();
      var req = setupPendingRequest(List.of("MONDAY 08:00-10:00")); // identical to Class 1

      assertDoesNotThrow(() -> service.respondRequest(tutorActor(), requestId, false, "Not available"));
      assertEquals("REJECTED", req.getStatus());
      verify(classes, never()).save(any());
    }
  }
}
