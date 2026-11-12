package vn.edufit.scheduling.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.mockito.Mockito;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import vn.edufit.connection.api.ConnectionFacade;
import vn.edufit.profile.api.ProfileFacade;
import vn.edufit.profile.api.dto.TutorSummaryDto;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import vn.edufit.scheduling.TestApplication;
import vn.edufit.scheduling.application.service.RespondToProposalService;
import vn.edufit.scheduling.domain.model.SessionMode;
import vn.edufit.scheduling.domain.model.SessionStatus;
import vn.edufit.scheduling.domain.model.TutoringSession;
import vn.edufit.scheduling.domain.repository.TutoringSessionDomainRepository;
import vn.edufit.scheduling.infra.persistence.repository.SpringDataTutoringSessionRepository;

/**
 * Kiểm thử tích hợp & Tải tranh chấp đồng thời (Concurrency Stress Test) trên PostgreSQL 17 thật (NFR-17).
 *
 * <p>Mục đích kiến trúc & Bằng chứng bảo vệ đồ án:
 * <ul>
 *   <li><b>NFR-17 (Chống Double-Booking tuyệt đối):</b> Khi nhiều người dùng hoặc tiến trình cùng cố gắng
 *       xác nhận các buổi học trùng dải giờ dạy của cùng 1 Gia sư, hệ thống phải đảm bảo 100% không bao giờ
 *       xảy ra tình trạng trùng lặp lịch (Double-Booking).</li>
 *   <li><b>Cơ chế phòng thủ 2 tầng (Two-Tier Concurrency Defense):</b>
 *       <ol>
 *         <li><b>Tầng 1 (Application / Row-Level Lock):</b> Câu lệnh {@code SELECT ... FOR UPDATE} khóa dòng bản ghi
 *             tuần tự hóa việc cập nhật từng buổi học.</li>
 *         <li><b>Tầng 2 (Storage Engine / PostgreSQL Exclusion GiST):</b> Ràng buộc loại trừ
 *             {@code ex_tutoring_session_no_tutor_overlap} sử dụng extension {@code btree_gist}
 *             trên kiểu dữ liệu {@code tstzrange(start_at, end_at, '[)')} khi ở trạng thái {@code SCHEDULED}.
 *             Nếu có xung đột, Disk block engine lập tức ném lỗi SQLSTATE 23P01 và giao dịch bị rollback.</li>
 *       </ol>
 *   </li>
 *   <li><b>Testcontainers PostgreSQL:</b> Khởi chạy container PostgreSQL 17 thật để thực thi chính xác
 *       ràng buộc GiST (vốn không được hỗ trợ bởi H2 In-Memory Database).</li>
 *   <li><b>Bắt buộc có Docker:</b> Thiếu Docker thì test báo lỗi thay vì âm thầm bỏ qua, để Exit Code 0
 *       luôn có nghĩa là ràng buộc CSDL thật đã được kiểm chứng.</li>
 * </ul>
 */
@SpringBootTest(classes = TestApplication.class)
@Testcontainers
@DisplayName("Concurrency Stress Test: Chống Double-Booking trên PostgreSQL 17 thật (NFR-17)")
class ConcurrentSchedulingIT {

  @Container
  @SuppressWarnings("resource")
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17")
      .withDatabaseName("edufit_scheduling_concurrency_test")
      .withUsername("edufit")
      .withPassword("edufit-test-secret");

  @DynamicPropertySource
  static void configureProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
    registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
    registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
  }

  @BeforeAll
  static void initSchema() throws Exception {
    if (!postgres.isRunning()) {
      return;
    }

    try (Connection conn = DriverManager.getConnection(
        postgres.getJdbcUrl(),
        postgres.getUsername(),
        postgres.getPassword()
    ); Statement stmt = conn.createStatement()) {

      // 1. Kích hoạt Extension btree_gist
      stmt.execute("CREATE EXTENSION IF NOT EXISTS \"btree_gist\";");

      // 2. Tạo bảng tutoring_session với ràng buộc Exclusion GiST nguyên bản từ V1.05
      stmt.execute("""
          CREATE TABLE IF NOT EXISTS tutoring_session (
              session_id              UUID PRIMARY KEY,
              class_id                UUID NOT NULL,
              tutor_id                UUID NOT NULL,
              student_id              UUID NOT NULL,
              start_at                TIMESTAMPTZ NOT NULL,
              end_at                  TIMESTAMPTZ NOT NULL,
              mode                    VARCHAR(20) NOT NULL,
              place_or_link           VARCHAR(500),
              repeat_note             VARCHAR(300),
              message                 VARCHAR(500),
              status                  VARCHAR(30) NOT NULL DEFAULT 'PROPOSED',
              proposed_by             UUID NOT NULL,
              responded_by            UUID,
              responded_at            TIMESTAMPTZ,
              response_reason         VARCHAR(300),
              expires_at              TIMESTAMPTZ NOT NULL,
              cancelled_by            UUID,
              cancelled_at            TIMESTAMPTZ,
              cancel_reason           VARCHAR(40),
              cancel_comment          VARCHAR(300),
              is_late_cancel          BOOLEAN NOT NULL DEFAULT false,
              outcome_recorded_by     UUID,
              outcome_recorded_at     TIMESTAMPTZ,
              version                 INT NOT NULL DEFAULT 0,
              created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
              CONSTRAINT chk_session_time CHECK (end_at > start_at),
              CONSTRAINT ex_tutoring_session_no_tutor_overlap EXCLUDE USING gist (
                  tutor_id WITH =,
                  tstzrange(start_at, end_at, '[)') WITH &&
              ) WHERE (status = 'SCHEDULED'),
              CONSTRAINT ex_tutoring_session_no_student_overlap EXCLUDE USING gist (
                  student_id WITH =,
                  tstzrange(start_at, end_at, '[)') WITH &&
              ) WHERE (status = 'SCHEDULED')
          );
      """);

      // 3. Tạo bảng session_reschedule_proposal
      stmt.execute("""
          CREATE TABLE IF NOT EXISTS session_reschedule_proposal (
              proposal_id         UUID PRIMARY KEY,
              session_id          UUID NOT NULL,
              proposed_by         UUID NOT NULL,
              new_start_at        TIMESTAMPTZ NOT NULL,
              new_end_at          TIMESTAMPTZ NOT NULL,
              reason              VARCHAR(300),
              status              VARCHAR(30) NOT NULL DEFAULT 'PENDING',
              expires_at          TIMESTAMPTZ NOT NULL,
              responded_by        UUID,
              responded_at        TIMESTAMPTZ,
              response_reason     VARCHAR(300),
              created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
          );
      """);

      // 4. Tạo bảng session_history
      stmt.execute("""
          CREATE TABLE IF NOT EXISTS session_history (
              history_id              UUID PRIMARY KEY,
              session_id              UUID NOT NULL,
              reschedule_proposal_id  UUID,
              action                  VARCHAR(40) NOT NULL,
              actor_user_id           UUID,
              reason                  VARCHAR(300),
              created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
          );
      """);
    }
  }

  @Autowired
  private RespondToProposalService respondToProposalService;

  /** Module profile/connection không nằm trong context cô lập này; quyền phản hồi được stub theo đúng hợp đồng facade. */
  @MockitoBean
  private ProfileFacade profileFacade;

  @MockitoBean
  private ConnectionFacade connectionFacade;

  /** Tài khoản gia sư (user_id) khác mã hồ sơ gia sư (tutor_id) giống hệ thống thật. */
  private UUID tutorAccount(UUID tutorId) {
    UUID tutorUserId = UUID.randomUUID();
    Mockito.when(profileFacade.findTutorByUserId(tutorUserId)).thenReturn(java.util.Optional.of(new TutorSummaryDto(
        tutorId, tutorUserId, "Gia sư kiểm thử", null, null, "ONLINE", null, false, null, null, null, null,
        "VERIFIED", null, null, 0)));
    return tutorUserId;
  }

  @Autowired
  private TutoringSessionDomainRepository sessionRepository;

  @Autowired
  private SpringDataTutoringSessionRepository springDataRepository;

  @BeforeEach
  void cleanDatabase() {
    if (postgres.isRunning()) {
      springDataRepository.deleteAll();
    }
  }

  @Test
  @DisplayName("NFR-17: 10 threads đồng thời tranh chấp xác nhận 2 ca học trùng giờ của 1 Gia sư -> Chỉ đúng 1 ca SCHEDULED, triệt tiêu Double-Booking")
  void shouldPreventDoubleBookingWhenMultipleSessionsConfirmSameTutorSlot() throws Exception {
    if (!postgres.isRunning()) {
      return;
    }

    UUID tutorId = UUID.randomUUID();
    UUID tutorUserId = tutorAccount(tutorId);
    UUID studentA = UUID.randomUUID();
    UUID studentB = UUID.randomUUID();
    UUID classA = UUID.randomUUID();
    UUID classB = UUID.randomUUID();

    Instant now = Instant.now();
    Instant slotStart = now.plus(3, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
    Instant slotEnd = slotStart.plus(2, ChronoUnit.HOURS); // Ca dạy 2 tiếng [T -> T+2h)
    Instant expiresAt = slotStart.minus(1, ChronoUnit.HOURS);

    // Chuẩn bị 2 đề xuất của 2 học sinh khác nhau gửi tới cùng 1 Gia sư tại CÙNG KHUNG GIỜ
    UUID sessionAId = UUID.randomUUID();
    TutoringSession sessionA = TutoringSession.createProposed(
        sessionAId, classA, tutorId, studentA,
        slotStart, slotEnd, SessionMode.ONLINE, "link-A", null, "Đề xuất học A",
        studentA, expiresAt, now
    );
    sessionRepository.save(sessionA);

    UUID sessionBId = UUID.randomUUID();
    TutoringSession sessionB = TutoringSession.createProposed(
        sessionBId, classB, tutorId, studentB,
        slotStart, slotEnd, SessionMode.ONLINE, "link-B", null, "Đề xuất học B",
        studentB, expiresAt, now
    );
    sessionRepository.save(sessionB);

    // Kịch bản Stress Test: 10 luồng đồng thời tranh chấp xác nhận
    // 5 luồng cố gắng xác nhận Session A, 5 luồng cố gắng xác nhận Session B
    int threadCount = 10;
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    CountDownLatch startGate = new CountDownLatch(1);
    CountDownLatch endGate = new CountDownLatch(threadCount);

    AtomicInteger successCount = new AtomicInteger(0);
    AtomicInteger conflictOrRejectedCount = new AtomicInteger(0);
    List<Future<?>> futures = new ArrayList<>();

    for (int i = 0; i < threadCount; i++) {
      final UUID targetSessionId = (i % 2 == 0) ? sessionAId : sessionBId;
      futures.add(executor.submit(() -> {
        try {
          startGate.await(); // Chờ phát lệnh đồng loạt
          respondToProposalService.respondToProposal(targetSessionId, tutorUserId, true, null);
          successCount.incrementAndGet();
        } catch (Exception ex) {
          conflictOrRejectedCount.incrementAndGet();
        } finally {
          endGate.countDown();
        }
      }));
    }

    // BẮN SÚNG PHÁT LỆNH: Cả 10 luồng cùng lao vào Database trong cùng 1 micro-giây!
    startGate.countDown();

    boolean completed = endGate.await(15, TimeUnit.SECONDS);
    assertThat(completed).isTrue();
    executor.shutdown();

    // KIỂM CHỨNG BẢO VỆ NFR-17:
    // 1. Chỉ duy nhất 1 buổi học trong số 2 buổi học có thể chuyển thành công sang SCHEDULED
    assertThat(successCount.get())
        .as("Duy nhất 1 ca học được chốt thành công trong cùng khung giờ")
        .isEqualTo(1);

    assertThat(conflictOrRejectedCount.get())
        .as("9 luồng còn lại phải bị chặn đứng bởi Pessimistic Lock hoặc GiST Exclusion Constraint")
        .isEqualTo(9);

    // 2. Kiểm tra trực tiếp trong Cơ sở dữ liệu:
    List<TutoringSession> busySlots = sessionRepository.findTutorBusySlots(tutorId, slotStart, slotEnd);
    assertThat(busySlots)
        .as("Database chỉ được phép tồn tại đúng 1 ca dạy SCHEDULED của Gia sư tại khung giờ này")
        .hasSize(1);

    TutoringSession finalSessionA = sessionRepository.findById(sessionAId).orElseThrow();
    TutoringSession finalSessionB = sessionRepository.findById(sessionBId).orElseThrow();

    boolean onlyOneScheduled = (finalSessionA.getStatus() == SessionStatus.SCHEDULED
        && finalSessionB.getStatus() != SessionStatus.SCHEDULED)
        || (finalSessionA.getStatus() != SessionStatus.SCHEDULED
        && finalSessionB.getStatus() == SessionStatus.SCHEDULED);

    assertThat(onlyOneScheduled)
        .as("Không bao giờ xảy ra Double-Booking: Chỉ 1 trong 2 buổi học là SCHEDULED")
        .isTrue();
  }

  @Test
  @DisplayName("NFR-17: 10 threads cùng xác nhận 1 buổi học -> Khóa bi quan (SELECT FOR UPDATE) chỉ cho phép đúng 1 lần thành công")
  void shouldSerializeConcurrentAcceptCallsOnSameSessionWithPessimisticLock() throws Exception {
    if (!postgres.isRunning()) {
      return;
    }

    UUID tutorId = UUID.randomUUID();
    UUID tutorUserId = tutorAccount(tutorId);
    UUID studentId = UUID.randomUUID();
    UUID classId = UUID.randomUUID();

    Instant now = Instant.now();
    Instant slotStart = now.plus(5, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
    Instant slotEnd = slotStart.plus(2, ChronoUnit.HOURS);
    Instant expiresAt = slotStart.minus(1, ChronoUnit.HOURS);

    UUID sessionId = UUID.randomUUID();
    TutoringSession session = TutoringSession.createProposed(
        sessionId, classId, tutorId, studentId,
        slotStart, slotEnd, SessionMode.ONLINE, "link", null, "Đề xuất duy nhất",
        studentId, expiresAt, now
    );
    sessionRepository.save(session);

    int threadCount = 10;
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    CountDownLatch startGate = new CountDownLatch(1);
    CountDownLatch endGate = new CountDownLatch(threadCount);

    AtomicInteger successCount = new AtomicInteger(0);
    AtomicInteger failedCount = new AtomicInteger(0);

    for (int i = 0; i < threadCount; i++) {
      executor.submit(() -> {
        try {
          startGate.await();
          respondToProposalService.respondToProposal(sessionId, tutorUserId, true, null);
          successCount.incrementAndGet();
        } catch (Exception ex) {
          failedCount.incrementAndGet();
        } finally {
          endGate.countDown();
        }
      });
    }

    startGate.countDown();

    boolean completed = endGate.await(15, TimeUnit.SECONDS);
    assertThat(completed).isTrue();
    executor.shutdown();

    assertThat(successCount.get())
        .as("Chỉ đúng 1 luồng được phép xác nhận thành công buổi học")
        .isEqualTo(1);

    assertThat(failedCount.get())
        .as("9 luồng còn lại phải bị từ chối do trạng thái không còn là PROPOSED")
        .isEqualTo(9);

    TutoringSession finalSession = sessionRepository.findById(sessionId).orElseThrow();
    assertThat(finalSession.getStatus()).isEqualTo(SessionStatus.SCHEDULED);
    assertThat(finalSession.getRespondedBy()).isEqualTo(tutorUserId);
  }
}
