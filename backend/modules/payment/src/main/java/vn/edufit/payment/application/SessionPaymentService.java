package vn.edufit.payment.application;

import java.time.*;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import vn.edufit.connection.api.ConnectionFacade;
import vn.edufit.payment.application.PaymentViews.*;
import vn.edufit.payment.domain.SessionCharge;
import vn.edufit.payment.infra.PaymentStore;
import vn.edufit.profile.api.ProfileFacade;
import vn.edufit.scheduling.api.SessionFacade;
import vn.edufit.scheduling.api.dto.SessionSummaryView;
import vn.edufit.shared.auth.CurrentUser;
import vn.edufit.shared.exception.*;

@Service
public class SessionPaymentService {
  private static final Logger log = LoggerFactory.getLogger(SessionPaymentService.class);
  private final PaymentStore store;
  private final PaymentAccess access;
  private final SessionFacade sessions;
  private final ProfileFacade profiles;
  private final ConnectionFacade connections;
  private final PaymentTime clock;

  public SessionPaymentService(
      PaymentStore store,
      PaymentAccess access,
      SessionFacade sessions,
      ProfileFacade profiles,
      ConnectionFacade connections,
      PaymentTime clock) {
    this.store = store;
    this.access = access;
    this.sessions = sessions;
    this.profiles = profiles;
    this.connections = connections;
    this.clock = clock;
  }

  public Session fund(CurrentUser actor, UUID sessionId, long expectedAmount) {
    access.learnerRole(actor);
    return store.locked(
        () -> {
          var source = source(sessionId);
          if (!access.student(actor, source.studentId())
              && !access.linkedParent(actor, source.studentId()))
            throw new ForbiddenOperationException("Không có quyền cấp tiền cho học sinh này");
          var existing = store.find(sessionId);
          if (existing.isPresent()) {
            if (!existing.get().payerId().equals(actor.getUserId()))
              throw new InvalidOperationException("Buổi học đã được cấp tiền từ ví khác");
            return view(existing.get(), source, actor);
          }
          if (!Set.of("SCHEDULED", "COMPLETED").contains(source.status())
              || !clock.instant().isBefore(fundingCutoff(source)))
            throw new InvalidOperationException("Buổi học không còn được cấp tiền");
          var tutor =
              profiles
                  .findTutorById(source.tutorId())
                  .orElseThrow(() -> new InvalidOperationException("Không tìm thấy gia sư"));
          if (classPrice(source.classId()) != expectedAmount)
            throw new InvalidOperationException(
                "Đơn giá đã thay đổi, hãy tải lại và kiểm tra học phí");
          if (actor.getUserId().equals(tutor.userId()))
            throw new InvalidOperationException("Không thể tự thanh toán cho mình");
          var state = new SessionCharge(expectedAmount, source.endAt());
          var c =
              new Charge(
                  sessionId,
                  source.classId(),
                  source.studentId(),
                  actor.getUserId(),
                  tutor.userId(),
                  1000,
                  state);
          syncReview(c, source, clock.instant());
          store.insert(c);
          store.move(
              c.payerId(),
              -state.amount,
              state.amount,
              "reserve:" + sessionId,
              "SESSION_RESERVED",
              sessionId);
          store.audit(
              actor.getUserId(),
              sessionId,
              "SESSION_FUNDED",
              "VND=" + expectedAmount + "; commissionBps=1000");
          store.save(c);
          return view(c, source, actor);
        });
  }

  public Session act(CurrentUser actor, UUID id, String action, String text, Long amount) {
    return act(actor, id, action, text, amount, "OTHER", null);
  }

  public Session act(CurrentUser actor, UUID id, String action, String text, Long amount,
      String category, String evidence) {
    access.authenticated(actor);
    return store.locked(
        () -> {
          var c = charge(id);
          access.participant(actor, c);
          var s = c.state();
          var source = source(id);
          boolean before = s.terminal();
          boolean tutor = access.tutor(actor, c);
          s.endAt = source.endAt();
          syncReview(c, source, clock.instant());
          try {
            s.tick(clock.instant(), "COMPLETED".equals(source.status()));
            switch (action) {
              case "confirm" ->
                  s.confirm(tutor, clock.instant(), "COMPLETED".equals(source.status()));
              case "dispute" -> {
                if (Set.of("DISPUTED", "AWAITING_ADMIN", "ADMIN_REVIEW").contains(s.status)) {
                  finish(c, before);
                  return view(c, source, actor);
                }
                if (!Set.of("DURATION", "NO_SHOW", "TECHNICAL", "QUALITY", "OTHER").contains(category)
                    || evidence != null && evidence.length() > 4000)
                  throw new InvalidOperationException("Loại vấn đề hoặc bằng chứng không hợp lệ");
                s.dispute(text, clock.instant());
                s.disputeCategory = category;
                s.disputeEvidence = evidence;
                s.disputeReporterId = actor.getUserId();
              }
              case "report" -> {
                if (!tutor && !access.payer(actor, c))
                  throw new ForbiddenOperationException("Chỉ người trả tiền hoặc gia sư được đề xuất mức giải quyết");
                if (amount == null) throw new InvalidOperationException("Cần số tiền thống nhất");
                s.report(tutor, amount, text, clock.instant());
              }
              default -> throw new InvalidOperationException("Thao tác không hợp lệ");
            }
          } catch (IllegalStateException e) {
            throw new InvalidOperationException(e.getMessage());
          }
          finish(c, before);
          store.audit(
              actor.getUserId(),
              id,
              "SESSION_" + action.toUpperCase(Locale.ROOT),
              text == null ? action : text);
          return view(c, source, actor);
        });
  }

  public Session review(CurrentUser actor, UUID id, boolean approve, String note) {
    access.admin(actor);
    if (note == null || note.isBlank() || note.length() > 2000)
      throw new InvalidOperationException("Cần lý do duyệt/từ chối");
    return store.locked(
        () -> {
          var c = charge(id);
          var s = c.state();
          if (!Set.of("AWAITING_ADMIN", "ADMIN_REVIEW").contains(s.status))
            throw new InvalidOperationException("Khoản này chưa có hai báo cáo thống nhất");
          if (approve && s.status.equals("ADMIN_REVIEW"))
            throw new InvalidOperationException("Cần nhập số tiền và lý do qua chức năng phân xử");
          var source = source(id);
          if (approve
              && s.learnerAmount != null
              && s.learnerAmount > 0
              && (!"COMPLETED".equals(source.status()) || clock.instant().isBefore(source.endAt())))
            throw new InvalidOperationException(
                "Chỉ tất toán học phí khi buổi học đã hoàn thành; hãy từ chối và hoàn ví nếu buổi"
                    + " bị hủy/vắng mặt");
          if (approve) s.approve();
          else s.refund();
          s.resolutionNote = note.trim();
          s.resolvedAt = clock.instant();
          finish(c, false);
          store.audit(actor.getUserId(), id, approve ? "ADMIN_SETTLED" : "ADMIN_REFUNDED", note);
          return view(c, source(id), actor);
        });
  }

  public List<Session> list(CurrentUser actor, Instant from, Instant to) {
    access.authenticated(actor);
    if (from == null || to == null || !to.isAfter(from) || Duration.between(from, to).toDays() > 93)
      throw new InvalidOperationException("Chọn khoảng thời gian tối đa 93 ngày");
    var classes = new ArrayList<ConnectionFacade.ClassDetailView>();
    if (actor.hasRole("TUTOR"))
      classes.addAll(
          connections.findClassesByTutor(profiles.getTutorIdByUserId(actor.getUserId())));
    else if (actor.hasRole("STUDENT"))
      classes.addAll(
          connections.findClassesByStudent(profiles.getStudentIdByUserId(actor.getUserId())));
    else if (actor.hasRole("PARENT"))
      for (UUID student : connections.findConfirmedStudentIds(actor.getUserId()))
        classes.addAll(connections.findClassesByStudent(student));
    else throw new ForbiddenOperationException("Chọn màn hình quản trị để xem Payment");
    if (actor.hasRole("PARENT")) {
      var known = classes.stream().map(ConnectionFacade.ClassDetailView::classId).collect(java.util.stream.Collectors.toSet());
      for (var cls : connections.findClassesByIds(store.payerClassIds(actor.getUserId()))) {
        if (known.add(cls.classId())) classes.add(cls);
      }
    }
    var result = new ArrayList<Session>();
    for (var cls : classes) {
      var tutor = profiles.findTutorById(cls.tutorId()).orElse(null);
      if (tutor == null) continue;
      for (var timeline : sessions.findSessionsByClassId(cls.classId(), from, to)) {
        var saved = store.find(timeline.sessionId());
        // A revoked parent retains only their own transactions, never the child's other sessions.
        if (actor.hasRole("PARENT") && !access.linkedParent(actor, cls.studentId())
            && (saved.isEmpty() || !access.payer(actor, saved.get()))) continue;
        var source =
            new SessionSummaryView(
                timeline.sessionId(),
                cls.classId(),
                cls.tutorId(),
                cls.studentId(),
                timeline.startAt(),
                timeline.endAt(),
                "",
                timeline.status(),
                false,
                timeline.startAt(), timeline.outcomeRecordedAt());
        if (saved.isPresent()) result.add(view(saved.get(), source, actor));
        else
          result.add(
              new Session(
                  source.sessionId(),
                  cls.classId(),
                  cls.studentId(),
                  tutor.displayName(),
                  source.status(),
                  source.startAt(),
                  source.endAt(),
                  cls.pricePerSession(),
                  1000,
                  null,
                  "UNFUNDED",
                  false,
                  false,
                  false,
                  null,
                  null,
                  null,
                  null,
                  null,
                  null,
                  0,
                  (access.student(actor, cls.studentId())
                          || access.linkedParent(actor, cls.studentId()))
                      && Set.of("SCHEDULED", "COMPLETED").contains(source.status())
                      && clock.instant().isBefore(fundingCutoff(source)),
                  false,
                  actor.hasRole("TUTOR") && actor.getUserId().equals(tutor.userId())));
      }
    }
    return result.stream().sorted(Comparator.comparing(Session::startAt).reversed()).toList();
  }

  public Session get(CurrentUser actor, UUID id) {
    access.authenticated(actor);
    var src = source(id);
    var tutor =
        profiles
            .findTutorById(src.tutorId())
            .orElseThrow(() -> new InvalidOperationException("Không tìm thấy gia sư"));
    var saved = store.find(id);
    if (!access.student(actor, src.studentId())
        && !access.linkedParent(actor, src.studentId())
        && !saved.map(c -> access.payer(actor, c)).orElse(false)
        && !(actor.hasRole("TUTOR") && actor.getUserId().equals(tutor.userId())))
      throw new ForbiddenOperationException("Không có quyền xem học phí buổi này");
    if (saved.isPresent()) return view(saved.get(), src, actor);
    boolean canFund =
        (access.student(actor, src.studentId()) || access.linkedParent(actor, src.studentId()))
            && Set.of("SCHEDULED", "COMPLETED").contains(src.status())
            && clock.instant().isBefore(fundingCutoff(src));
    return new Session(
        id,
        src.classId(),
        src.studentId(),
        tutor.displayName(),
        src.status(),
        src.startAt(),
        src.endAt(),
        classPrice(src.classId()),
        1000,
        null,
        "UNFUNDED",
        false,
        false,
        false,
        null,
        null,
        null,
        null,
        null,
        null,
        0,
        canFund,
        false,
        actor.hasRole("TUTOR") && actor.getUserId().equals(tutor.userId()));
  }

  public List<Session> adminList(CurrentUser actor, int offset) {
    access.admin(actor);
    return store.adminCharges(offset).stream()
        .map(c -> view(c, source(c.sessionId()), actor))
        .toList();
  }

  public void processDeadlines() {
    for (var candidate : store.pending()) {
      try {
        store.locked(
            () -> {
              var c = charge(candidate.sessionId());
              var s = c.state();
              if (s.terminal()) return null;
              var src = source(c.sessionId());
              s.endAt = src.endAt();
              syncReview(c, src, clock.instant());
              if (Set.of("DISPUTED", "AWAITING_ADMIN", "ADMIN_REVIEW").contains(s.status))
                s.tick(clock.instant(), "COMPLETED".equals(src.status()));
              else if (Set.of("CANCELLED", "ABSENT", "REJECTED", "WITHDRAWN", "EXPIRED")
                  .contains(src.status())) s.refund();
              else s.tick(clock.instant(), "COMPLETED".equals(src.status()));
              finish(c, false);
              return null;
            });
      } catch (Exception e) {
        log.error("Payment deadline failed for session {}", candidate.sessionId(), e);
      }
    }
  }

  private void finish(Charge c, boolean wasTerminal) {
    var s = c.state();
    if (!wasTerminal && s.terminal()) {
      if (s.resolvedAt == null) s.resolvedAt = clock.instant();
      long fee = Math.multiplyExact(s.settledAmount, c.commissionBps()) / 10000;
      store.move(
          c.payerId(),
          s.amount - s.settledAmount,
          -s.amount,
          "settle-payer:" + c.sessionId(),
          "SESSION_SETTLED",
          c.sessionId());
      if (s.settledAmount > 0) {
        store.move(
            c.tutorUserId(),
            s.settledAmount - fee,
            0,
            "settle-tutor:" + c.sessionId(),
            "TUITION_INCOME",
            c.sessionId());
        store.move(null, fee, 0, "commission:" + c.sessionId(), "COMMISSION", c.sessionId());
      }
      store.audit(
          null,
          c.sessionId(),
          "FINANCIAL_RESULT",
          s.status + "; amount=" + s.settledAmount + "; fee=" + fee);
    }
    store.save(c);
  }

  private Instant fundingCutoff(SessionSummaryView source) {
    Instant base = source.outcomeRecordedAt() != null && source.outcomeRecordedAt().isAfter(source.endAt())
        ? source.outcomeRecordedAt() : source.endAt();
    return base.plusSeconds(SessionCharge.CONFIRMATION_SECONDS);
  }

  private void syncReview(Charge charge, SessionSummaryView source, Instant observedAt) {
    if ("COMPLETED".equals(source.status())) charge.state().beginReview(observedAt);
  }

  public void outcomeRecorded(UUID sessionId, Instant recordedAt) {
    store.locked(() -> {
      var found = store.find(sessionId);
      if (found.isPresent()) {
        var c = found.get();
        var src = source(sessionId);
        syncReview(c, src, recordedAt);
        store.save(c);
      }
      return null;
    });
  }

  public Session adminGet(CurrentUser actor, UUID id) {
    access.admin(actor);
    return view(charge(id), source(id), actor);
  }

  public Session comment(CurrentUser actor, UUID sessionId, UUID messageId, String text) {
    access.authenticated(actor);
    if (messageId == null || text == null || text.isBlank() || text.length() > 4000)
      throw new InvalidOperationException("Cần phản hồi tối đa 4000 ký tự");
    return store.locked(() -> {
      var c = charge(sessionId);
      access.participant(actor, c);
      if (!Set.of("DISPUTED", "AWAITING_ADMIN", "ADMIN_REVIEW").contains(c.state().status))
        throw new InvalidOperationException("Chỉ bổ sung thông tin cho hồ sơ vấn đề đang xử lý");
      String side = access.tutor(actor, c) ? "TUTOR" : access.payer(actor, c) ? "PAYER" : "STUDENT";
      store.addCaseMessage(c, actor.getUserId(), messageId, side, text.trim());
      return view(c, source(sessionId), actor);
    });
  }

  public Session resolve(CurrentUser actor, UUID id, long amount, String note) {
    access.admin(actor);
    if (note == null || note.isBlank() || note.length() > 2000)
      throw new InvalidOperationException("Cần ghi rõ lý do phân xử");
    return store.locked(() -> {
      var c = charge(id);
      var src = source(id);
      if (amount > 0 && clock.instant().isBefore(src.endAt()))
        throw new InvalidOperationException("Chỉ phân xử học phí sau giờ kết thúc buổi học");
      try { c.state().resolve(amount); }
      catch (IllegalStateException e) { throw new InvalidOperationException(e.getMessage()); }
      c.state().resolutionNote = note.trim();
      c.state().resolvedAt = clock.instant();
      finish(c, false);
      store.audit(actor.getUserId(), id, "ADMIN_RESOLVED", "VND=" + amount + "; " + note);
      return view(c, src, actor);
    });
  }

  /** Học phí mỗi buổi được chốt trên lớp khi gia sư nhận yêu cầu kết nối. */
  private long classPrice(UUID classId) {
    return connections.findClassById(classId)
        .map(ConnectionFacade.ClassDetailView::pricePerSession)
        .orElseThrow(() -> new InvalidOperationException("Không tìm thấy lớp học của buổi này"));
  }

  private SessionSummaryView source(UUID id) {
    return sessions
        .findSessionById(id)
        .orElseThrow(() -> new InvalidOperationException("Không tìm thấy buổi học"));
  }

  private Charge charge(UUID id) {
    return store
        .find(id)
        .orElseThrow(() -> new InvalidOperationException("Buổi học chưa được cấp tiền"));
  }

  private Session view(Charge c, SessionSummaryView src, CurrentUser actor) {
    var s = c.state();
    String name = profiles.findTutorById(src.tutorId()).map(t -> t.displayName()).orElse("Gia sư");
    return new Session(
        c.sessionId(),
        c.classId(),
        c.studentId(),
        name,
        src.status(),
        src.startAt(),
        src.endAt(),
        s.amount,
        c.commissionBps(),
        c.payerId(),
        s.status,
        s.learnerAccepted,
        s.tutorAccepted,
        s.autoAccepted,
        s.disputeOpenedAt == null ? null : s.disputeOpenedAt.plusSeconds(SessionCharge.DISPUTE_SECONDS),
        s.disputeReason,
        s.learnerAmount,
        s.tutorAmount,
        s.learnerReport,
        s.tutorReport,
        s.settledAmount,
        false,
        access.learner(actor, c),
        access.tutor(actor, c), s.reviewDeadline(),
        access.tutor(actor, c) || access.payer(actor, c), access.payer(actor, c),
        s.disputeCategory, s.disputeEvidence, s.disputeReporterId, s.resolutionNote, s.resolvedAt,
        s.disputeReason == null ? List.of() : store.caseMessages(c.sessionId()));
  }
}
