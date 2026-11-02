package vn.edufit.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.edufit.profile.application.service.CatalogFixtures;
import vn.edufit.profile.application.service.CatalogQueryService;
import vn.edufit.profile.application.service.LearningCatalogLoader;
import vn.edufit.profile.infra.persistence.entity.EducationLevel;
import vn.edufit.profile.infra.persistence.entity.Subject;
import vn.edufit.profile.infra.persistence.repository.EducationLevelRepository;
import vn.edufit.profile.infra.persistence.repository.SubjectRepository;
import vn.edufit.profile.web.response.CatalogResponse;

@ExtendWith(MockitoExtension.class)
class CatalogQueryServiceTest {

  @Mock
  private EducationLevelRepository educationLevelRepository;

  @Mock
  private SubjectRepository subjectRepository;

  @Mock
  private LearningCatalogLoader learningCatalogLoader;

  private CatalogQueryService catalogQueryService;

  @BeforeEach
  void setUp() {
    catalogQueryService = new CatalogQueryService(educationLevelRepository, subjectRepository, learningCatalogLoader);
  }

  @Test
  @DisplayName("Lấy danh mục cấp học và môn học thành công")
  void shouldReturnFullCatalog() {
    when(educationLevelRepository.findByIsActiveTrueOrderBySortOrderAsc()).thenReturn(List.of(
        new EducationLevel("Lớp 10", 10, true),
        new EducationLevel("Lớp 11", 11, true)
    ));
    when(subjectRepository.findByIsActiveTrueOrderByNameAsc()).thenReturn(List.of(
        new Subject("Toán", true),
        new Subject("Vật lý", true)
    ));

    CatalogResponse catalog = catalogQueryService.getCatalog();

    assertNotNull(catalog);
    assertEquals(2, catalog.educationLevels().size());
    assertEquals(2, catalog.subjects().size());
    assertEquals("Lớp 10", catalog.educationLevels().get(0).name());
    assertEquals("Toán", catalog.subjects().get(0).name());
  }

  @Test
  @DisplayName("Danh mục nhu cầu học chỉ trả mục đang hoạt động kèm luật mục tiêu")
  void shouldReturnLearningCatalogWithRules() {
    when(learningCatalogLoader.load()).thenReturn(CatalogFixtures.catalog());

    var catalog = catalogQueryService.getLearningCatalog();

    assertEquals(4, catalog.educationLevels().size());
    assertEquals(List.of("Toán", "Khoa học tự nhiên"), catalog.subjects().stream().map(s -> s.name()).toList());
    assertEquals(List.of("SCI_INTEGRATED", "SCI_PHYSICS"), catalog.tracks().stream().map(t -> t.code()).toList());
    assertEquals(List.of(1, 2), catalog.tracks().getFirst().levelIds());
    assertTrue(catalog.goalTagRules().size() > 10);
  }

  @Test
  @DisplayName("Lọc mục tiêu theo lớp/môn/nhánh: lớp 7 Toán không có Luyện thi vào 10")
  void shouldFilterGoalTags() {
    when(learningCatalogLoader.load()).thenReturn(CatalogFixtures.catalog());
    assertEquals(List.of("FOUNDATION", "GRADE_BOOST", "GIFTED"),
        catalogQueryService.getGoalTags(CatalogFixtures.LEVEL_7, CatalogFixtures.MATH, null).stream()
            .map(tag -> tag.code()).toList());
  }
}
