package vn.edufit.profile.application.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.edufit.profile.infra.persistence.repository.DistrictRepository;
import vn.edufit.profile.infra.persistence.repository.EducationLevelRepository;
import vn.edufit.profile.infra.persistence.repository.GoalTagRepository;
import vn.edufit.profile.infra.persistence.repository.GoalTagRuleRepository;
import vn.edufit.profile.infra.persistence.repository.ProvinceRepository;
import vn.edufit.profile.infra.persistence.repository.SpecializedSchoolRepository;
import vn.edufit.profile.infra.persistence.repository.SubjectRepository;
import vn.edufit.profile.infra.persistence.repository.SubjectTrackRepository;
import vn.edufit.profile.infra.persistence.repository.TextbookSeriesRepository;

/** Nạp toàn bộ danh mục nhu cầu học (vài trăm dòng) thành một {@link LearningCatalog}. */
@Component
public class LearningCatalogLoader {

  private final EducationLevelRepository levels;
  private final SubjectRepository subjects;
  private final SubjectTrackRepository tracks;
  private final GoalTagRepository goalTags;
  private final GoalTagRuleRepository rules;
  private final TextbookSeriesRepository textbooks;
  private final ProvinceRepository provinces;
  private final DistrictRepository districts;
  private final SpecializedSchoolRepository schools;

  public LearningCatalogLoader(
      EducationLevelRepository levels,
      SubjectRepository subjects,
      SubjectTrackRepository tracks,
      GoalTagRepository goalTags,
      GoalTagRuleRepository rules,
      TextbookSeriesRepository textbooks,
      ProvinceRepository provinces,
      DistrictRepository districts,
      SpecializedSchoolRepository schools) {
    this.levels = levels;
    this.subjects = subjects;
    this.tracks = tracks;
    this.goalTags = goalTags;
    this.rules = rules;
    this.textbooks = textbooks;
    this.provinces = provinces;
    this.districts = districts;
    this.schools = schools;
  }

  @Transactional(readOnly = true)
  public LearningCatalog load() {
    return new LearningCatalog(
        levels.findAllByOrderBySortOrderAscLevelIdAsc(),
        subjects.findAllByOrderByNameAsc(),
        tracks.findAll(),
        goalTags.findAll(),
        rules.findAll(),
        textbooks.findAll(),
        provinces.findAll(),
        districts.findAll(),
        schools.findAll());
  }
}
