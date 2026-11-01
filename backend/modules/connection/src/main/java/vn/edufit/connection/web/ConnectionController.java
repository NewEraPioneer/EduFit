package vn.edufit.connection.web;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.*;
import vn.edufit.connection.application.service.ConnectionRequestService;
import vn.edufit.connection.application.service.ConnectionQueryService;
import vn.edufit.connection.application.service.LinkInvitationService;
import vn.edufit.connection.application.service.ParentStudentLinkService;
import vn.edufit.connection.application.dto.*;
import vn.edufit.connection.web.request.*;
import vn.edufit.connection.web.response.*;
import vn.edufit.shared.auth.CurrentUser;
import vn.edufit.shared.exception.ResourceConflictException;
import vn.edufit.shared.exception.ErrorCode;
import vn.edufit.shared.response.ApiResponse;

@RestController
@RequestMapping("/api/v1/connections")
public class ConnectionController {
  private final ConnectionRequestService service;
  private final LinkInvitationService invitationService;
  private final ParentStudentLinkService linkService;
  private final ObjectProvider<CurrentUser> users;
  private final ConnectionQueryService queries;

  public ConnectionController(ConnectionRequestService service, LinkInvitationService invitationService,
      ParentStudentLinkService linkService, ConnectionQueryService queries, ObjectProvider<CurrentUser> users) {
    this.service = service;
    this.invitationService = invitationService;
    this.linkService = linkService;
    this.users = users;
    this.queries = queries;
  }

  // Map only after the service proxy returns (and its owned transaction has completed).
  private static <T> T value(TransitionResult<T> result) {
    return switch (result) {
      case TransitionResult.Success<T> success -> success.value();
      case TransitionResult.Conflict<T> conflict -> throw new ResourceConflictException(conflict.message());
    };
  }

  private CurrentUser user() { return users.getObject(); }

  @PostMapping("/parent-student/invite")
  public ApiResponse<Void> invite(@Valid @RequestBody InviteBody body) {
    if (!invitationService.invite(user(), body.targetEmail()))
      throw new ResourceConflictException(ErrorCode.RATE_LIMIT_EXCEEDED,
          "Đã đạt giới hạn 10 lời mời trong một giờ");
    return ApiResponse.ok("Gửi lời mời liên kết thành công");
  }

  @GetMapping("/parent-student/invitations")
  public ApiResponse<List<InvitationResponse>> invitations() {
    return ApiResponse.success(queries.invitations(user()).stream().map(InvitationResponse::of).toList());
  }

  @GetMapping("/parent-student/invitations/page")
  public ApiResponse<PageResponse<InvitationResponse>> invitationsPage(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    PageResponse<InvitationResult> p = queries.invitationsPage(user(), page, size);
    List<InvitationResponse> responses = p.items().stream().map(InvitationResponse::of).toList();
    return ApiResponse.success(new PageResponse<>(responses, p.page(), p.size(), p.totalElements(), p.totalPages()));
  }

  @PostMapping("/parent-student/respond")
  public ApiResponse<LinkResponse> respond(@Valid @RequestBody RespondBody body) {
    ParentStudentLinkResult link = value(invitationService.respondInvitation(user(), body.invitationId(), body.accept()));
    return ApiResponse.success(link == null ? null : LinkResponse.of(link));
  }

  @PostMapping("/parent-student/invitations/{id}/withdraw")
  public ApiResponse<Void> withdraw(@PathVariable UUID id) {
    value(invitationService.withdrawInvitation(user(), id));
    return ApiResponse.ok();
  }

  @GetMapping("/parent-student/links")
  public ApiResponse<List<LinkResponse>> links() {
    return ApiResponse.success(queries.links(user()).stream().map(LinkResponse::of).toList());
  }

  @GetMapping("/parent-student/links/page")
  public ApiResponse<PageResponse<LinkResponse>> linksPage(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    PageResponse<ParentStudentLinkResult> p = queries.linksPage(user(), page, size);
    List<LinkResponse> responses = p.items().stream().map(LinkResponse::of).toList();
    return ApiResponse.success(new PageResponse<>(responses, p.page(), p.size(), p.totalElements(), p.totalPages()));
  }

  @GetMapping("/admin/parent-student/links")
  public ApiResponse<PageResponse<LinkResponse>> adminLinksPage(
      @RequestParam("email") String email,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    PageResponse<ParentStudentLinkResult> p = queries.adminLinksPage(user(), email, page, size);
    List<LinkResponse> responses = p.items().stream().map(LinkResponse::of).toList();
    return ApiResponse.success(new PageResponse<>(responses, p.page(), p.size(), p.totalElements(), p.totalPages()));
  }

  @GetMapping("/admin/parent-student/links/all")
  public ApiResponse<PageResponse<AdminParentStudentLinkResponse>> adminAllLinks(
      @RequestParam(name = "status", required = false) String status,
      @RequestParam(name = "search", required = false) String search,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return ApiResponse.success(queries.adminGetAllLinks(user(), status, search, page, size));
  }

  @GetMapping("/admin/parent-student/links/stats")
  public ApiResponse<AdminParentStudentLinkStatsResponse> adminLinkStats() {
    return ApiResponse.success(queries.adminGetLinkStats(user()));
  }

  @PostMapping("/admin/parent-student/links/{id}/revoke")
  public ApiResponse<Void> adminRevokeLink(
      @PathVariable UUID id,
      @RequestBody(required = false) RevokeBody body) {
    String reason = (body != null && body.reason() != null && !body.reason().isBlank())
        ? body.reason()
        : "Hủy liên kết bởi Quản trị viên hệ thống";
    linkService.revokeLink(user(), id, reason);
    return ApiResponse.ok("Đã hủy liên kết thành công");
  }

  @DeleteMapping("/parent-student/links/{id}")
  public ApiResponse<Void> revoke(@PathVariable UUID id, @RequestBody(required = false) RevokeBody body) {
    linkService.revokeLink(user(), id, body == null ? null : body.reason());
    return ApiResponse.ok();
  }

  @PostMapping("/requests")
  public ApiResponse<RequestResponse> request(@Valid @RequestBody CreateRequestBody body) {
    return ApiResponse.success(RequestResponse.of(value(service.request(user(), body.needId(), body.tutorId(),
        body.desiredSlots(), body.message()))));
  }

  @GetMapping("/requests")
  public ApiResponse<?> requests(@RequestParam(required = false) UUID studentId) {
    CurrentUser actor = user();
    if (actor.hasRole("TUTOR")) return ApiResponse.success(queries.tutorInbox(actor).stream()
        .map(TutorInboxResponse::of).toList());
    return ApiResponse.success(queries.requests(actor, studentId).stream().map(RequestResponse::of).toList());
  }

  @GetMapping("/requests/page")
  public ApiResponse<?> requestsPage(
      @RequestParam(required = false) UUID studentId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    CurrentUser actor = user();
    if (actor.hasRole("TUTOR")) {
      PageResponse<TutorInboxResult> p = queries.tutorInboxPage(actor, page, size);
      List<TutorInboxResponse> responses = p.items().stream().map(TutorInboxResponse::of).toList();
      return ApiResponse.success(new PageResponse<>(responses, p.page(), p.size(), p.totalElements(), p.totalPages()));
    }
    PageResponse<ConnectionRequestResult> p = queries.requestsPage(actor, studentId, page, size);
    List<RequestResponse> responses = p.items().stream().map(RequestResponse::of).toList();
    return ApiResponse.success(new PageResponse<>(responses, p.page(), p.size(), p.totalElements(), p.totalPages()));
  }

  @PostMapping("/requests/{id}/cancel")
  public ApiResponse<Void> cancel(@PathVariable UUID id) {
    value(service.cancelRequest(user(), id));
    return ApiResponse.ok();
  }

  @PostMapping("/requests/{id}/respond")
  public ApiResponse<ClassResponse> respondRequest(@PathVariable UUID id,
      @Valid @RequestBody RequestRespondBody body) {
    ClassResult c = value(service.respondRequest(user(), id, body.accept(), body.reason()));
    return ApiResponse.success(c == null ? null : ClassResponse.of(c));
  }

  @GetMapping("/classes")
  public ApiResponse<List<TutoringClassResponse>> classes() {
    List<TutoringClassResponse> items = queries.classes(user()).stream()
        .map(TutoringClassResponse::of)
        .toList();
    return ApiResponse.success(items);
  }

  @PostMapping("/classes/{id}/end")
  public ApiResponse<TutoringClassResponse> endClass(
      @PathVariable UUID id,
      @Valid @RequestBody EndClassBody body) {
    String reason = body != null ? body.reason() : null;
    TutoringClassResult result = value(service.endClass(user(), id, reason));
    return ApiResponse.success(TutoringClassResponse.of(result));
  }
}
