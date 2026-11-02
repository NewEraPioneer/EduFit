package vn.edufit.scheduling.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import vn.edufit.scheduling.api.event.SessionCancelledEvent;
import vn.edufit.scheduling.api.event.SessionScheduledEvent;
import vn.edufit.scheduling.application.port.ConnectionValidationPort;
import vn.edufit.scheduling.application.port.ConnectionValidationPort.ActiveClassInfo;
import vn.edufit.scheduling.application.service.RescheduleClassScheduleService;
import vn.edufit.scheduling.application.service.SchedulingSecurityResolver;
import vn.edufit.scheduling.application.service.SimpleCurrentUser;
import vn.edufit.scheduling.domain.model.ProposalStatus;
import vn.edufit.scheduling.domain.model.RecurringRescheduleProposal;
import vn.edufit.scheduling.domain.model.SessionMode;
import vn.edufit.scheduling.domain.model.SessionStatus;
import vn.edufit.scheduling.domain.model.TutoringSession;
import vn.edufit.scheduling.domain.repository.SessionHistoryDomainRepository;
import vn.edufit.scheduling.domain.repository.TutoringSessionDomainRepository;
import vn.edufit.scheduling.web.request.ProposeRecurringScheduleRequest;
import vn.edufit.scheduling.web.request.RespondRecurringScheduleRequest;
import vn.edufit.shared.auth.CurrentUser;
import vn.edufit.shared.exception.ForbiddenOperationException;
import vn.edufit.shared.exception.InvalidOperationException;
import vn.edufit.shared.exception.ResourceConflictException;

@ExtendWith(MockitoExtension.class)
@DisplayName("Unit Test Service: UC4.8 Điều chỉnh lịch học định kỳ (RescheduleClassScheduleService)")
class RescheduleClassScheduleServiceTest {

  private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

  @Mock
  private TutoringSessionDomainRepository sessionRepository;

  @Mock
  private SessionHistoryDomainRepository historyRepository;

  @Mock
  private ConnectionValidationPort connectionValidationPort;

  @Mock
  private SchedulingSecurityResolver schedulingSecurityResolver;

  @Mock
  private ApplicationEventPublisher eventPublisher;

  private Clock fixedClock;
  private RescheduleClassScheduleService service;

  private final UUID classId = UUID.randomUUID();
  private final UUID tutorId = UUID.randomUUID();
  private final UUID studentId = UUID.randomUUID();
  private final UUID tutorUserId = UUID.randomUUID();
  private final UUID studentUserId = UUID.randomUUID();
  private final UUID otherUserId = UUID.randomUUID();

  // Mốc thời gian cố định: 2026-11-01T00:00:00Z (7:00 AM VN)
  private final Instant fixedNow = Instant.parse("2026-11-01T00:00:00Z");
  private final LocalDate today = LocalDate.ofInstant(fixedNow, VIETNAM_ZONE); // 2026-11-01

  private CurrentUser tutorUser;
  private CurrentUser studentUser;
  private CurrentUser otherUser;

  @BeforeEach
  void setUp() {
    fixedClock = Clock.fixed(fixedNow, VIETNAM_ZONE);
    service = new RescheduleClassScheduleService(
        sessionRepository,
        historyRepository,
        connectionValidationPort,
        schedulingSecurityResolver,
        eventPublisher,
        fixedClock
    );

    tutorUser = SimpleCurrentUser.of(tutorUserId, "TUTOR");
    studentUser = SimpleCurrentUser.of(studentUserId, "STUDENT");
    otherUser = SimpleCurrentUser.of(otherUserId, "STUDENT");
  }

  private RecurringRescheduleProposal proposeHelper(ProposeRecurringScheduleRequest req, CurrentUser user) {
    return service.proposeRecurringSchedule(
        req.classId(),
        req.effectiveFrom(),
        req.effectiveTo(),
        req.newWeeklySlots(),
        req.mode(),
        req.placeOrLink(),
        req.message(),
        user
    );
  }

  private RescheduleClassScheduleService.RecurringScheduleResult respondHelper(RespondRecurringScheduleRequest req, CurrentUser user) {
    return service.respondRecurringSchedule(
        req.proposalId(),
        req.accept(),
        req.reason(),
        user
    );
  }

  // ==========================================
  // BOUNDED HORIZON TESTS (4-8 WEEKS / 28-56 DAYS)
  // ==========================================

  @ParameterizedTest
  @ValueSource(ints = {0, 1, 7, 14, 21, 27})
  @DisplayName("Bounded Horizon: Từ chối các đề xuất có thời gian áp dụng < 28 ngày (dưới 4 tuần)")
  void shouldRejectProposalsWithHorizonLessThan28Days(int durationDays) {
    LocalDate effectiveFrom = today.plusDays(1);
    LocalDate effectiveTo = effectiveFrom.plusDays(durationDays);

    ProposeRecurringScheduleRequest req = new ProposeRecurringScheduleRequest(
        classId,
        effectiveFrom,
        effectiveTo,
        List.of("MONDAY 18:00-20:00"),
        SessionMode.ONLINE,
        null,
        "Đổi lịch"
    );

    assertThrows(InvalidOperationException.class, () ->
        proposeHelper(req, tutorUser)
    );
  }

  @ParameterizedTest
  @ValueSource(ints = {57, 60, 70, 90, 180})
  @DisplayName("Bounded Horizon: Từ chối các đề xuất có thời gian áp dụng > 56 ngày (trên 8 tuần)")
  void shouldRejectProposalsWithHorizonGreaterThan56Days(int durationDays) {
    LocalDate effectiveFrom = today.plusDays(1);
    LocalDate effectiveTo = effectiveFrom.plusDays(durationDays);

    ProposeRecurringScheduleRequest req = new ProposeRecurringScheduleRequest(
        classId,
        effectiveFrom,
        effectiveTo,
        List.of("MONDAY 18:00-20:00"),
        SessionMode.ONLINE,
        null,
        "Đổi lịch"
    );

    assertThrows(InvalidOperationException.class, () ->
        proposeHelper(req, tutorUser)
    );
  }

  @ParameterizedTest
  @ValueSource(ints = {28, 35, 42, 49, 56})
  @DisplayName("Bounded Horizon: Chấp nhận các đề xuất có thời gian áp dụng hợp lệ từ 28 đến 56 ngày (4-8 tuần)")
  void shouldAcceptProposalsWithHorizonBetween28And56Days(int durationDays) {
    LocalDate effectiveFrom = today.plusDays(1);
    LocalDate effectiveTo = effectiveFrom.plusDays(durationDays);

    when(connectionValidationPort.findActiveClassById(classId))
        .thenReturn(Optional.of(new ActiveClassInfo(classId, studentId, tutorId, UUID.randomUUID())));

    ProposeRecurringScheduleRequest req = new ProposeRecurringScheduleRequest(
        classId,
        effectiveFrom,
        effectiveTo,
        List.of("MONDAY 18:00-20:00", "THURSDAY 18:00-20:00"),
        SessionMode.ONLINE,
        null,
        "Đề xuất hợp lệ"
    );

    RecurringRescheduleProposal proposal = proposeHelper(req, tutorUser);
    assertNotNull(proposal);
    assertEquals(ProposalStatus.PENDING, proposal.getStatus());
    assertEquals(effectiveFrom, proposal.getEffectiveFrom());
    assertEquals(effectiveTo, proposal.getEffectiveTo());
  }

  @Test
  @DisplayName("Bounded Horizon: Từ chối nếu effectiveFrom ở trong quá khứ so với hôm nay")
  void shouldRejectPastEffectiveFrom() {
    LocalDate pastEffectiveFrom = today.minusDays(2);
    LocalDate effectiveTo = pastEffectiveFrom.plusDays(30);

    ProposeRecurringScheduleRequest req = new ProposeRecurringScheduleRequest(
        classId,
        pastEffectiveFrom,
        effectiveTo,
        List.of("MONDAY 18:00-20:00"),
        SessionMode.ONLINE,
        null,
        "Quá khứ"
    );

    assertThrows(InvalidOperationException.class, () ->
        proposeHelper(req, tutorUser)
    );
  }

  // ==========================================
  // SLOT FORMAT & OVERLAP VALIDATION TESTS
  // ==========================================

  @Test
  @DisplayName("Định dạng khung giờ: Từ chối nếu định dạng slot không hợp lệ")
  void shouldRejectInvalidSlotFormat() {
    LocalDate effectiveFrom = today.plusDays(1);
    LocalDate effectiveTo = effectiveFrom.plusDays(30);

    ProposeRecurringScheduleRequest req = new ProposeRecurringScheduleRequest(
        classId,
        effectiveFrom,
        effectiveTo,
        List.of("Khung giờ linh hoạt"), // Không đúng định dạng
        SessionMode.ONLINE,
        null,
        "Lỗi định dạng"
    );

    assertThrows(InvalidOperationException.class, () ->
        proposeHelper(req, tutorUser)
    );
  }

  @Test
  @DisplayName("Định dạng khung giờ: Từ chối nếu có 2 khung giờ bị trùng nhau vào cùng một ngày")
  void shouldRejectOverlappingSlotsInProposal() {
    LocalDate effectiveFrom = today.plusDays(1);
    LocalDate effectiveTo = effectiveFrom.plusDays(30);

    ProposeRecurringScheduleRequest req = new ProposeRecurringScheduleRequest(
        classId,
        effectiveFrom,
        effectiveTo,
        List.of("MONDAY 18:00-20:00", "MONDAY 19:00-21:00"),
        SessionMode.ONLINE,
        null,
        "Trùng lặp nội tại"
    );

    assertThrows(ResourceConflictException.class, () ->
        proposeHelper(req, tutorUser)
    );
  }

  // ==========================================
  // MUTUAL CONSENT & RESPOND TESTS
  // ==========================================

  @Test
  @DisplayName("Mutual Consent: Người gửi đề xuất không được tự phản hồi đề xuất của chính mình")
  void shouldRejectSelfResponse() {
    LocalDate effectiveFrom = today.plusDays(1);
    LocalDate effectiveTo = effectiveFrom.plusDays(28);

    when(connectionValidationPort.findActiveClassById(classId))
        .thenReturn(Optional.of(new ActiveClassInfo(classId, studentId, tutorId, UUID.randomUUID())));

    RecurringRescheduleProposal proposal = service.proposeRecurringSchedule(
        classId,
        effectiveFrom,
        effectiveTo,
        List.of("MONDAY 18:00-20:00"),
        SessionMode.ONLINE,
        null,
        "Đổi lịch",
        tutorUser
    );

    doThrow(new ForbiddenOperationException("Bạn không thể tự phản hồi đề xuất do chính mình tạo ra"))
        .when(schedulingSecurityResolver).requireCounterpartyResponseAccess(
            eq(tutorUser), eq(tutorId), eq(studentId), eq(tutorUserId)
        );

    RespondRecurringScheduleRequest req = new RespondRecurringScheduleRequest(
        proposal.getProposalId(),
        true,
        null
    );

    assertThrows(ForbiddenOperationException.class, () ->
        respondHelper(req, tutorUser)
    );
  }

  @Test
  @DisplayName("Mutual Consent: Đối tác từ chối đề xuất (REJECT) -> đánh dấu đề xuất REJECTED, không sinh buổi học mới")
  void shouldHandleRejectByCounterparty() {
    LocalDate effectiveFrom = today.plusDays(1);
    LocalDate effectiveTo = effectiveFrom.plusDays(28);

    when(connectionValidationPort.findActiveClassById(classId))
        .thenReturn(Optional.of(new ActiveClassInfo(classId, studentId, tutorId, UUID.randomUUID())));

    RecurringRescheduleProposal proposal = service.proposeRecurringSchedule(
        classId,
        effectiveFrom,
        effectiveTo,
        List.of("MONDAY 18:00-20:00"),
        SessionMode.ONLINE,
        null,
        "Đổi lịch",
        tutorUser
    );

    RespondRecurringScheduleRequest req = new RespondRecurringScheduleRequest(
        proposal.getProposalId(),
        false,
        "Không tiện giờ này"
    );

    RescheduleClassScheduleService.RecurringScheduleResult result = respondHelper(req, studentUser);

    assertEquals(ProposalStatus.REJECTED, result.proposal().getStatus());
    assertEquals(0, result.cancelledSessionCount());
    assertTrue(result.generatedSessions().isEmpty());
    verify(sessionRepository, never()).save(any());
  }

  @Test
  @DisplayName("Mutual Consent & Atomic Regeneration: Chấp thuận (ACCEPT) thành công -> Hủy các buổi cũ sắp tới và sinh các buổi mới")
  void shouldCancelOldSessionsAndRegenerateNewSessionsOnAccept() {
    LocalDate effectiveFrom = today.plusDays(1);
    LocalDate effectiveTo = effectiveFrom.plusDays(28); // 4 tuần

    when(connectionValidationPort.findActiveClassById(classId))
        .thenReturn(Optional.of(new ActiveClassInfo(classId, studentId, tutorId, UUID.randomUUID())));

    RecurringRescheduleProposal proposal = service.proposeRecurringSchedule(
        classId,
        effectiveFrom,
        effectiveTo,
        List.of("MONDAY 18:00-20:00", "THURSDAY 18:00-20:00"),
        SessionMode.ONLINE,
        null,
        "Đổi lịch định kỳ",
        tutorUser
    );

    // Giả lập 2 buổi học cũ sắp tới cần bị hủy
    Instant oldSessionStart1 = effectiveFrom.plusDays(2).atTime(18, 0).atZone(VIETNAM_ZONE).toInstant();
    Instant oldSessionEnd1 = oldSessionStart1.plusSeconds(7200);
    TutoringSession oldSession1 = TutoringSession.createScheduled(
        UUID.randomUUID(), classId, tutorId, studentId, oldSessionStart1, oldSessionEnd1,
        SessionMode.ONLINE, null, null, null, tutorUserId, studentUserId, fixedNow
    );

    when(sessionRepository.findScheduledSessionsByClass(eq(classId), any(), any()))
        .thenReturn(List.of(oldSession1));
    when(sessionRepository.findTutorBusySlots(any(), any(), any())).thenReturn(Collections.emptyList());
    when(sessionRepository.findCalendarSessions(any(), any(), any())).thenReturn(Collections.emptyList());
    when(sessionRepository.save(any(TutoringSession.class))).thenAnswer(inv -> inv.getArgument(0));

    RespondRecurringScheduleRequest req = new RespondRecurringScheduleRequest(
        proposal.getProposalId(),
        true,
        null
    );

    RescheduleClassScheduleService.RecurringScheduleResult result = respondHelper(req, studentUser);

    assertEquals(ProposalStatus.ACCEPTED, result.proposal().getStatus());
    assertEquals(1, result.cancelledSessionCount());
    // Từ 02/11/2026 (Thứ 2) đến 30/11/2026 (Thứ 2) bao gồm 5 Thứ Hai và 4 Thứ Năm -> tổng cộng 9 buổi học mới được sinh ra
    assertEquals(9, result.generatedSessions().size());
    verify(eventPublisher).publishEvent(any(SessionCancelledEvent.class));
    verify(eventPublisher, org.mockito.Mockito.times(9)).publishEvent(any(SessionScheduledEvent.class));
  }

  @Test
  @DisplayName("Cascade Termination: Từ chối chấp thuận đề xuất đổi lịch định kỳ nếu lớp học đã kết thúc (không còn ACTIVE)")
  void shouldRejectAcceptingRecurringProposalWhenClassIsNotActive() {
    LocalDate effectiveFrom = today.plusDays(1);
    LocalDate effectiveTo = effectiveFrom.plusDays(28);

    when(connectionValidationPort.findActiveClassById(classId))
        .thenReturn(Optional.of(new ActiveClassInfo(classId, studentId, tutorId, UUID.randomUUID())));

    RecurringRescheduleProposal proposal = service.proposeRecurringSchedule(
        classId,
        effectiveFrom,
        effectiveTo,
        List.of("MONDAY 18:00-20:00"),
        SessionMode.ONLINE,
        null,
        "Đổi lịch",
        tutorUser
    );

    // Giả lập lớp học đã kết thúc trước khi đối tác phản hồi
    when(connectionValidationPort.findActiveClassById(classId)).thenReturn(Optional.empty());

    RespondRecurringScheduleRequest req = new RespondRecurringScheduleRequest(
        proposal.getProposalId(),
        true,
        null
    );

    assertThrows(InvalidOperationException.class, () ->
        respondHelper(req, studentUser)
    );
  }

  @Test
  @DisplayName("Cascade Termination: cancelActiveProposalForClass làm hết hạn đề xuất PENDING của lớp học kết thúc")
  void shouldCancelActiveProposalForClass() {
    LocalDate effectiveFrom = today.plusDays(1);
    LocalDate effectiveTo = effectiveFrom.plusDays(28);

    when(connectionValidationPort.findActiveClassById(classId))
        .thenReturn(Optional.of(new ActiveClassInfo(classId, studentId, tutorId, UUID.randomUUID())));

    RecurringRescheduleProposal proposal = service.proposeRecurringSchedule(
        classId,
        effectiveFrom,
        effectiveTo,
        List.of("MONDAY 18:00-20:00"),
        SessionMode.ONLINE,
        null,
        "Đổi lịch",
        tutorUser
    );

    assertTrue(proposal.isPending(fixedNow));
    assertTrue(service.findActiveProposalByClassId(classId).isPresent());

    // Cascade cancel
    service.cancelActiveProposalForClass(classId, "Lớp đã kết thúc", fixedNow);

    assertFalse(proposal.isPending(fixedNow));
    assertTrue(service.findActiveProposalByClassId(classId).isEmpty());
  }
}
