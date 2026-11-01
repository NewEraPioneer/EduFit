package vn.edufit.profile.application.service;

import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edufit.profile.infra.persistence.entity.District;
import vn.edufit.profile.infra.persistence.entity.EducationLevel;
import vn.edufit.profile.infra.persistence.entity.GoalTag;
import vn.edufit.profile.infra.persistence.entity.Province;
import vn.edufit.profile.infra.persistence.entity.SpecializedSchool;
import vn.edufit.profile.infra.persistence.entity.Subject;
import vn.edufit.profile.infra.persistence.entity.SubjectTrack;
import vn.edufit.profile.infra.persistence.entity.TextbookSeries;
import vn.edufit.profile.infra.persistence.repository.EducationLevelRepository;
import vn.edufit.profile.infra.persistence.repository.SubjectRepository;
import vn.edufit.profile.web.response.CatalogResponse;
import vn.edufit.profile.web.response.LearningCatalogResponse;

@Service
public class CatalogQueryService {

  private final EducationLevelRepository educationLevelRepository;
  private final SubjectRepository subjectRepository;
  private final LearningCatalogLoader learningCatalogLoader;

  public CatalogQueryService(
      EducationLevelRepository educationLevelRepository,
      SubjectRepository subjectRepository,
      LearningCatalogLoader learningCatalogLoader
  ) {
    this.educationLevelRepository = educationLevelRepository;
    this.subjectRepository = subjectRepository;
    this.learningCatalogLoader = learningCatalogLoader;
  }

  @Transactional(readOnly = true)
  public LearningCatalogResponse getLearningCatalog() {
    LearningCatalog catalog = learningCatalogLoader.load();
    var levels = catalog.levels().stream()
        .filter(level -> Boolean.TRUE.equals(level.getIsActive()))
        .sorted(Comparator.comparing(EducationLevel::getSortOrder).thenComparing(EducationLevel::getLevelId))
        .map(level -> new LearningCatalogResponse.LevelItem(level.getLevelId(), level.getName(), level.getSortOrder()))
        .toList();
    var subjects = catalog.subjects().stream()
        .filter(subject -> Boolean.TRUE.equals(subject.getIsActive()))
        .sorted(Comparator.comparing(Subject::getSubjectId))
        .map(subject -> new LearningCatalogResponse.SubjectItem(subject.getSubjectId(), subject.getName()))
        .toList();
    var tracks = catalog.tracks().stream()
        .filter(SubjectTrack::isActive)
        .sorted(Comparator.comparing(SubjectTrack::getSubjectId).thenComparing(SubjectTrack::getSortOrder))
        .map(track -> new LearningCatalogResponse.TrackItem(track.getTrackId(), track.getSubjectId(),
            track.getCode(), track.getName(), track.getLevelIds().stream().sorted().toList()))
        .toList();
    var goalTags = catalog.goalTags().stream()
        .filter(GoalTag::isActive)
        .sorted(Comparator.comparing(GoalTag::getSortOrder).thenComparing(GoalTag::getGoalTagId))
        .map(CatalogQueryService::toGoalTagItem)
        .toList();
    var rules = catalog.rules().stream()
        .map(rule -> new LearningCatalogResponse.GoalTagRuleItem(
            rule.getGoalTagId(), rule.getSubjectId(), rule.getTrackId(), rule.getLevelId()))
        .toList();
    var textbooks = catalog.textbooks().stream()
        .filter(TextbookSeries::isActive)
        .sorted(Comparator.comparing(TextbookSeries::getSortOrder))
        .map(series -> new LearningCatalogResponse.TextbookItem(series.getSeriesId(), series.getCode(),
            series.getName(), series.getSubjectIds().stream().sorted().toList()))
        .toList();
    var provinces = catalog.provinces().stream()
        .filter(Province::isActive)
        .sorted(Comparator.comparing(Province::getSortOrder).thenComparing(Province::getName))
        .map(province -> new LearningCatalogResponse.ProvinceItem(province.getProvinceId(), province.getName()))
        .toList();
    var districts = catalog.districts().stream()
        .filter(District::isActive)
        .sorted(Comparator.comparing(District::getProvinceId).thenComparing(District::getSortOrder))
        .map(district -> new LearningCatalogResponse.DistrictItem(
            district.getDistrictId(), district.getProvinceId(), district.getName()))
        .toList();
    var schools = catalog.schools().stream()
        .filter(SpecializedSchool::isActive)
        .sorted(Comparator.comparing(SpecializedSchool::getProvinceId).thenComparing(SpecializedSchool::getName))
        .map(school -> new LearningCatalogResponse.SchoolItem(
            school.getSchoolId(), school.getProvinceId(), school.getName()))
        .toList();
    return new LearningCatalogResponse(levels, subjects, tracks, goalTags, rules, textbooks, provinces,
        districts, schools);
  }

  @Transactional(readOnly = true)
  public List<LearningCatalogResponse.GoalTagItem> getGoalTags(Integer levelId, Integer subjectId, Integer trackId) {
    return learningCatalogLoader.load().goalTagsFor(levelId, subjectId, trackId).stream()
        .map(CatalogQueryService::toGoalTagItem)
        .toList();
  }

  private static LearningCatalogResponse.GoalTagItem toGoalTagItem(GoalTag tag) {
    return new LearningCatalogResponse.GoalTagItem(tag.getGoalTagId(), tag.getCode(), tag.getName(),
        tag.getDescription(), tag.requiresTextbook(), tag.requiresExamProvince(), tag.isPremium());
  }

  @Transactional(readOnly = true)
  public CatalogResponse getCatalog() {
    List<CatalogResponse.EducationLevelItem> levels = getEducationLevels();
    List<CatalogResponse.SubjectItem> subjects = getSubjects();
    return new CatalogResponse(levels, subjects);
  }

  @Transactional(readOnly = true)
  public List<CatalogResponse.EducationLevelItem> getEducationLevels() {
    return educationLevelRepository.findByIsActiveTrueOrderBySortOrderAsc()
        .stream()
        .map(level -> new CatalogResponse.EducationLevelItem(
            level.getLevelId(),
            level.getName(),
            level.getSortOrder()
        ))
        .toList();
  }

  @Transactional(readOnly = true)
  public List<CatalogResponse.SubjectItem> getSubjects() {
    return subjectRepository.findByIsActiveTrueOrderByNameAsc()
        .stream()
        .map(subj -> new CatalogResponse.SubjectItem(
            subj.getSubjectId(),
            subj.getName()
        ))
        .toList();
  }
}
