package vn.edufit.progress.infra.persistence.adapter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.edufit.progress.domain.model.PeriodReport;
import vn.edufit.progress.domain.repository.PeriodReportDomainRepository;
import vn.edufit.progress.infra.persistence.entity.PeriodReportJpaEntity;
import vn.edufit.progress.infra.persistence.repository.SpringDataPeriodReportRepository;

@Component
@RequiredArgsConstructor
public class PeriodReportRepositoryAdapter implements PeriodReportDomainRepository {

  private static final String DRAFT = PeriodReport.Status.DRAFT.name();

  private final SpringDataPeriodReportRepository repository;

  @Override
  public PeriodReport save(PeriodReport report) {
    return toDomain(repository.saveAndFlush(toEntity(report)));
  }

  @Override
  public Optional<PeriodReport> findById(UUID reportId) {
    return repository.findById(reportId).map(PeriodReportRepositoryAdapter::toDomain);
  }

  @Override
  public boolean existsByClassIdAndPeriodIndex(UUID classId, int periodIndex) {
    return repository.existsByClassIdAndPeriodIndex(classId, periodIndex);
  }

  @Override
  public List<PeriodReport> findByClassId(UUID classId) {
    return repository.findByClassIdOrderByPeriodIndexDesc(classId).stream()
        .map(PeriodReportRepositoryAdapter::toDomain).toList();
  }

  @Override
  public List<PeriodReport> findDraftsByClassIdIn(Collection<UUID> classIds) {
    if (classIds.isEmpty()) return List.of();
    return repository.findByClassIdInAndStatusOrderByAutoSendAtAsc(classIds, DRAFT).stream()
        .map(PeriodReportRepositoryAdapter::toDomain).toList();
  }

  @Override
  public List<PeriodReport> findDraftsDueBy(Instant now) {
    return repository.findByStatusAndAutoSendAtLessThanEqualOrderByAutoSendAtAsc(DRAFT, now).stream()
        .map(PeriodReportRepositoryAdapter::toDomain).toList();
  }

  private static PeriodReportJpaEntity toEntity(PeriodReport r) {
    PeriodReport.Snapshot s = r.getSnapshot();
    return PeriodReportJpaEntity.builder()
        .reportId(r.getReportId())
        .classId(r.getClassId())
        .periodIndex(r.getPeriodIndex())
        .periodStart(r.getPeriodStart())
        .periodEnd(r.getPeriodEnd())
        .onTrackStatus(s.onTrackStatus())
        .onTrackExplanation(s.onTrackExplanation())
        .progressPercentage(toShort(s.progressPercentage()))
        .progressDelta(toShort(s.progressDelta()))
        .attendedCount(s.attendedCount())
        .countedCount(s.countedCount())
        .highlight(s.highlight())
        .nextFocusTitle(s.nextFocusTitle())
        .achievedMilestones(new ArrayList<>(s.achievedMilestones()))
        .tutorSummary(r.getTutorSummary())
        .status(r.getStatus().name())
        .autoSendAt(r.getAutoSendAt())
        .sentAt(r.getSentAt())
        .version(r.getVersion())
        .createdAt(r.getCreatedAt())
        .build();
  }

  private static PeriodReport toDomain(PeriodReportJpaEntity e) {
    return new PeriodReport(
        e.getReportId(),
        e.getClassId(),
        e.getPeriodIndex(),
        e.getPeriodStart(),
        e.getPeriodEnd(),
        new PeriodReport.Snapshot(
            e.getOnTrackStatus(),
            e.getOnTrackExplanation(),
            e.getProgressPercentage() != null ? e.getProgressPercentage().intValue() : null,
            e.getProgressDelta() != null ? e.getProgressDelta().intValue() : null,
            e.getAttendedCount(),
            e.getCountedCount(),
            e.getHighlight(),
            e.getNextFocusTitle(),
            List.copyOf(e.getAchievedMilestones())),
        e.getTutorSummary(),
        PeriodReport.Status.valueOf(e.getStatus()),
        e.getAutoSendAt(),
        e.getSentAt(),
        e.getVersion(),
        e.getCreatedAt());
  }

  private static Short toShort(Integer value) {
    return value != null ? value.shortValue() : null;
  }
}
