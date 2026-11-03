package vn.edufit.progress.application.service;

import java.time.Clock;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edufit.connection.api.ConnectionFacade;
import vn.edufit.profile.api.dto.LearningNeedView;
import vn.edufit.progress.application.dto.TopicViews.SaveTopicCommand;
import vn.edufit.progress.application.dto.TopicViews.TopicView;
import vn.edufit.progress.domain.model.Topic;
import vn.edufit.progress.domain.repository.TopicDomainRepository;
import vn.edufit.shared.auth.CurrentUser;
import vn.edufit.shared.exception.EntityNotFoundException;
import vn.edufit.shared.exception.ErrorCode;
import vn.edufit.shared.exception.ForbiddenOperationException;
import vn.edufit.shared.exception.InvalidOperationException;
import vn.edufit.shared.exception.ResourceConflictException;

/**
 * Danh mục chủ đề chuẩn theo môn × khối (Giai đoạn 3): admin quản lý; gia sư chọn chủ đề cho từng cột mốc khi lập
 * lộ trình. Hệ thống không có sẵn chủ đề nào — chưa nhập thì radar hiện "—" kèm lý do.
 */
@Service
public class TopicCatalogService {

  private final TopicDomainRepository topicRepository;
  private final ProgressAccessGuard accessGuard;
  private final ClassNeedResolver needResolver;
  private final Clock clock;

  public TopicCatalogService(
      TopicDomainRepository topicRepository,
      ProgressAccessGuard accessGuard,
      ClassNeedResolver needResolver,
      Clock clock
  ) {
    this.topicRepository = topicRepository;
    this.accessGuard = accessGuard;
    this.needResolver = needResolver;
    this.clock = Objects.requireNonNull(clock);
  }

  @Transactional(readOnly = true)
  public List<TopicView> listAll(CurrentUser actor) {
    requireAdmin(actor);
    return topicRepository.findAll().stream().map(TopicView::from).toList();
  }

  /** Chủ đề đang dùng được cho môn × khối của lớp mà gia sư đang dạy. */
  @Transactional(readOnly = true)
  public List<TopicView> listForClass(CurrentUser actor, UUID classId) {
    ConnectionFacade.ClassDetailView cls = accessGuard.requireClass(classId);
    accessGuard.requireTutorOfClass(actor, cls);
    LearningNeedView need = needResolver.requireSubjectAndLevel(cls, "để chọn chủ đề chuẩn");
    return topicRepository.findActiveBySubjectAndLevel(need.subjectId(), need.educationLevelId()).stream()
        .map(TopicView::from).toList();
  }

  @Transactional
  public TopicView create(CurrentUser actor, SaveTopicCommand cmd) {
    requireAdmin(actor);
    if (cmd.subjectId() == null || cmd.educationLevelId() == null) {
      throw new InvalidOperationException(ErrorCode.VALIDATION_FAILED, "Cần chọn môn học và khối lớp cho chủ đề");
    }
    requireUniqueName(cmd.subjectId(), cmd.educationLevelId(), cmd.name(), null);
    Topic topic = Topic.createNew(cmd.subjectId(), cmd.educationLevelId(), cmd.name(),
        cmd.sortOrder() != null ? cmd.sortOrder() : 0, actor.getUserId(), clock.instant());
    return TopicView.from(topicRepository.save(topic));
  }

  /** Đổi tên, thứ tự hoặc lưu trữ; môn và khối không đổi được vì cột mốc đã gắn chủ đề phụ thuộc vào chúng. */
  @Transactional
  public TopicView update(CurrentUser actor, UUID topicId, SaveTopicCommand cmd) {
    requireAdmin(actor);
    Topic topic = topicRepository.findById(topicId)
        .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy chủ đề với ID: " + topicId));
    if (cmd.version() == null || cmd.version() != topic.getVersion()) {
      throw new ResourceConflictException(
          ErrorCode.CONFLICT_DETECTED, "Chủ đề đã được sửa ở phiên làm việc khác. Hãy tải lại rồi sửa tiếp.");
    }
    requireUniqueName(topic.getSubjectId(), topic.getEducationLevelId(), cmd.name(), topicId);
    topic.revise(cmd.name(), cmd.sortOrder() != null ? cmd.sortOrder() : topic.getSortOrder(), cmd.archived(),
        clock.instant());
    return TopicView.from(topicRepository.save(topic));
  }

  /**
   * Kiểm tra chủ đề gắn cho cột mốc khi lập/sửa lộ trình: phải thuộc môn × khối của lớp; chủ đề đã lưu trữ chỉ được
   * giữ lại trên cột mốc vốn đã gắn nó.
   *
   * @param requested  chủ đề gửi lên (bỏ qua null).
   * @param alreadyUsed chủ đề các cột mốc hiện có của lộ trình đang gắn.
   */
  public void requireAssignable(ConnectionFacade.ClassDetailView cls, Collection<UUID> requested, Set<UUID> alreadyUsed) {
    Set<UUID> ids = requested.stream().filter(Objects::nonNull).collect(Collectors.toSet());
    if (ids.isEmpty()) return;
    LearningNeedView need = needResolver.requireSubjectAndLevel(cls, "để gắn chủ đề chuẩn");
    Map<UUID, Topic> found = topicRepository.findByIdIn(ids).stream()
        .collect(Collectors.toMap(Topic::getTopicId, Function.identity()));
    for (UUID id : ids) {
      Topic topic = found.get(id);
      if (topic == null || !topic.belongsTo(need.subjectId(), need.educationLevelId())) {
        throw new InvalidOperationException(
            ErrorCode.VALIDATION_FAILED, "Chủ đề chuẩn không thuộc môn và khối của lớp học");
      }
      if (topic.isArchived() && !alreadyUsed.contains(id)) {
        throw new InvalidOperationException(
            ErrorCode.VALIDATION_FAILED, "Chủ đề \"" + topic.getName() + "\" đã ngừng sử dụng");
      }
    }
  }

  private void requireUniqueName(int subjectId, int levelId, String name, UUID selfId) {
    if (name == null) return; // Topic tự báo lỗi tên trống
    String key = name.trim().toLowerCase();
    Set<String> taken = new HashSet<>();
    topicRepository.findAll().stream()
        .filter(t -> t.getSubjectId() == subjectId && t.getEducationLevelId() == levelId
            && !t.getTopicId().equals(selfId))
        .forEach(t -> taken.add(t.getName().toLowerCase()));
    if (taken.contains(key)) {
      throw new ResourceConflictException(ErrorCode.CONFLICT_DETECTED, "Môn và khối này đã có chủ đề \"" + name.trim() + "\"");
    }
  }

  private static void requireAdmin(CurrentUser actor) {
    if (!actor.hasRole("ADMIN")) {
      throw new ForbiddenOperationException("Chỉ quản trị viên mới quản lý được danh mục chủ đề chuẩn");
    }
  }
}
