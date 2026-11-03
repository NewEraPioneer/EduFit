package vn.edufit.progress.application.service;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import vn.edufit.ai.api.AiFeature;
import vn.edufit.ai.api.AiGateway;
import vn.edufit.ai.api.AiRequest;
import vn.edufit.ai.api.AiUsageQuery;
import vn.edufit.connection.api.ConnectionFacade;
import vn.edufit.profile.api.dto.LearningNeedView;
import vn.edufit.progress.application.dto.PlanDraftView;
import vn.edufit.progress.application.dto.PlanDraftView.DraftMilestoneView;
import vn.edufit.progress.domain.model.Milestone;
import vn.edufit.progress.domain.model.Topic;
import vn.edufit.progress.domain.policy.RoadmapShapePolicy;
import vn.edufit.progress.domain.repository.TopicDomainRepository;
import vn.edufit.shared.auth.CurrentUser;

/**
 * Bản nháp lộ trình bằng AI (mục 3.4, Giai đoạn 3). Chỉ gửi tên môn, khối, mục tiêu, chương trình, bộ sách và danh
 * mục chủ đề chuẩn — không gửi dữ liệu cá nhân của học sinh (NFR-09). Kết quả được kiểm tra đủ 3–8 cột mốc, mỗi cột
 * mốc 1–4 tiêu chí rồi trả về để frontend điền vào biểu mẫu; AI lỗi hoặc hết lượt thì trả lý do và gợi ý dùng mẫu.
 */
@Slf4j
@Service
public class PlanDraftService {

  public static final int HOURLY_QUOTA = 10;

  static final String QUOTA_EXHAUSTED = "Bạn đã dùng hết " + HOURLY_QUOTA
      + " lượt gợi ý AI trong 1 giờ. Hãy chọn mẫu lộ trình hoặc thử lại sau.";
  static final String AI_UNAVAILABLE =
      "AI hiện không khả dụng. Hãy chọn mẫu lộ trình phù hợp hoặc tự lập lộ trình.";
  static final String AI_INVALID =
      "AI trả về bản nháp không hợp lệ. Hãy thử lại hoặc chọn mẫu lộ trình.";

  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final int MAX_DESCRIPTION = 1000;
  private static final int MAX_TEXT = 200;

  private static final String SYSTEM_PROMPT = """
      Bạn là chuyên gia sư phạm bậc THCS tại Việt Nam, giúp gia sư lập lộ trình học theo cột mốc.
      QUY TẮC:
      1. Trả về DUY NHẤT một đối tượng JSON, không kèm giải thích, không dùng Markdown.
      2. Lộ trình có từ 3 đến 8 cột mốc, sắp theo thứ tự học. Mỗi cột mốc ứng với khoảng 2–6 buổi học.
      3. Mỗi cột mốc có 1–4 tiêu chí đạt, mô tả hành vi quan sát được (ví dụ "Tự giải đúng bài hệ phương trình bằng phương pháp thế").
      4. Tiêu đề tối đa 200 ký tự, mỗi tiêu chí tối đa 200 ký tự, mô tả tối đa 300 ký tự.
      5. Nếu có danh sách chủ đề chuẩn, trường "topic" phải là đúng một tên trong danh sách đó, không thì để null.
      6. Không bịa ngày tháng, điểm số hay thông tin cá nhân.
      ĐỊNH DẠNG:
      {"milestones":[{"title":"...","description":"...","weight":1,"suggestedSessions":3,"criteria":["..."],"topic":null}]}
      "weight" là số nguyên 1–5 thể hiện độ quan trọng của cột mốc với mục tiêu.
      """;

  private final AiGateway aiGateway;
  private final AiUsageQuery aiUsageQuery;
  private final ProgressAccessGuard accessGuard;
  private final ClassNeedResolver needResolver;
  private final TopicDomainRepository topicRepository;
  private final Clock clock;

  public PlanDraftService(
      AiGateway aiGateway,
      AiUsageQuery aiUsageQuery,
      ProgressAccessGuard accessGuard,
      ClassNeedResolver needResolver,
      TopicDomainRepository topicRepository,
      Clock clock
  ) {
    this.aiGateway = aiGateway;
    this.aiUsageQuery = aiUsageQuery;
    this.accessGuard = accessGuard;
    this.needResolver = needResolver;
    this.topicRepository = topicRepository;
    this.clock = Objects.requireNonNull(clock);
  }

  /** Không mở transaction: cuộc gọi AI có thể mất tới 15 giây. */
  public PlanDraftView draft(CurrentUser actor, UUID classId) {
    ConnectionFacade.ClassDetailView cls = accessGuard.requireClass(classId);
    accessGuard.requireTutorOfClass(actor, cls);
    accessGuard.requireActiveClass(cls, "Chỉ có thể gợi ý lộ trình cho lớp học đang hoạt động");
    LearningNeedView need = needResolver.requireSubjectAndLevel(cls, "để gợi ý lộ trình bằng AI");
    List<Topic> topics = topicRepository.findActiveBySubjectAndLevel(need.subjectId(), need.educationLevelId());

    long used = aiUsageQuery.countRequestsSince(
        actor.getUserId(), AiFeature.LEARNING_PLAN_DRAFT, clock.instant().minus(Duration.ofHours(1)));
    if (used >= HOURLY_QUOTA) return PlanDraftView.unavailable(QUOTA_EXHAUSTED, 0);
    int remaining = (int) Math.max(0, HOURLY_QUOTA - used - 1);

    String text;
    try {
      text = aiGateway.complete(new AiRequest(
          actor.getUserId(), AiFeature.LEARNING_PLAN_DRAFT, SYSTEM_PROMPT, userPrompt(need, topics), 0.4)).text();
    } catch (RuntimeException ex) {
      log.warn("Không gợi ý được lộ trình bằng AI cho lớp {}: {}", classId, ex.getClass().getSimpleName());
      return PlanDraftView.unavailable(AI_UNAVAILABLE, remaining);
    }
    List<DraftMilestoneView> milestones = parse(text, topics);
    return milestones.isEmpty()
        ? PlanDraftView.unavailable(AI_INVALID, remaining)
        : new PlanDraftView(milestones, null, remaining);
  }

  static String userPrompt(LearningNeedView need, List<Topic> topics) {
    StringBuilder sb = new StringBuilder("Lập lộ trình học cho một học sinh.\n");
    sb.append("Môn học: ").append(need.subjectName()).append('\n');
    sb.append("Khối lớp: ").append(need.educationLevelName()).append('\n');
    if (need.goalTagName() != null) sb.append("Mục tiêu: ").append(need.goalTagName()).append('\n');
    if (need.trackName() != null) sb.append("Chương trình: ").append(need.trackName()).append('\n');
    if (need.textbookSeriesName() != null) sb.append("Bộ sách: ").append(need.textbookSeriesName()).append('\n');
    if (!topics.isEmpty()) {
      sb.append("Danh sách chủ đề chuẩn: ")
          .append(topics.stream().map(Topic::getName).collect(Collectors.joining("; ")))
          .append('\n');
    }
    return sb.toString();
  }

  /** @return danh sách rỗng nếu đầu ra không phải JSON đúng định dạng hoặc sai hình dạng lộ trình. */
  static List<DraftMilestoneView> parse(String text, List<Topic> topics) {
    JsonNode items;
    try {
      items = JSON.readTree(stripCodeFence(text)).path("milestones");
    } catch (JacksonException ex) {
      return List.of();
    }
    if (!items.isArray() || items.size() < RoadmapShapePolicy.MIN_MILESTONES
        || items.size() > RoadmapShapePolicy.MAX_MILESTONES) {
      return List.of();
    }
    Map<String, UUID> topicByName = topics.stream()
        .collect(Collectors.toMap(t -> key(t.getName()), Topic::getTopicId, (a, b) -> a));
    List<DraftMilestoneView> result = new ArrayList<>();
    for (JsonNode item : items) {
      String title = text(item.path("title"), MAX_TEXT);
      List<String> criteria = new ArrayList<>();
      for (JsonNode c : item.path("criteria")) {
        String content = text(c, MAX_TEXT);
        if (content != null && criteria.size() < Milestone.MAX_CRITERIA) criteria.add(content);
      }
      if (title == null || criteria.isEmpty()) return List.of();
      String topic = text(item.path("topic"), MAX_TEXT);
      result.add(new DraftMilestoneView(
          title,
          text(item.path("description"), MAX_DESCRIPTION),
          clamp(item.path("weight").asInt(1), 1, 5),
          clamp(item.path("suggestedSessions").asInt(RoadmapShapePolicy.MIN_SESSIONS),
              RoadmapShapePolicy.MIN_SESSIONS, RoadmapShapePolicy.MAX_SESSIONS),
          List.copyOf(criteria),
          topic != null ? topicByName.get(key(topic)) : null));
    }
    return List.copyOf(result);
  }

  private static String stripCodeFence(String text) {
    String trimmed = text.trim();
    if (!trimmed.startsWith("```")) return trimmed;
    int firstNewline = trimmed.indexOf('\n');
    int lastFence = trimmed.lastIndexOf("```");
    return firstNewline > 0 && lastFence > firstNewline ? trimmed.substring(firstNewline + 1, lastFence) : trimmed;
  }

  private static String text(JsonNode node, int maxLength) {
    if (node == null || !node.isString()) return null;
    String value = node.asString().trim();
    if (value.isEmpty()) return null;
    return value.length() > maxLength ? value.substring(0, maxLength).trim() : value;
  }

  private static int clamp(int value, int min, int max) {
    return Math.max(min, Math.min(max, value));
  }

  private static String key(String name) {
    return name.trim().toLowerCase(Locale.ROOT);
  }
}
