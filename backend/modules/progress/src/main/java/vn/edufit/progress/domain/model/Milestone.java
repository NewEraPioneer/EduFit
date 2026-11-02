package vn.edufit.progress.domain.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import vn.edufit.shared.exception.ErrorCode;
import vn.edufit.shared.exception.InvalidOperationException;

/**
 * Entity miền đại diện cho Cột mốc học tập (Milestone).
 * Java thuần túy (POJO) - không phụ thuộc Spring hay JPA.
 *
 * <p>Vòng đời trạng thái:
 * <ul>
 *   <li>{@code NOT_STARTED → IN_PROGRESS}: tự động khi buổi học đầu tiên được gắn vào cột mốc.</li>
 *   <li>{@code → ACHIEVED}: chỉ gia sư chốt, và chỉ khi mọi tiêu chí đạt đã được tick.</li>
 *   <li>{@code ACHIEVED → IN_PROGRESS}: mở lại khi phát hiện học sinh chưa vững, bắt buộc có lý do.</li>
 * </ul>
 */
public class Milestone {

  public static final int MIN_CRITERIA = 1;
  public static final int MAX_CRITERIA = 4;
  public static final int MAX_REOPEN_REASON_LENGTH = 300;

  private final UUID milestoneId;
  private UUID planId;
  private String title;
  private String description;
  private LocalDate targetDate;
  private int weight;
  private MilestoneStatus status;
  private LocalDate achievedDate;
  private boolean archived;
  private int sortOrder;
  private final Instant createdAt;
  private final List<MilestoneCriterion> criteria;

  /** Bản nháp tiêu chí gửi lên khi lập/sửa lộ trình; {@code criterionId} null nghĩa là tiêu chí mới. */
  public record CriterionDraft(UUID criterionId, String content) {}

  /** Tỉ lệ hoàn thành của cột mốc dưới dạng phân số {@code completed/total}. */
  public record CompletionShare(int completed, int total) {}

  public Milestone(
      UUID milestoneId,
      UUID planId,
      String title,
      String description,
      LocalDate targetDate,
      int weight,
      MilestoneStatus status,
      LocalDate achievedDate,
      boolean archived,
      int sortOrder,
      Instant createdAt,
      List<MilestoneCriterion> criteria
  ) {
    validateTitle(title);
    validateWeight(weight);
    List<MilestoneCriterion> initialCriteria = criteria != null ? criteria : List.of();
    if (initialCriteria.size() > MAX_CRITERIA) {
      throw new InvalidOperationException(
          ErrorCode.VALIDATION_FAILED, "Mỗi cột mốc có tối đa " + MAX_CRITERIA + " tiêu chí đạt");
    }
    this.milestoneId = milestoneId != null ? milestoneId : UUID.randomUUID();
    this.planId = planId;
    this.title = title.trim();
    this.description = description;
    this.targetDate = targetDate;
    this.weight = weight;
    this.status = status != null ? status : MilestoneStatus.NOT_STARTED;
    this.achievedDate = achievedDate;
    this.archived = archived;
    this.sortOrder = sortOrder;
    this.createdAt = createdAt != null ? createdAt : Instant.now();
    this.criteria = new ArrayList<>(initialCriteria);
    this.criteria.sort(Comparator.comparingInt(MilestoneCriterion::getSortOrder));
  }

  public static Milestone createNew(
      UUID planId,
      String title,
      String description,
      LocalDate targetDate,
      int weight,
      int sortOrder,
      List<String> criteriaContents,
      LocalDate today,
      Instant now
  ) {
    if (targetDate != null && targetDate.isBefore(today)) {
      throw new InvalidOperationException(
          ErrorCode.VALIDATION_FAILED,
          "Hạn hoàn thành cột mốc không được nằm trong quá khứ"
      );
    }
    List<String> contents = criteriaContents != null ? criteriaContents : List.of();
    validateRequiredCriteriaCount(contents.size(), title);
    List<MilestoneCriterion> criteria = new ArrayList<>();
    for (int i = 0; i < contents.size(); i++) {
      criteria.add(MilestoneCriterion.createNew(contents.get(i), i, now));
    }
    return new Milestone(
        UUID.randomUUID(),
        planId,
        title,
        description,
        targetDate,
        weight,
        MilestoneStatus.NOT_STARTED,
        null,
        false,
        sortOrder,
        now,
        criteria
    );
  }

  /**
   * Cập nhật chi tiết và danh sách tiêu chí của cột mốc.
   *
   * <p>Cột mốc cũ chưa có tiêu chí vẫn hợp lệ khi không bị sửa; một khi được sửa thì bắt buộc có 1–4 tiêu chí.
   * Tiêu chí của cột mốc đã đạt bị khóa, cần mở lại cột mốc trước khi thay đổi.
   *
   * @return {@code true} nếu cột mốc có thay đổi.
   */
  public boolean updateDetails(
      String newTitle,
      String newDescription,
      LocalDate newTargetDate,
      int newWeight,
      int newSortOrder,
      List<CriterionDraft> criteriaDrafts,
      LocalDate today,
      Instant now
  ) {
    validateTitle(newTitle);
    validateWeight(newWeight);

    if (!Objects.equals(this.targetDate, newTargetDate) && newTargetDate != null && newTargetDate.isBefore(today)) {
      throw new InvalidOperationException(
          ErrorCode.VALIDATION_FAILED,
          "Hạn hoàn thành cột mốc cập nhật không được nằm trong quá khứ"
      );
    }

    List<CriterionDraft> drafts = criteriaDrafts != null ? criteriaDrafts : List.of();
    boolean detailsChanged = !this.title.equals(newTitle.trim())
        || !Objects.equals(this.description, newDescription)
        || !Objects.equals(this.targetDate, newTargetDate)
        || this.weight != newWeight;
    boolean criteriaChanged = !sameCriteria(drafts);

    if (criteriaChanged && this.status == MilestoneStatus.ACHIEVED) {
      throw new InvalidOperationException(
          ErrorCode.INVALID_OPERATION,
          "Không thể sửa tiêu chí của cột mốc đã đạt \"" + this.title + "\". Hãy mở lại cột mốc trước."
      );
    }
    if (detailsChanged || criteriaChanged) {
      validateRequiredCriteriaCount(drafts.size(), newTitle);
    }

    this.title = newTitle.trim();
    this.description = newDescription;
    this.targetDate = newTargetDate;
    this.weight = newWeight;
    this.sortOrder = newSortOrder;
    if (criteriaChanged) {
      replaceCriteria(drafts, now);
    }
    return detailsChanged || criteriaChanged;
  }

  /**
   * Đặt trạng thái tick cho toàn bộ tiêu chí: tiêu chí có trong {@code checkedCriterionIds} được tick, còn lại bỏ tick.
   *
   * @return {@code true} nếu có tiêu chí thay đổi.
   */
  public boolean applyCriteriaChecks(Collection<UUID> checkedCriterionIds, Instant now) {
    Set<UUID> checkedIds = checkedCriterionIds != null ? new HashSet<>(checkedCriterionIds) : Set.of();
    Set<UUID> ownIds = criteria.stream().map(MilestoneCriterion::getCriterionId).collect(Collectors.toSet());
    if (!ownIds.containsAll(checkedIds)) {
      throw new InvalidOperationException(
          ErrorCode.VALIDATION_FAILED, "Tiêu chí được tick không thuộc cột mốc \"" + this.title + "\"");
    }
    boolean changed = criteria.stream().anyMatch(c -> c.isChecked() != checkedIds.contains(c.getCriterionId()));
    if (!changed) return false;
    if (this.status == MilestoneStatus.ACHIEVED) {
      throw new InvalidOperationException(
          ErrorCode.INVALID_OPERATION,
          "Cột mốc \"" + this.title + "\" đã đạt, không thể bỏ tick tiêu chí. Hãy mở lại cột mốc trước."
      );
    }
    for (MilestoneCriterion c : criteria) {
      c.setChecked(checkedIds.contains(c.getCriterionId()), now);
    }
    return true;
  }

  /**
   * Chuyển {@code NOT_STARTED → IN_PROGRESS} khi buổi học đầu tiên được gắn vào cột mốc.
   *
   * @return {@code true} nếu trạng thái thay đổi.
   */
  public boolean startProgress() {
    if (this.status != MilestoneStatus.NOT_STARTED) return false;
    this.status = MilestoneStatus.IN_PROGRESS;
    return true;
  }

  /** Gia sư chốt cột mốc đã đạt. Bắt buộc mọi tiêu chí đã tick (cột mốc cũ chưa có tiêu chí được miễn). */
  public void achieve(LocalDate specifiedAchievedDate, LocalDate today) {
    if (this.status == MilestoneStatus.ACHIEVED) {
      throw new InvalidOperationException(ErrorCode.INVALID_OPERATION, "Cột mốc \"" + this.title + "\" đã đạt");
    }
    if (!allCriteriaChecked()) {
      throw new InvalidOperationException(
          ErrorCode.INVALID_OPERATION,
          "Cần tick đủ tiêu chí đạt trước khi chốt cột mốc \"" + this.title + "\""
      );
    }
    LocalDate finalAchievedDate = specifiedAchievedDate != null ? specifiedAchievedDate : today;
    if (finalAchievedDate.isAfter(today)) {
      throw new InvalidOperationException(ErrorCode.VALIDATION_FAILED, "Ngày đạt cột mốc không được nằm trong tương lai");
    }
    this.achievedDate = finalAchievedDate;
    this.status = MilestoneStatus.ACHIEVED;
  }

  /**
   * Mở lại cột mốc đã đạt khi phát hiện học sinh chưa vững ({@code ACHIEVED → IN_PROGRESS}).
   *
   * @return ngày đạt cũ, để ghi vào lịch sử.
   */
  public LocalDate reopen(String reason) {
    if (this.status != MilestoneStatus.ACHIEVED) {
      throw new InvalidOperationException(ErrorCode.INVALID_OPERATION, "Chỉ có thể mở lại cột mốc đã đạt");
    }
    if (reason == null || reason.isBlank()) {
      throw new InvalidOperationException(ErrorCode.VALIDATION_FAILED, "Cần ghi lý do khi mở lại cột mốc đã đạt");
    }
    if (reason.trim().length() > MAX_REOPEN_REASON_LENGTH) {
      throw new InvalidOperationException(
          ErrorCode.VALIDATION_FAILED, "Lý do mở lại tối đa " + MAX_REOPEN_REASON_LENGTH + " ký tự");
    }
    LocalDate previous = this.achievedDate;
    this.status = MilestoneStatus.IN_PROGRESS;
    this.achievedDate = null;
    return previous;
  }

  public void archive() {
    this.archived = true;
  }

  /**
   * Kiểm tra xem milestone này đã từng có tiến triển chưa (IN_PROGRESS hoặc ACHIEVED).
   * Dùng để quyết định: xóa thật nếu chưa từng tiến triển, chuyển isArchived=true nếu đã có tiến triển.
   */
  public boolean hasProgressed() {
    return this.status != MilestoneStatus.NOT_STARTED
        || this.achievedDate != null
        || criteria.stream().anyMatch(MilestoneCriterion::isChecked);
  }

  public boolean allCriteriaChecked() {
    return criteria.stream().allMatch(MilestoneCriterion::isChecked);
  }

  /** Tỉ lệ hoàn thành hiện tại: ACHIEVED = 1/1; đang học = số tiêu chí đã tick / tổng tiêu chí; chưa có tiêu chí = 0. */
  public CompletionShare completionShare() {
    if (this.status == MilestoneStatus.ACHIEVED) return new CompletionShare(1, 1);
    if (criteria.isEmpty()) return new CompletionShare(0, 1);
    int checked = (int) criteria.stream().filter(MilestoneCriterion::isChecked).count();
    return new CompletionShare(checked, criteria.size());
  }

  /** Tỉ lệ hoàn thành dựng lại tại thời điểm {@code cutoff} từ {@code achievedDate} và thời điểm tick tiêu chí. */
  public CompletionShare completionShareAt(Instant cutoff, LocalDate cutoffDate) {
    if (this.status == MilestoneStatus.ACHIEVED && this.achievedDate != null && !this.achievedDate.isAfter(cutoffDate)) {
      return new CompletionShare(1, 1);
    }
    if (criteria.isEmpty()) return new CompletionShare(0, 1);
    int checked = (int) criteria.stream()
        .filter(c -> c.isChecked() && c.getCheckedAt() != null && !c.getCheckedAt().isAfter(cutoff))
        .count();
    return new CompletionShare(checked, criteria.size());
  }

  private boolean sameCriteria(List<CriterionDraft> drafts) {
    if (drafts.size() != criteria.size()) return false;
    for (int i = 0; i < drafts.size(); i++) {
      CriterionDraft draft = drafts.get(i);
      MilestoneCriterion current = criteria.get(i);
      if (draft.criterionId() == null
          || !draft.criterionId().equals(current.getCriterionId())
          || draft.content() == null
          || !draft.content().trim().equals(current.getContent())) {
        return false;
      }
    }
    return true;
  }

  private void replaceCriteria(List<CriterionDraft> drafts, Instant now) {
    Map<UUID, MilestoneCriterion> existing = criteria.stream()
        .collect(Collectors.toMap(MilestoneCriterion::getCriterionId, Function.identity()));
    List<MilestoneCriterion> next = new ArrayList<>();
    for (int i = 0; i < drafts.size(); i++) {
      CriterionDraft draft = drafts.get(i);
      MilestoneCriterion kept = draft.criterionId() != null ? existing.get(draft.criterionId()) : null;
      if (kept == null) {
        next.add(MilestoneCriterion.createNew(draft.content(), i, now));
      } else {
        boolean contentChanged = draft.content() == null || !draft.content().trim().equals(kept.getContent());
        kept.rename(draft.content(), i);
        if (contentChanged) {
          kept.setChecked(false, now); // Nội dung tiêu chí đổi thì cần đánh giá lại
        }
        next.add(kept);
      }
    }
    criteria.clear();
    criteria.addAll(next);
  }

  private static void validateRequiredCriteriaCount(int count, String title) {
    if (count < MIN_CRITERIA || count > MAX_CRITERIA) {
      throw new InvalidOperationException(
          ErrorCode.VALIDATION_FAILED,
          "Cột mốc \"" + (title != null ? title.trim() : "") + "\" cần từ "
              + MIN_CRITERIA + " đến " + MAX_CRITERIA + " tiêu chí đạt"
      );
    }
  }

  private static void validateTitle(String title) {
    if (title == null || title.isBlank()) {
      throw new InvalidOperationException(ErrorCode.VALIDATION_FAILED, "Tiêu đề cột mốc không được để trống");
    }
    if (title.trim().length() > 200) {
      throw new InvalidOperationException(ErrorCode.VALIDATION_FAILED, "Tiêu đề cột mốc tối đa 200 ký tự");
    }
  }

  private static void validateWeight(int weight) {
    if (weight < 1 || weight > 5) {
      throw new InvalidOperationException(ErrorCode.VALIDATION_FAILED, "Trọng số cột mốc phải từ 1 đến 5");
    }
  }

  public UUID getMilestoneId() { return milestoneId; }
  public UUID getPlanId() { return planId; }
  public void setPlanId(UUID planId) { this.planId = planId; }
  public String getTitle() { return title; }
  public String getDescription() { return description; }
  public LocalDate getTargetDate() { return targetDate; }
  public int getWeight() { return weight; }
  public MilestoneStatus getStatus() { return status; }
  public LocalDate getAchievedDate() { return achievedDate; }
  public boolean isArchived() { return archived; }
  public int getSortOrder() { return sortOrder; }
  public Instant getCreatedAt() { return createdAt; }
  public List<MilestoneCriterion> getCriteria() { return Collections.unmodifiableList(criteria); }
}
