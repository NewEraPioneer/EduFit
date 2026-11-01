package vn.edufit.verification;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItems;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManagerFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.json.JsonMapper;
import vn.edufit.profile.api.ProfileFacade;
import vn.edufit.profile.application.service.LearningCatalogLoader;
import vn.edufit.profile.application.service.LearningNeedService;
import vn.edufit.profile.application.service.ProfileFacadeImpl;
import vn.edufit.profile.application.service.TutorProfileAssembler;
import vn.edufit.shared.auth.CurrentUser;
import vn.edufit.storage.infra.CredentialStorageProperties;
import vn.edufit.storage.infra.FileValidationService;
import vn.edufit.storage.infra.LocalCredentialStorageService;
import vn.edufit.verification.application.service.AdminVerificationService;
import vn.edufit.verification.application.service.TutorVerificationService;
import vn.edufit.verification.infra.persistence.entity.Credential;
import vn.edufit.verification.web.AdminVerificationController;
import vn.edufit.verification.web.TutorVerificationController;
import vn.edufit.verification.web.request.ReviewVerificationRequest;
import vn.edufit.verification.web.request.SubmitVerificationRequest;
import vn.edufit.web.advice.GlobalExceptionHandler;

@Testcontainers
@SpringJUnitConfig(VerificationWorkflowPostgresTest.Config.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("Kiểm thử tích hợp vòng đời Verification & Thẩm định KYC trên PostgreSQL 17 thật (Testcontainers)")
class VerificationWorkflowPostgresTest {

  @Container
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17")
      .withEnv("TZ", "Asia/Ho_Chi_Minh");

  @TempDir
  static Path tempStorageDir;

  @Autowired
  private ApplicationContext context;

  @Autowired
  private DataSource dataSource;

  @Autowired
  private AdminVerificationController adminVerificationController;

  @Autowired
  private TutorVerificationController tutorVerificationController;

  @Autowired
  private MutableCurrentUser currentUser;

  private JdbcTemplate jdbc;
  private MockMvc mvc;
  private JsonMapper jsonMapper;

  private UUID tutorUserId;
  private UUID tutorProfileId;
  private UUID adminUserId;

  @BeforeEach
  void setUp() {
    jdbc = new JdbcTemplate(dataSource);
    jsonMapper = JsonMapper.builder().build();

    // Dọn dẹp dữ liệu trước mỗi test
    jdbc.execute("TRUNCATE credential_file, credential, verification_request, tutor_profile, account CASCADE");

    tutorUserId = UUID.randomUUID();
    tutorProfileId = UUID.randomUUID();
    adminUserId = UUID.randomUUID();

    // Tạo tài khoản Admin trong DB
    jdbc.update(
        "INSERT INTO account(user_id, email, password_hash, full_name, role) VALUES (?, ?, 'hash', 'Admin', 'ADMIN')",
        adminUserId, "admin@edufit.vn"
    );

    // Tạo tài khoản Tutor trong DB
    jdbc.update(
        "INSERT INTO account(user_id, email, password_hash, full_name, role) VALUES (?, ?, 'hash', 'Nguyen Van A', 'TUTOR')",
        tutorUserId, "tutor@edufit.vn"
    );

    // Tạo hồ sơ Tutor trạng thái UNVERIFIED
    jdbc.update(
        "INSERT INTO tutor_profile(tutor_id, user_id, display_name, headline, bio, teaching_mode, status, rating_avg, review_count, created_at, updated_at) " +
            "VALUES (?, ?, 'Nguyen Van A', 'Toan Cap 3', 'Gia su toan', 'ONLINE', 'UNVERIFIED', 0.0, 0, now(), now())",
        tutorProfileId, tutorUserId
    );

    mvc = MockMvcBuilders
        .standaloneSetup(adminVerificationController, tutorVerificationController)
        .setControllerAdvice(new GlobalExceptionHandler())
        .build();
  }

  @Test
  @DisplayName("Quy trình 1: Gia sư nộp 2 bằng cấp -> DB lưu PENDING -> Admin duyệt APPROVE -> Profile chuyển VERIFIED")
  void testFullVerificationApprovalLifecycle() throws Exception {
    // 1. Gia sư đăng nhập và nộp hồ sơ
    currentUser.actAs(tutorUserId, "tutor@edufit.vn", Set.of("TUTOR"));

    SubmitVerificationRequest submitRequest = new SubmitVerificationRequest(List.of(
        new SubmitVerificationRequest.CredentialItem(
            Credential.Type.DEGREE, "Đại học Sư phạm Hà Nội", (short) 2023, "Bằng cử nhân sư phạm toán", List.of(0), null
        ),
        new SubmitVerificationRequest.CredentialItem(
            Credential.Type.CERTIFICATE, "IELTS British Council", (short) 2022, "IELTS 7.5 Academic", List.of(1), null
        )
    ));

    byte[] pdf1Bytes = "%PDF-1.4\n1 0 obj\n<< /Type /Catalog >>\nendobj\ntrailer\n<< /Root 1 0 R >>\n%%EOF".getBytes();
    byte[] pdf2Bytes = "%PDF-1.4\n2 0 obj\n<< /Type /Catalog >>\nendobj\ntrailer\n<< /Root 2 0 R >>\n%%EOF".getBytes();

    MockMultipartFile payloadPart = new MockMultipartFile(
        "payload", "", "application/json", jsonMapper.writeValueAsBytes(submitRequest)
    );
    MockMultipartFile file0 = new MockMultipartFile(
        "files", "bang_dai_hoc.pdf", "application/pdf", pdf1Bytes
    );
    MockMultipartFile file1 = new MockMultipartFile(
        "files", "ielts_cert.pdf", "application/pdf", pdf2Bytes
    );

    String submitResultJson = mvc.perform(multipart("/api/v1/verifications")
            .file(payloadPart)
            .file(file0)
            .file(file1)
            .contentType(MediaType.MULTIPART_FORM_DATA))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.status").value("PENDING"))
        .andExpect(jsonPath("$.data.credentials.length()").value(2))
        .andReturn()
        .getResponse()
        .getContentAsString();

    UUID requestId = UUID.fromString(jsonMapper.readTree(submitResultJson).get("data").get("requestId").asText());

    // Kiểm tra trực tiếp trong PostgreSQL
    assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM verification_request WHERE request_id = ? AND status = 'PENDING'", Integer.class, requestId));
    assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM credential WHERE request_id = ?", Integer.class, requestId));
    assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM credential_file", Integer.class));
    assertEquals("PENDING_VERIFICATION", jdbc.queryForObject("SELECT status FROM tutor_profile WHERE tutor_id = ?", String.class, tutorProfileId));

    // 2. Thử nộp lần 2 khi đã có PENDING -> Bị chặn với CONFLICT (BR-15)
    mvc.perform(multipart("/api/v1/verifications")
            .file(payloadPart)
            .file(file0)
            .contentType(MediaType.MULTIPART_FORM_DATA))
        .andExpect(status().isConflict());

    // 3. Admin đăng nhập kiểm tra hàng đợi và xem chi tiết
    currentUser.actAs(adminUserId, "admin@edufit.vn", Set.of("ADMIN"));

    mvc.perform(get("/api/v1/admin/verifications/pending"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items[0].requestId").value(requestId.toString()))
        .andExpect(jsonPath("$.data.items[0].tutorId").value(tutorProfileId.toString()));

    mvc.perform(get("/api/v1/admin/verifications/" + requestId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.requestId").value(requestId.toString()))
        .andExpect(jsonPath("$.data.credentials[*].institution", containsInAnyOrder("Đại học Sư phạm Hà Nội", "IELTS British Council")))
        .andExpect(jsonPath("$.data.credentials[*].files[*].fileName", hasItems("bang_dai_hoc.pdf", "ielts_cert.pdf")));

    // 4. Admin phê duyệt hồ sơ (APPROVE)
    ReviewVerificationRequest approveRequest = new ReviewVerificationRequest(ReviewVerificationRequest.Action.APPROVE, null, null);
    mvc.perform(post("/api/v1/admin/verifications/" + requestId + "/review")
            .contentType(MediaType.APPLICATION_JSON)
            .content(jsonMapper.writeValueAsString(approveRequest)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("APPROVED"));

    // Kiểm tra DB sau khi duyệt
    assertEquals("APPROVED", jdbc.queryForObject("SELECT status FROM verification_request WHERE request_id = ?", String.class, requestId));
    assertEquals(adminUserId, jdbc.queryForObject("SELECT reviewed_by FROM verification_request WHERE request_id = ?", UUID.class, requestId));
    assertEquals("VERIFIED", jdbc.queryForObject("SELECT status FROM tutor_profile WHERE tutor_id = ?", String.class, tutorProfileId));
    assertNotNull(jdbc.queryForObject("SELECT verified_at FROM tutor_profile WHERE tutor_id = ?", Instant.class, tutorProfileId));
  }

  @Test
  @DisplayName("Quy trình 1B: Gia sư đã VERIFIED nộp bổ sung bằng cấp -> Lưu PENDING, BẢO TOÀN huy hiệu VERIFIED -> getMyRequest trả đủ bằng -> Admin từ chối không làm mất huy hiệu")
  void testSupplementaryVerificationLifecycleForVerifiedTutor() throws Exception {
    // 1. Gia sư đã có 1 hồ sơ APPROVED và profile VERIFIED từ trước
    UUID approvedReqId = UUID.randomUUID();
    jdbc.update(
        "UPDATE tutor_profile SET status = 'VERIFIED', verified_at = now() WHERE tutor_id = ?",
        tutorProfileId
    );
    jdbc.update(
        "INSERT INTO verification_request(request_id, tutor_id, status, submitted_at, reviewed_by, reviewed_at) " +
            "VALUES (?, ?, 'APPROVED', now() - interval '2 days', ?, now() - interval '1 day')",
        approvedReqId, tutorProfileId, adminUserId
    );
    UUID prevCredId = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO credential(credential_id, request_id, type, institution, year, note) " +
            "VALUES (?, ?, 'DEGREE', 'Đại học Bách Khoa Hà Nội', 2022, 'Cử nhân CNTT')",
        prevCredId, approvedReqId
    );

    // 2. Gia sư đăng nhập và nộp bổ sung chứng chỉ IELTS
    currentUser.actAs(tutorUserId, "tutor@edufit.vn", Set.of("TUTOR"));

    SubmitVerificationRequest supplementRequest = new SubmitVerificationRequest(List.of(
        new SubmitVerificationRequest.CredentialItem(
            Credential.Type.CERTIFICATE, "British Council", (short) 2024, "IELTS 8.0 Supplementary", List.of(0), null
        )
    ));
    byte[] pdfBytes = "%PDF-1.4\n1 0 obj\n<< /Type /Catalog >>\nendobj\ntrailer\n<< /Root 1 0 R >>\n%%EOF".getBytes();
    MockMultipartFile payloadPart = new MockMultipartFile(
        "payload", "", "application/json", jsonMapper.writeValueAsBytes(supplementRequest)
    );
    MockMultipartFile file0 = new MockMultipartFile(
        "files", "ielts_80.pdf", "application/pdf", pdfBytes
    );

    String submitResultJson = mvc.perform(multipart("/api/v1/verifications")
            .file(payloadPart)
            .file(file0)
            .contentType(MediaType.MULTIPART_FORM_DATA))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.status").value("PENDING"))
        .andReturn()
        .getResponse()
        .getContentAsString();

    UUID suppRequestId = UUID.fromString(jsonMapper.readTree(submitResultJson).get("data").get("requestId").asText());

    // 3. Kiểm tra DB: Hồ sơ bổ sung PENDING, NHƯNG tutor_profile vẫn giữ nguyên 'VERIFIED'
    assertEquals("PENDING", jdbc.queryForObject("SELECT status FROM verification_request WHERE request_id = ?", String.class, suppRequestId));
    assertEquals("VERIFIED", jdbc.queryForObject("SELECT status FROM tutor_profile WHERE tutor_id = ?", String.class, tutorProfileId));

    // Thử nộp tiếp yêu cầu khác khi đang có hồ sơ PENDING -> Bị chặn theo BR-15 (409 Conflict)
    mvc.perform(multipart("/api/v1/verifications")
            .file(payloadPart)
            .file(file0)
            .contentType(MediaType.MULTIPART_FORM_DATA))
        .andExpect(status().isConflict());

    // 4. Gia sư kiểm tra hồ sơ qua getMyRequest -> Trả về hồ sơ PENDING nhưng tổng hợp cả bằng trước đó (2 bằng)
    mvc.perform(get("/api/v1/verifications/my-request"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("PENDING"))
        .andExpect(jsonPath("$.data.credentials.length()").value(2))
        .andExpect(jsonPath("$.data.credentials[*].institution", containsInAnyOrder("Đại học Bách Khoa Hà Nội", "British Council")));

    // 5. Admin từ chối hồ sơ bổ sung (do giấy tờ mờ)
    currentUser.actAs(adminUserId, "admin@edufit.vn", Set.of("ADMIN"));
    ReviewVerificationRequest rejectRequest = new ReviewVerificationRequest(
        ReviewVerificationRequest.Action.REJECT, "Chứng chỉ IELTS không đủ rõ nét", null
    );
    mvc.perform(post("/api/v1/admin/verifications/" + suppRequestId + "/review")
            .contentType(MediaType.APPLICATION_JSON)
            .content(jsonMapper.writeValueAsString(rejectRequest)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("REJECTED"));

    // DB: Hồ sơ bổ sung bị REJECTED, nhưng profile gia sư VẪN LÀ VERIFIED vì đã có bằng cấp APPROVED trước đó
    assertEquals("REJECTED", jdbc.queryForObject("SELECT status FROM verification_request WHERE request_id = ?", String.class, suppRequestId));
    assertEquals("VERIFIED", jdbc.queryForObject("SELECT status FROM tutor_profile WHERE tutor_id = ?", String.class, tutorProfileId));
  }

  @Test
  @DisplayName("Quy trình 1C: Gia sư có nhiều hồ sơ APPROVED -> Admin thu hồi (REVOKE) -> Thu hồi tất cả APPROVED & Giáng profile UNVERIFIED -> Nộp lại chuyển PENDING_VERIFICATION")
  void testRevokeVerificationWhenMultipleApprovedExist() throws Exception {
    // 1. Gia sư có 2 hồ sơ APPROVED (hồ sơ gốc + hồ sơ bổ sung)
    UUID req1Id = UUID.randomUUID();
    UUID req2Id = UUID.randomUUID();

    jdbc.update(
        "UPDATE tutor_profile SET status = 'VERIFIED', verified_at = now() WHERE tutor_id = ?",
        tutorProfileId
    );
    jdbc.update(
        "INSERT INTO verification_request(request_id, tutor_id, status, submitted_at, reviewed_by, reviewed_at) " +
            "VALUES (?, ?, 'APPROVED', now() - interval '5 days', ?, now() - interval '4 days')",
        req1Id, tutorProfileId, adminUserId
    );
    jdbc.update(
        "INSERT INTO verification_request(request_id, tutor_id, status, submitted_at, reviewed_by, reviewed_at) " +
            "VALUES (?, ?, 'APPROVED', now() - interval '2 days', ?, now() - interval '1 day')",
        req2Id, tutorProfileId, adminUserId
    );

    // 2. Admin thu hồi hồ sơ req2Id
    currentUser.actAs(adminUserId, "admin@edufit.vn", Set.of("ADMIN"));
    vn.edufit.verification.web.request.RevokeVerificationRequest revokeReq =
        new vn.edufit.verification.web.request.RevokeVerificationRequest("Phát hiện văn bằng bổ sung và bằng gốc là giả mạo");

    mvc.perform(post("/api/v1/admin/verifications/" + req2Id + "/revoke")
            .contentType(MediaType.APPLICATION_JSON)
            .content(jsonMapper.writeValueAsString(revokeReq)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("REJECTED"));

    // 3. Kiểm tra DB: Cả 2 hồ sơ đều bị REJECTED ([REVOKED] ...), tutor_profile chuyển UNVERIFIED
    assertEquals("REJECTED", jdbc.queryForObject("SELECT status FROM verification_request WHERE request_id = ?", String.class, req1Id));
    assertEquals("REJECTED", jdbc.queryForObject("SELECT status FROM verification_request WHERE request_id = ?", String.class, req2Id));
    assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM verification_request WHERE tutor_id = ? AND status = 'APPROVED'", Integer.class, tutorProfileId));
    assertEquals("UNVERIFIED", jdbc.queryForObject("SELECT status FROM tutor_profile WHERE tutor_id = ?", String.class, tutorProfileId));

    // 4. Gia sư nộp lại hồ sơ xác minh mới -> Profile phải chuyển thành PENDING_VERIFICATION
    currentUser.actAs(tutorUserId, "tutor@edufit.vn", Set.of("TUTOR"));
    SubmitVerificationRequest resubmitRequest = new SubmitVerificationRequest(List.of(
        new SubmitVerificationRequest.CredentialItem(
            Credential.Type.DEGREE, "Đại học Mới Hợp Lệ", (short) 2024, "Bằng chính quy mới", List.of(0), null
        )
    ));
    byte[] pdfBytes = "%PDF-1.4\n1 0 obj\n<< >>\nendobj\n%%EOF".getBytes();
    MockMultipartFile payloadPart = new MockMultipartFile("payload", "", "application/json", jsonMapper.writeValueAsBytes(resubmitRequest));
    MockMultipartFile file0 = new MockMultipartFile("files", "new_degree.pdf", "application/pdf", pdfBytes);

    mvc.perform(multipart("/api/v1/verifications")
            .file(payloadPart)
            .file(file0)
            .contentType(MediaType.MULTIPART_FORM_DATA))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.status").value("PENDING"));

    assertEquals("PENDING_VERIFICATION", jdbc.queryForObject("SELECT status FROM tutor_profile WHERE tutor_id = ?", String.class, tutorProfileId));
  }

  @Test
  @DisplayName("Quy trình 2: Admin từ chối REJECT không có lý do -> 400 Bad Request; Có lý do -> REJECTED & Profile UNVERIFIED")
  void testVerificationRejectionLifecycle() throws Exception {
    currentUser.actAs(tutorUserId, "tutor@edufit.vn", Set.of("TUTOR"));

    SubmitVerificationRequest submitRequest = new SubmitVerificationRequest(List.of(
        new SubmitVerificationRequest.CredentialItem(
            Credential.Type.DEGREE, "Đại học X", (short) 2021, "Bằng cử nhân", List.of(0), null
        )
    ));
    byte[] pdfBytes = "%PDF-1.4\n1 0 obj\n<< >>\nendobj\n%%EOF".getBytes();

    MockMultipartFile payloadPart = new MockMultipartFile("payload", "", "application/json", jsonMapper.writeValueAsBytes(submitRequest));
    MockMultipartFile file0 = new MockMultipartFile("files", "degree.pdf", "application/pdf", pdfBytes);

    String submitResultJson = mvc.perform(multipart("/api/v1/verifications")
            .file(payloadPart)
            .file(file0)
            .contentType(MediaType.MULTIPART_FORM_DATA))
        .andExpect(status().isCreated())
        .andReturn()
        .getResponse()
        .getContentAsString();

    UUID requestId = UUID.fromString(jsonMapper.readTree(submitResultJson).get("data").get("requestId").asText());

    // Admin từ chối không kèm lý do -> Bị lỗi (BR-06)
    currentUser.actAs(adminUserId, "admin@edufit.vn", Set.of("ADMIN"));
    ReviewVerificationRequest invalidReject = new ReviewVerificationRequest(ReviewVerificationRequest.Action.REJECT, null, null);
    mvc.perform(post("/api/v1/admin/verifications/" + requestId + "/review")
            .contentType(MediaType.APPLICATION_JSON)
            .content(jsonMapper.writeValueAsString(invalidReject)))
        .andExpect(status().isBadRequest());

    // Admin từ chối kèm lý do hợp lệ
    ReviewVerificationRequest validReject = new ReviewVerificationRequest(
        ReviewVerificationRequest.Action.REJECT, "Ảnh chụp bằng cấp quá mờ, không thể đọc số hiệu", null
    );
    mvc.perform(post("/api/v1/admin/verifications/" + requestId + "/review")
            .contentType(MediaType.APPLICATION_JSON)
            .content(jsonMapper.writeValueAsString(validReject)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("REJECTED"))
        .andExpect(jsonPath("$.data.rejectionReason").value("Ảnh chụp bằng cấp quá mờ, không thể đọc số hiệu"));

    // Kiểm tra DB
    assertEquals("REJECTED", jdbc.queryForObject("SELECT status FROM verification_request WHERE request_id = ?", String.class, requestId));
    assertEquals("Ảnh chụp bằng cấp quá mờ, không thể đọc số hiệu", jdbc.queryForObject("SELECT rejection_reason FROM verification_request WHERE request_id = ?", String.class, requestId));
    assertEquals("UNVERIFIED", jdbc.queryForObject("SELECT status FROM tutor_profile WHERE tutor_id = ?", String.class, tutorProfileId));

    // Gia sư xem lại hồ sơ
    currentUser.actAs(tutorUserId, "tutor@edufit.vn", Set.of("TUTOR"));
    mvc.perform(get("/api/v1/verifications/my-request"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("REJECTED"))
        .andExpect(jsonPath("$.data.rejectionReason").value("Ảnh chụp bằng cấp quá mờ, không thể đọc số hiệu"));
  }

  @Test
  @DisplayName("Quy trình 3: Tải xem file bằng cấp -> Gia sư chính chủ & Admin được tải; Gia sư khác bị FORBIDDEN")
  void testCredentialFileDownloadSecurity() throws Exception {
    currentUser.actAs(tutorUserId, "tutor@edufit.vn", Set.of("TUTOR"));

    SubmitVerificationRequest submitRequest = new SubmitVerificationRequest(List.of(
        new SubmitVerificationRequest.CredentialItem(
            Credential.Type.DEGREE, "Đại học Bách Khoa", (short) 2023, "Bằng kỹ sư", List.of(0), null
        )
    ));
    byte[] pdfBytes = "%PDF-1.4\n1 0 obj\n<< /Type /Catalog >>\nendobj\n%%EOF".getBytes();

    MockMultipartFile payloadPart = new MockMultipartFile("payload", "", "application/json", jsonMapper.writeValueAsBytes(submitRequest));
    MockMultipartFile file0 = new MockMultipartFile("files", "bk_degree.pdf", "application/pdf", pdfBytes);

    String submitResultJson = mvc.perform(multipart("/api/v1/verifications")
            .file(payloadPart)
            .file(file0)
            .contentType(MediaType.MULTIPART_FORM_DATA))
        .andExpect(status().isCreated())
        .andReturn()
        .getResponse()
        .getContentAsString();

    UUID fileId = UUID.fromString(
        jsonMapper.readTree(submitResultJson).get("data").get("credentials").get(0).get("files").get(0).get("fileId").asText()
    );

    // 1. Gia sư chính chủ tải file thành công
    mvc.perform(get("/api/v1/verifications/credentials/files/" + fileId))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Disposition", "inline; filename=\"bk_degree.pdf\""))
        .andExpect(header().string("Content-Type", "application/pdf"));

    // 2. Admin tải file thành công
    currentUser.actAs(adminUserId, "admin@edufit.vn", Set.of("ADMIN"));
    mvc.perform(get("/api/v1/verifications/credentials/files/" + fileId))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Disposition", "inline; filename=\"bk_degree.pdf\""));

    // 3. Gia sư khác cố tình truy cập file -> 403 Forbidden
    UUID anotherTutorUserId = UUID.randomUUID();
    UUID anotherTutorProfileId = UUID.randomUUID();
    jdbc.update("INSERT INTO account(user_id, email, password_hash, full_name, role) VALUES (?, 'other@edufit.vn', 'hash', 'Other', 'TUTOR')", anotherTutorUserId);
    jdbc.update("INSERT INTO tutor_profile(tutor_id, user_id, display_name, headline, bio, teaching_mode, status, rating_avg, review_count, created_at, updated_at) " +
        "VALUES (?, ?, 'Other', 'H', 'B', 'ONLINE', 'UNVERIFIED', 0, 0, now(), now())", anotherTutorProfileId, anotherTutorUserId);

    currentUser.actAs(anotherTutorUserId, "other@edufit.vn", Set.of("TUTOR"));
    mvc.perform(get("/api/v1/verifications/credentials/files/" + fileId))
        .andExpect(status().isForbidden());
  }

  // =========================================================================
  // CẤU HÌNH SPRING TEST CONTEXT
  // =========================================================================
  static class MutableCurrentUser implements CurrentUser {
    private UUID userId;
    private String email;
    private Set<String> roles = Set.of();

    void actAs(UUID userId, String email, Set<String> roles) {
      this.userId = userId;
      this.email = email;
      this.roles = roles;
    }

    @Override
    public UUID getUserId() {
      return userId;
    }

    @Override
    public String getEmail() {
      return email;
    }

    @Override
    public Set<String> getRoles() {
      return roles;
    }

    @Override
    public boolean hasRole(String role) {
      return roles.stream().anyMatch(r -> r.equalsIgnoreCase(role));
    }
  }

  @Configuration
  @EnableTransactionManagement
  @EnableJpaRepositories(basePackages = {
      "vn.edufit.profile.infra.persistence.repository",
      "vn.edufit.verification.infra.persistence.repository"
  })
  @Import({
      ProfileFacadeImpl.class,
      LearningNeedService.class,
      LearningCatalogLoader.class,
      TutorProfileAssembler.class,
      AdminVerificationService.class,
      TutorVerificationService.class,
      AdminVerificationController.class,
      TutorVerificationController.class,
      LocalCredentialStorageService.class,
      FileValidationService.class
  })
  static class Config {

    // AdminVerificationService bắt buộc ghi audit và tra cứu IAM; slice test cung cấp mock tường minh
    @Bean
    vn.edufit.shared.audit.AuditSink auditSink() {
      return org.mockito.Mockito.mock(vn.edufit.shared.audit.AuditSink.class);
    }

    @Bean
    vn.edufit.iam.api.IamFacade iamFacade() {
      return org.mockito.Mockito.mock(vn.edufit.iam.api.IamFacade.class);
    }

    // Liên kết phụ huynh–học sinh thuộc module connection, ngoài phạm vi slice test này
    @Bean
    vn.edufit.profile.api.port.ParentStudentLinkPort parentStudentLinkPort() {
      return org.mockito.Mockito.mock(vn.edufit.profile.api.port.ParentStudentLinkPort.class);
    }

    @Bean(destroyMethod = "close")
    HikariDataSource dataSource() {
      HikariDataSource source = new HikariDataSource();
      source.setJdbcUrl(postgres.getJdbcUrl());
      source.setUsername(postgres.getUsername());
      source.setPassword(postgres.getPassword());
      source.setMaximumPoolSize(5);

      // Kích hoạt toàn bộ migrations Flyway trên PostgreSQL 17 thật
      Flyway.configure()
          .dataSource(source)
          .locations("classpath:db/migration")
          .load()
          .migrate();

      return source;
    }

    @Bean
    LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource source) {
      LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
      factory.setDataSource(source);
      factory.setPackagesToScan(
          "vn.edufit.profile.infra.persistence.entity",
          "vn.edufit.verification.infra.persistence.entity"
      );
      factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
      factory.setJpaPropertyMap(Map.of(
          "hibernate.hbm2ddl.auto", "validate",
          "hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect"
      ));
      return factory;
    }

    @Bean
    JpaTransactionManager transactionManager(EntityManagerFactory factory) {
      return new JpaTransactionManager(factory);
    }

    @Bean
    CredentialStorageProperties credentialStorageProperties() {
      CredentialStorageProperties props = new CredentialStorageProperties();
      props.setRoot(tempStorageDir);
      props.setMaxFileSizeBytes(10 * 1024 * 1024L);
      return props;
    }

    @Bean
    MutableCurrentUser mutableCurrentUser() {
      return new MutableCurrentUser();
    }

    @Bean
    ObjectProvider<CurrentUser> currentUserProvider(MutableCurrentUser user) {
      ObjectProvider<CurrentUser> provider = mock(ObjectProvider.class);
      when(provider.getIfAvailable()).thenAnswer(inv -> user);
      when(provider.getObject()).thenAnswer(inv -> user);
      return provider;
    }
  }
}
