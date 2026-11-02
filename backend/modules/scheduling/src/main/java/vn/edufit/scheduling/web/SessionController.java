package vn.edufit.scheduling.web;

import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.edufit.scheduling.application.service.BatchProposeSessionService;
import vn.edufit.scheduling.application.service.BatchRespondSessionService;
import vn.edufit.scheduling.application.service.CancelSessionService;
import vn.edufit.scheduling.application.service.GetCalendarService;
import vn.edufit.scheduling.application.service.GetSessionDetailService;
import vn.edufit.scheduling.application.service.ProposeSessionService;
import vn.edufit.scheduling.application.service.RecordSessionOutcomeService;
import vn.edufit.scheduling.application.service.RescheduleClassScheduleService;
import vn.edufit.scheduling.application.service.RescheduleSessionService;
import vn.edufit.scheduling.application.service.RespondToProposalService;
import vn.edufit.scheduling.domain.model.RecurringRescheduleProposal;
import vn.edufit.scheduling.domain.model.SessionRescheduleProposal;
import vn.edufit.scheduling.domain.model.TutoringSession;
import vn.edufit.scheduling.web.request.BatchProposeSessionRequest;
import vn.edufit.scheduling.web.request.BatchRespondSessionRequest;
import vn.edufit.scheduling.web.request.CancelSessionRequest;
import vn.edufit.scheduling.web.request.ProposeRecurringScheduleRequest;
import vn.edufit.scheduling.web.request.ProposeSessionRequest;
import vn.edufit.scheduling.web.request.RecordOutcomeRequest;
import vn.edufit.scheduling.web.request.RescheduleSessionRequest;
import vn.edufit.scheduling.web.request.RespondProposalRequest;
import vn.edufit.scheduling.web.request.RespondRecurringScheduleRequest;
import vn.edufit.scheduling.web.request.RespondRescheduleRequest;
import vn.edufit.scheduling.web.response.CalendarItemResponse;
import vn.edufit.scheduling.web.response.RecurringProposalResponse;
import vn.edufit.scheduling.web.response.RecurringScheduleResultResponse;
import vn.edufit.scheduling.web.response.RescheduleProposalResponse;
import vn.edufit.scheduling.web.response.SessionResponse;
import vn.edufit.shared.auth.CurrentUser;
import vn.edufit.shared.exception.EntityNotFoundException;
import vn.edufit.shared.exception.ErrorCode;
import vn.edufit.shared.exception.InvalidOperationException;
import vn.edufit.shared.response.ApiResponse;

/**
 * REST Controller cho phân hệ Scheduling (/api/v1/scheduling).
 *
 * <p>Cung cấp các API:
 * <ul>
 *   <li>{@code POST /sessions/propose}: Đề xuất lịch học mới (UC4.1).</li>
 *   <li>{@code POST /sessions/propose-batch}: Đề xuất hàng loạt lịch học (Batch Propose).</li>
 *   <li>{@code POST /sessions/{id}/respond}: Chấp nhận / Từ chối đề xuất (UC4.3).</li>
 *   <li>{@code POST /sessions/respond-batch}: Phản hồi hàng loạt đề xuất (Batch Respond).</li>
 *   <li>{@code POST /sessions/{id}/reschedule}: Yêu cầu đổi lịch (UC4.4).</li>
 *   <li>{@code POST /sessions/reschedule-proposals/{proposalId}/respond}: Phản hồi đổi lịch (UC4.4).</li>
 *   <li>{@code POST /recurring/propose}: Đề xuất đổi lịch học định kỳ (UC4.8).</li>
 *   <li>{@code POST /recurring/respond}: Phản hồi đề xuất đổi lịch định kỳ (UC4.8).</li>
 *   <li>{@code POST /sessions/{id}/cancel}: Hủy buổi học đã lên lịch (UC4.5).</li>
 *   <li>{@code POST /sessions/{id}/outcome}: Ghi nhận kết quả và biên bản (UC4.6).</li>
 *   <li>{@code GET /sessions/{id}}: Chi tiết buổi học (bảo vệ quyền truy cập qua GetSessionDetailService).</li>
 *   <li>{@code GET /calendar}: Tra cứu lịch học theo khoảng thời gian và hồ sơ người dùng.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/scheduling")
public class SessionController {

  private final ProposeSessionService proposeSessionService;
  private final BatchProposeSessionService batchProposeSessionService;
  private final RespondToProposalService respondToProposalService;
  private final BatchRespondSessionService batchRespondSessionService;
  private final RescheduleSessionService rescheduleSessionService;
  private final RescheduleClassScheduleService rescheduleClassScheduleService;
  private final CancelSessionService cancelSessionService;
  private final RecordSessionOutcomeService recordSessionOutcomeService;
  private final GetCalendarService getCalendarService;
  private final GetSessionDetailService getSessionDetailService;
  private final ObjectProvider<CurrentUser> currentUserProvider;

  public SessionController(
      ProposeSessionService proposeSessionService,
      BatchProposeSessionService batchProposeSessionService,
      RespondToProposalService respondToProposalService,
      BatchRespondSessionService batchRespondSessionService,
      RescheduleSessionService rescheduleSessionService,
      RescheduleClassScheduleService rescheduleClassScheduleService,
      CancelSessionService cancelSessionService,
      RecordSessionOutcomeService recordSessionOutcomeService,
      GetCalendarService getCalendarService,
      GetSessionDetailService getSessionDetailService,
      ObjectProvider<CurrentUser> currentUserProvider
  ) {
    this.proposeSessionService = proposeSessionService;
    this.batchProposeSessionService = batchProposeSessionService;
    this.respondToProposalService = respondToProposalService;
    this.batchRespondSessionService = batchRespondSessionService;
    this.rescheduleSessionService = rescheduleSessionService;
    this.rescheduleClassScheduleService = rescheduleClassScheduleService;
    this.cancelSessionService = cancelSessionService;
    this.recordSessionOutcomeService = recordSessionOutcomeService;
    this.getCalendarService = getCalendarService;
    this.getSessionDetailService = getSessionDetailService;
    this.currentUserProvider = currentUserProvider;
  }

  @PostMapping("/sessions/propose")
  public ResponseEntity<ApiResponse<SessionResponse>> proposeSession(
      @Valid @RequestBody ProposeSessionRequest req
  ) {
    TutoringSession session = proposeSessionService.proposeSession(
        req.classId(),
        req.tutorId(),
        req.studentId(),
        req.startAt(),
        req.endAt(),
        req.mode(),
        req.placeOrLink(),
        req.repeatNote(),
        req.message(),
        currentUser()
    );
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(SessionResponse.from(session), "Đề xuất lịch học thành công"));
  }

  @PostMapping("/sessions/propose-batch")
  public ResponseEntity<ApiResponse<List<SessionResponse>>> proposeBatch(
      @Valid @RequestBody BatchProposeSessionRequest req
  ) {
    List<BatchProposeSessionService.ProposeItemCommand> items = req.items() == null ? List.of() :
        req.items().stream()
            .map(item -> new BatchProposeSessionService.ProposeItemCommand(
                item.startAt(),
                item.endAt(),
                item.mode(),
                item.placeOrLink(),
                item.repeatNote(),
                item.message()
            ))
            .toList();
    List<TutoringSession> sessions = batchProposeSessionService.proposeBatch(
        req.classId(),
        req.tutorId(),
        req.studentId(),
        items,
        currentUser()
    );
    List<SessionResponse> responseList = sessions.stream().map(SessionResponse::from).toList();
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(responseList, "Đề xuất hàng loạt buổi học thành công"));
  }

  @PostMapping("/sessions/{id}/respond")
  public ResponseEntity<ApiResponse<SessionResponse>> respondToProposal(
      @PathVariable("id") UUID id,
      @Valid @RequestBody RespondProposalRequest req
  ) {
    TutoringSession session = respondToProposalService.respondToProposal(
        id,
        currentUser(),
        req.accept(),
        req.reason()
    );
    return ResponseEntity.ok(ApiResponse.success(SessionResponse.from(session), "Phản hồi đề xuất lịch học thành công"));
  }

  @PostMapping("/sessions/respond-batch")
  public ResponseEntity<ApiResponse<List<SessionResponse>>> respondBatch(
      @Valid @RequestBody BatchRespondSessionRequest req
  ) {
    List<TutoringSession> sessions = batchRespondSessionService.respondBatch(
        req.sessionIds(),
        req.accept(),
        req.reason(),
        currentUser()
    );
    List<SessionResponse> responseList = sessions.stream().map(SessionResponse::from).toList();
    return ResponseEntity.ok(ApiResponse.success(responseList, "Phản hồi hàng loạt đề xuất buổi học thành công"));
  }

  @PostMapping("/sessions/{id}/reschedule")
  public ResponseEntity<ApiResponse<RescheduleProposalResponse>> proposeReschedule(
      @PathVariable("id") UUID id,
      @Valid @RequestBody RescheduleSessionRequest req
  ) {
    SessionRescheduleProposal proposal = rescheduleSessionService.proposeReschedule(
        id,
        currentUser(),
        req.newStartAt(),
        req.newEndAt(),
        req.reason()
    );
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(RescheduleProposalResponse.from(proposal), "Tạo đề xuất đổi lịch thành công"));
  }

  @PostMapping("/sessions/reschedule-proposals/{proposalId}/respond")
  public ResponseEntity<ApiResponse<RescheduleProposalResponse>> respondToReschedule(
      @PathVariable("proposalId") UUID proposalId,
      @Valid @RequestBody RespondRescheduleRequest req
  ) {
    SessionRescheduleProposal proposal = rescheduleSessionService.respondToReschedule(
        proposalId,
        currentUser(),
        req.accept(),
        req.responseReason()
    );
    return ResponseEntity.ok(
        ApiResponse.success(RescheduleProposalResponse.from(proposal), "Phản hồi đề xuất đổi lịch thành công")
    );
  }

  @GetMapping("/sessions/{id}/reschedule-proposal")
  public ResponseEntity<ApiResponse<RescheduleProposalResponse>> getRescheduleProposal(
      @PathVariable("id") UUID id
  ) {
    SessionRescheduleProposal proposal = rescheduleSessionService.findActiveProposalBySessionId(id, currentUser())
        .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy đề xuất đổi lịch đang chờ xử lý cho buổi học: " + id));
    return ResponseEntity.ok(
        ApiResponse.success(RescheduleProposalResponse.from(proposal), "Lấy thông tin đề xuất đổi lịch thành công")
    );
  }

  @PostMapping("/recurring/propose")
  public ResponseEntity<ApiResponse<RecurringProposalResponse>> proposeRecurringSchedule(
      @Valid @RequestBody ProposeRecurringScheduleRequest req
  ) {
    RecurringRescheduleProposal proposal = rescheduleClassScheduleService.proposeRecurringSchedule(
        req.classId(),
        req.effectiveFrom(),
        req.effectiveTo(),
        req.newWeeklySlots(),
        req.mode(),
        req.placeOrLink(),
        req.message(),
        currentUser()
    );
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(RecurringProposalResponse.from(proposal), "Tạo đề xuất đổi lịch học định kỳ thành công"));
  }

  @PostMapping("/recurring/respond")
  public ResponseEntity<ApiResponse<RecurringScheduleResultResponse>> respondRecurringSchedule(
      @Valid @RequestBody RespondRecurringScheduleRequest req
  ) {
    RescheduleClassScheduleService.RecurringScheduleResult result = rescheduleClassScheduleService.respondRecurringSchedule(
        req.proposalId(),
        req.accept(),
        req.reason(),
        currentUser()
    );
    List<SessionResponse> sessionResponses = result.generatedSessions().stream()
        .map(SessionResponse::from)
        .toList();
    RecurringScheduleResultResponse response = new RecurringScheduleResultResponse(
        RecurringProposalResponse.from(result.proposal()),
        result.cancelledSessionCount(),
        sessionResponses
    );
    return ResponseEntity.ok(ApiResponse.success(response, "Phản hồi đề xuất đổi lịch học định kỳ thành công"));
  }

  @GetMapping("/recurring/proposals/{proposalId}")
  public ResponseEntity<ApiResponse<RecurringProposalResponse>> getRecurringProposal(
      @PathVariable("proposalId") UUID proposalId
  ) {
    RecurringRescheduleProposal proposal = rescheduleClassScheduleService.findProposalById(proposalId)
        .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy đề xuất đổi lịch định kỳ với ID: " + proposalId));
    return ResponseEntity.ok(ApiResponse.success(RecurringProposalResponse.from(proposal), "Lấy thông tin đề xuất đổi lịch định kỳ thành công"));
  }

  @GetMapping("/recurring/classes/{classId}/active-proposal")
  public ResponseEntity<ApiResponse<RecurringProposalResponse>> getActiveRecurringProposal(
      @PathVariable("classId") UUID classId
  ) {
    RecurringProposalResponse response = rescheduleClassScheduleService.findActiveProposalByClassId(classId)
        .map(RecurringProposalResponse::from)
        .orElse(null);
    return ResponseEntity.ok(ApiResponse.success(response, "Lấy thông tin đề xuất đổi lịch định kỳ của lớp học"));
  }

  @PostMapping("/sessions/{id}/cancel")
  public ResponseEntity<ApiResponse<SessionResponse>> cancelSession(
      @PathVariable("id") UUID id,
      @Valid @RequestBody CancelSessionRequest req
  ) {
    TutoringSession session = cancelSessionService.cancelSession(
        id,
        currentUser(),
        req.reason(),
        req.comment()
    );
    return ResponseEntity.ok(ApiResponse.success(SessionResponse.from(session), "Hủy buổi học thành công"));
  }

  @PostMapping("/sessions/{id}/outcome")
  public ResponseEntity<ApiResponse<SessionResponse>> recordOutcome(
      @PathVariable("id") UUID id,
      @Valid @RequestBody RecordOutcomeRequest req
  ) {
    TutoringSession session = recordSessionOutcomeService.recordOutcome(
        id,
        currentUser(),
        req.outcome(),
        req.noteContent()
    );
    return ResponseEntity.ok(ApiResponse.success(SessionResponse.from(session), "Ghi nhận kết quả buổi học thành công"));
  }

  @GetMapping("/sessions/{id}")
  public ResponseEntity<ApiResponse<SessionResponse>> getSessionDetail(
      @PathVariable("id") UUID id
  ) {
    TutoringSession session = getSessionDetailService.getSessionDetail(id, currentUser());
    return ResponseEntity.ok(ApiResponse.success(SessionResponse.from(session), "Lấy thông tin buổi học thành công"));
  }

  @GetMapping("/calendar")
  public ResponseEntity<ApiResponse<List<CalendarItemResponse>>> getCalendar(
      @RequestParam("from") Instant from,
      @RequestParam("to") Instant to,
      @RequestParam(value = "studentId", required = false) UUID studentId
  ) {
    List<TutoringSession> sessions = getCalendarService.getCalendarSessions(currentUser(), from, to, studentId);
    List<CalendarItemResponse> responseList = sessions.stream()
        .map(CalendarItemResponse::from)
        .toList();
    return ResponseEntity.ok(ApiResponse.success(responseList, "Lấy danh sách lịch học thành công"));
  }

  private CurrentUser currentUser() {
    CurrentUser currentUser = currentUserProvider.getIfAvailable();
    if (currentUser == null || currentUser.getUserId() == null) {
      throw new InvalidOperationException(ErrorCode.UNAUTHORIZED, "Phiên làm việc chưa được xác thực hoặc đã hết hạn");
    }
    return currentUser;
  }
}
