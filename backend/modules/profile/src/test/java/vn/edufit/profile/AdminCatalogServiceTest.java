package vn.edufit.profile;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;
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
import vn.edufit.profile.web.request.CreateSubjectRequest;
import vn.edufit.profile.web.response.SubjectAdminResponse;
import vn.edufit.shared.exception.EntityNotFoundException;
import vn.edufit.shared.exception.ResourceConflictException;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminCatalogService Unit Tests")
class AdminCatalogServiceTest {

  @Mock
  private SubjectRepository subjectRepository;

  private AdminCatalogService service;

  @BeforeEach
  void setUp() {
    service = new AdminCatalogService(subjectRepository);
  }

  @Test
  @DisplayName("getAllSubjects trả về danh sách môn học với đầy đủ metadata")
  void getAllSubjects_ReturnsMappedResponses() {
    Subject s1 = new Subject("Toán", true);
    ReflectionTestUtils.setField(s1, "subjectId", 1);
    Subject s2 = new Subject("Tiếng Anh", false);
    ReflectionTestUtils.setField(s2, "subjectId", 2);

    when(subjectRepository.findAllByOrderByNameAsc()).thenReturn(List.of(s1, s2));

    List<SubjectAdminResponse> results = service.getAllSubjects();

    assertEquals(2, results.size());

    SubjectAdminResponse r1 = results.get(0);
    assertEquals(1, r1.id());
    assertEquals("Toán", r1.name());
    assertTrue(r1.isActive());
    assertEquals("Khoa học tự nhiên", r1.category());
    assertNotNull(r1.slug());
    assertNotNull(r1.code());

    SubjectAdminResponse r2 = results.get(1);
    assertEquals(2, r2.id());
    assertEquals("Tiếng Anh", r2.name());
    assertFalse(r2.isActive());
    assertEquals("Ngoại ngữ", r2.category());
  }

  @Test
  @DisplayName("createSubject tạo môn học thành công với metadata hợp lệ")
  void createSubject_Success() {
    CreateSubjectRequest request = new CreateSubjectRequest(
        "Lập Trình Python",
        "Công nghệ thông tin",
        "PYTHON",
        "lap-trinh-python",
        "Khóa học lập trình Python cơ bản"
    );

    when(subjectRepository.existsByName("Lập Trình Python")).thenReturn(false);

    Subject saved = new Subject("Lập Trình Python", true);
    ReflectionTestUtils.setField(saved, "subjectId", 10);
    when(subjectRepository.save(any(Subject.class))).thenReturn(saved);

    SubjectAdminResponse response = service.createSubject(request);

    assertNotNull(response);
    assertEquals(10, response.id());
    assertEquals("Lập Trình Python", response.name());
    assertEquals("PYTHON", response.code());
    assertEquals("lap-trinh-python", response.slug());
    assertEquals("Công nghệ thông tin", response.category());
    assertEquals("Khóa học lập trình Python cơ bản", response.description());
    assertTrue(response.isActive());
  }

  @Test
  @DisplayName("createSubject ném ngoại lệ khi trùng tên môn học")
  void createSubject_Conflict_ThrowsException() {
    CreateSubjectRequest request = new CreateSubjectRequest(
        "Toán",
        null,
        null,
        null,
        null
    );

    when(subjectRepository.existsByName("Toán")).thenReturn(true);

    assertThrows(ResourceConflictException.class, () -> service.createSubject(request));
    verify(subjectRepository, never()).save(any());
  }

  @Test
  @DisplayName("toggleSubjectStatus chuyển đổi từ true sang false và ngược lại")
  void toggleSubjectStatus_FlipsStatus() {
    Subject subject = new Subject("Vật Lý", true);
    ReflectionTestUtils.setField(subject, "subjectId", 5);

    when(subjectRepository.findById(5)).thenReturn(Optional.of(subject));
    when(subjectRepository.save(any(Subject.class))).thenAnswer(invocation -> invocation.getArgument(0));

    SubjectAdminResponse toggledOff = service.toggleSubjectStatus(5);
    assertFalse(toggledOff.isActive());

    SubjectAdminResponse toggledOn = service.toggleSubjectStatus(5);
    assertTrue(toggledOn.isActive());
  }

  @Test
  @DisplayName("toggleSubjectStatus ném EntityNotFoundException khi không tìm thấy môn học")
  void toggleSubjectStatus_NotFound_ThrowsException() {
    when(subjectRepository.findById(999)).thenReturn(Optional.empty());

    assertThrows(EntityNotFoundException.class, () -> service.toggleSubjectStatus(999));
  }

  @Test
  @DisplayName("updateSubjectStatus cập nhật trạng thái rõ ràng")
  void updateSubjectStatus_ExplicitStatus() {
    Subject subject = new Subject("Hóa Học", true);
    ReflectionTestUtils.setField(subject, "subjectId", 6);

    when(subjectRepository.findById(6)).thenReturn(Optional.of(subject));
    when(subjectRepository.save(any(Subject.class))).thenAnswer(invocation -> invocation.getArgument(0));

    SubjectAdminResponse response = service.updateSubjectStatus(6, false);
    assertFalse(response.isActive());
  }
}
