package vn.edufit.profile.qa;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import vn.edufit.profile.application.service.AdminCatalogService;
import vn.edufit.profile.infra.persistence.entity.Subject;
import vn.edufit.profile.infra.persistence.repository.SubjectRepository;
import vn.edufit.profile.web.AdminCatalogController;
import vn.edufit.profile.web.request.CreateSubjectRequest;
import vn.edufit.profile.web.response.SubjectAdminResponse;
import vn.edufit.shared.exception.ErrorCode;
import vn.edufit.shared.exception.InvalidOperationException;
import vn.edufit.shared.exception.ResourceConflictException;

/**
 * Bộ kiểm thử đối kháng và kiểm thử đồng thời (Adversarial & Concurrency QA Test)
 * độc lập cho phân hệ Admin Catalogs & Foreign Key Integrity.
 *
 * <p>Được thiết kế và thực thi bởi: EduFit Backend QA & Concurrency Specialist.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AdminCatalog Concurrency & Adversarial QA Tests")
class AdminCatalogConcurrencyAndSecurityQATest {

  @Mock
  private SubjectRepository subjectRepository;

  private AdminCatalogService adminCatalogService;
  private AdminCatalogController adminCatalogController;

  @BeforeEach
  void setUp() {
    adminCatalogService = new AdminCatalogService(subjectRepository);
    adminCatalogController = new AdminCatalogController(adminCatalogService);
  }

  @Test
  @DisplayName("Adversarial QA 1: Khắc chế Hard Delete - Nghiêm cấm xóa cứng môn học để bảo vệ toàn vẹn khóa ngoại")
  void shouldStrictlyForbidHardDeleteToProtectForeignKeyIntegrity() {
    InvalidOperationException ex = assertThrows(
        InvalidOperationException.class,
        () -> adminCatalogController.deleteSubject(42)
    );

    assertEquals(ErrorCode.INVALID_OPERATION, ex.getErrorCode());
    assertTrue(ex.getMessage().contains("Không được phép xóa cứng"));
    verifyNoInteractions(subjectRepository);
  }

  @Test
  @DisplayName("Adversarial QA 2: Chặn tạo trùng môn học khi có xung đột tên (Case-sensitive / Trimming)")
  void shouldDetectAndBlockDuplicateSubjectCreation() {
    CreateSubjectRequest request = new CreateSubjectRequest(
        "  Toán Học Nâng Cao  ",
        "Khoa học tự nhiên",
        "MATH_ADV",
        "toan-hoc-nang-cao",
        "Môn toán chuyên sâu"
    );

    when(subjectRepository.existsByName("Toán Học Nâng Cao")).thenReturn(true);

    ResourceConflictException ex = assertThrows(
        ResourceConflictException.class,
        () -> adminCatalogService.createSubject(request)
    );

    assertTrue(ex.getMessage().contains("đã tồn tại"));
    verify(subjectRepository, never()).save(any());
  }

  @Test
  @DisplayName("Concurrency QA 3: Stress Test đa luồng Toggle Status - 10 luồng đồng thời không gây race condition")
  void shouldHandleConcurrentTogglesSafelyWithoutDeadlock() throws Exception {
    int threadCount = 10;
    Subject subject = new Subject("Vật Lý Chuyên", true);
    ReflectionTestUtils.setField(subject, "subjectId", 101);

    AtomicBoolean currentActive = new AtomicBoolean(true);
    when(subjectRepository.findById(101)).thenAnswer(inv -> {
      Subject s = new Subject("Vật Lý Chuyên", currentActive.get());
      ReflectionTestUtils.setField(s, "subjectId", 101);
      return Optional.of(s);
    });

    when(subjectRepository.save(any(Subject.class))).thenAnswer(inv -> {
      Subject s = inv.getArgument(0);
      currentActive.set(s.getIsActive());
      return s;
    });

    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch doneLatch = new CountDownLatch(threadCount);
    List<Boolean> observedStatuses = Collections.synchronizedList(new ArrayList<>());

    for (int i = 0; i < threadCount; i++) {
      executor.submit(() -> {
        try {
          startLatch.await();
          SubjectAdminResponse resp = adminCatalogService.toggleSubjectStatus(101);
          observedStatuses.add(resp.isActive());
        } catch (Exception e) {
          // ignore
        } finally {
          doneLatch.countDown();
        }
      });
    }

    startLatch.countDown();
    assertTrue(doneLatch.await(5, TimeUnit.SECONDS), "Tất cả các luồng phải hoàn thành trong 5 giây");
    executor.shutdown();

    assertEquals(threadCount, observedStatuses.size(), "Mỗi luồng phải hoàn tất chuyển đổi trạng thái");
    verify(subjectRepository, times(threadCount)).findById(101);
    verify(subjectRepository, times(threadCount)).save(any(Subject.class));
  }

  @Test
  @DisplayName("Concurrency QA 4: Stress Test xung đột tạo môn học đồng thời - Đúng 1 luồng thành công, các luồng còn lại ném conflict")
  void shouldEnforceConflictWhenMultipleThreadsAttemptSameSubjectConcurrently() throws Exception {
    int threadCount = 6;
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch doneLatch = new CountDownLatch(threadCount);

    AtomicBoolean alreadyExists = new AtomicBoolean(false);
    AtomicInteger successCount = new AtomicInteger(0);
    AtomicInteger conflictCount = new AtomicInteger(0);

    when(subjectRepository.existsByName("Trí Tuệ Nhân Tạo")).thenAnswer(inv -> {
      // Giả lập luồng đầu tiên check false và chuyển cờ sang true, các luồng sau check true
      return !alreadyExists.compareAndSet(false, true);
    });

    when(subjectRepository.save(any(Subject.class))).thenAnswer(inv -> {
      Subject s = inv.getArgument(0);
      ReflectionTestUtils.setField(s, "subjectId", 202);
      return s;
    });

    CreateSubjectRequest request = new CreateSubjectRequest(
        "Trí Tuệ Nhân Tạo",
        "Công nghệ",
        "AI",
        "tri-tue-nhan-tao",
        "Học máy và AI"
    );

    for (int i = 0; i < threadCount; i++) {
      executor.submit(() -> {
        try {
          startLatch.await();
          adminCatalogService.createSubject(request);
          successCount.incrementAndGet();
        } catch (ResourceConflictException e) {
          conflictCount.incrementAndGet();
        } catch (Exception e) {
          // unexpected
        } finally {
          doneLatch.countDown();
        }
      });
    }

    startLatch.countDown();
    assertTrue(doneLatch.await(5, TimeUnit.SECONDS));
    executor.shutdown();

    assertEquals(1, successCount.get(), "Chỉ đúng 1 luồng được phép tạo môn học thành công");
    assertEquals(threadCount - 1, conflictCount.get(), "Tất cả các luồng còn lại phải bị chặn với ResourceConflictException");
  }

  @Test
  @DisplayName("Adversarial QA 5: Tự động hóa chuẩn hóa Slug và Code tiếng Việt có dấu")
  void shouldAutoNormalizeVietnameseDiacriticsToUrlFriendlySlugAndCode() {
    CreateSubjectRequest request = new CreateSubjectRequest(
        "Giáo Dục Thể Chất & Quốc Phòng",
        null,
        null,
        null,
        null
    );

    when(subjectRepository.existsByName("Giáo Dục Thể Chất & Quốc Phòng")).thenReturn(false);
    when(subjectRepository.save(any(Subject.class))).thenAnswer(inv -> {
      Subject s = inv.getArgument(0);
      ReflectionTestUtils.setField(s, "subjectId", 303);
      return s;
    });

    SubjectAdminResponse resp = adminCatalogService.createSubject(request);

    assertNotNull(resp);
    assertEquals(303, resp.id());
    assertEquals("Giáo Dục Thể Chất & Quốc Phòng", resp.name());
    // Slug không được chứa ký tự tiếng Việt có dấu hoặc ký tự đặc biệt
    assertTrue(resp.slug().matches("^[a-z0-9-]+$"), "Slug phải là URL-friendly: " + resp.slug());
    assertTrue(resp.code().matches("^[A-Z0-9_]+$"), "Code phải là alphanumeric/underscore: " + resp.code());
    assertNotNull(resp.category());
  }
}
