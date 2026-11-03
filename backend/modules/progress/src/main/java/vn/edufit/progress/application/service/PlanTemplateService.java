package vn.edufit.progress.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edufit.connection.api.ConnectionFacade;
import vn.edufit.profile.api.ProfileFacade;
import vn.edufit.profile.api.dto.LearningNeedView;
import vn.edufit.progress.application.dto.PlanTemplateViews.PlanTemplateView;
import vn.edufit.progress.application.dto.PlanTemplateViews.SavePlanTemplateCommand;
import vn.edufit.progress.application.dto.PlanTemplateViews.TemplateMilestoneView;
import vn.edufit.progress.domain.model.PlanTemplate;
import vn.edufit.progress.domain.repository.PlanTemplateDomainRepository;
import vn.edufit.shared.auth.CurrentUser;
import vn.edufit.shared.exception.EntityNotFoundException;
import vn.edufit.shared.exception.ErrorCode;
import vn.edufit.shared.exception.ForbiddenOperationException;
import vn.edufit.shared.exception.ResourceConflictException;

/**
 * Mẫu lộ trình (mục 3.4): admin quản lý mẫu hệ thống; gia sư xem mẫu hệ thống + mẫu riêng, nhân bản vào lớp
 * (frontend điền sẵn biểu mẫu lập lộ trình) và lưu mẫu riêng.
 */
@Service
public class PlanTemplateService {

  private final PlanTemplateDomainRepository templateRepository;
  private final ProgressAccessGuard accessGuard;
  private final ProfileFacade profileFacade;
  private final ClassNeedResolver needResolver;
  private final Clock clock;

  public PlanTemplateService(
      PlanTemplateDomainRepository templateRepository,
      ProgressAccessGuard accessGuard,
      ProfileFacade profileFacade,
      ClassNeedResolver needResolver,
      Clock clock
  ) {
    this.templateRepository = templateRepository;
    this.accessGuard = accessGuard;
    this.profileFacade = profileFacade;
    this.needResolver = needResolver;
    this.clock = Objects.requireNonNull(clock);
  }

  /**
   * Gia sư: mẫu hệ thống + mẫu riêng; có {@code classId} thì chỉ mẫu hợp môn × khối × mục tiêu của lớp, mẫu khớp
   * đúng mục tiêu và mẫu riêng lên trước. Admin: mẫu hệ thống.
   */
  @Transactional(readOnly = true)
  public List<PlanTemplateView> list(CurrentUser actor, UUID classId) {
    UUID tutorId = viewerTutorId(actor);
    List<PlanTemplate> visible = templateRepository.findVisibleTo(tutorId);
    if (classId == null) {
      return visible.stream().map(t -> PlanTemplateView.from(t, tutorId)).toList();
    }
    LearningNeedView need = requireClassNeed(actor, classId);
    return visible.stream()
        .filter(t -> t.matches(need.subjectId(), need.educationLevelId(), need.goalTagId()))
        .sorted(Comparator
            .comparing((PlanTemplate t) -> t.getGoalTagId() == null)
            .thenComparing(PlanTemplate::isSystem))
        .map(t -> PlanTemplateView.from(t, tutorId))
        .toList();
  }

  @Transactional
  public PlanTemplateView create(CurrentUser actor, SavePlanTemplateCommand cmd) {
    UUID ownerTutorId = actor.hasRole("ADMIN") ? null : requireTutorId(actor);
    Catalog catalog = resolveCatalog(actor, cmd);
    Instant now = clock.instant();
    PlanTemplate template = new PlanTemplate(UUID.randomUUID(), ownerTutorId, catalog.subjectId(),
        catalog.educationLevelId(), catalog.goalTagId(), cmd.title(), cmd.description(), toMilestones(cmd),
        actor.getUserId(), 0, now, now);
    return PlanTemplateView.from(templateRepository.save(template), ownerTutorId);
  }

  @Transactional
  public PlanTemplateView update(CurrentUser actor, UUID templateId, SavePlanTemplateCommand cmd) {
    PlanTemplate template = requireManageable(actor, templateId);
    if (cmd.version() == null || cmd.version() != template.getVersion()) {
      throw new ResourceConflictException(
          ErrorCode.CONFLICT_DETECTED, "Mẫu lộ trình đã được sửa ở phiên làm việc khác. Hãy tải lại rồi sửa tiếp.");
    }
    Catalog catalog = resolveCatalog(actor, cmd);
    template.revise(catalog.subjectId(), catalog.educationLevelId(), catalog.goalTagId(),
        cmd.title(), cmd.description(), toMilestones(cmd), clock.instant());
    return PlanTemplateView.from(templateRepository.save(template), template.getOwnerTutorId());
  }

  @Transactional
  public void delete(CurrentUser actor, UUID templateId) {
    templateRepository.delete(requireManageable(actor, templateId).getTemplateId());
  }

  /** Mẫu hệ thống chỉ admin sửa/xóa; mẫu riêng chỉ gia sư sở hữu sửa/xóa. */
  private PlanTemplate requireManageable(CurrentUser actor, UUID templateId) {
    PlanTemplate template = templateRepository.findById(templateId)
        .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy mẫu lộ trình với ID: " + templateId));
    boolean allowed = template.isSystem()
        ? actor.hasRole("ADMIN")
        : actor.hasRole("TUTOR") && template.getOwnerTutorId().equals(profileFacade.getTutorIdByUserId(actor.getUserId()));
    if (!allowed) {
      throw new ForbiddenOperationException(template.isSystem()
          ? "Chỉ quản trị viên được sửa mẫu lộ trình hệ thống"
          : "Chỉ gia sư tạo mẫu mới được sửa mẫu riêng này");
    }
    return template;
  }

  private record Catalog(Integer subjectId, Integer educationLevelId, Integer goalTagId) {}

  private Catalog resolveCatalog(CurrentUser actor, SavePlanTemplateCommand cmd) {
    if (cmd.classId() != null) {
      LearningNeedView need = requireClassNeed(actor, cmd.classId());
      return new Catalog(need.subjectId(), need.educationLevelId(), need.goalTagId());
    }
    return new Catalog(cmd.subjectId(), cmd.educationLevelId(), cmd.goalTagId());
  }

  /** Nhu cầu học của lớp mà gia sư đang dạy (môn, khối, mục tiêu). */
  private LearningNeedView requireClassNeed(CurrentUser actor, UUID classId) {
    ConnectionFacade.ClassDetailView cls = accessGuard.requireClass(classId);
    accessGuard.requireTutorOfClass(actor, cls);
    return needResolver.requireSubjectAndLevel(cls, "để chọn mẫu lộ trình");
  }

  private UUID viewerTutorId(CurrentUser actor) {
    if (actor.hasRole("ADMIN")) return null;
    return requireTutorId(actor);
  }

  private UUID requireTutorId(CurrentUser actor) {
    UUID tutorId = actor.hasRole("TUTOR") ? profileFacade.getTutorIdByUserId(actor.getUserId()) : null;
    if (tutorId == null) {
      throw new ForbiddenOperationException("Chỉ Gia sư hoặc Quản trị viên mới dùng được mẫu lộ trình");
    }
    return tutorId;
  }

  private static List<PlanTemplate.TemplateMilestone> toMilestones(SavePlanTemplateCommand cmd) {
    List<TemplateMilestoneView> items = cmd.milestones() != null ? cmd.milestones() : List.of();
    return items.stream()
        .map(m -> new PlanTemplate.TemplateMilestone(
            m.title(), m.description(), m.weight(), m.suggestedSessions(), m.criteria()))
        .toList();
  }
}
