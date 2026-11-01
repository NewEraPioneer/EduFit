package vn.edufit.profile.infra.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalTime;
import java.util.UUID;

/** Khung giờ học mong muốn hằng tuần của một nhu cầu học. */
@Entity
@Table(name = "learning_need_slot")
public class LearningNeedSlot {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "slot_id", updatable = false, nullable = false)
  private UUID slotId;

  @Column(name = "need_id", nullable = false)
  private UUID needId;

  @Column(name = "day_of_week", nullable = false)
  private Short dayOfWeek;

  @Column(name = "start_time", nullable = false)
  private LocalTime startTime;

  @Column(name = "end_time", nullable = false)
  private LocalTime endTime;

  protected LearningNeedSlot() {
    // JPA required
  }

  public LearningNeedSlot(UUID needId, Short dayOfWeek, LocalTime startTime, LocalTime endTime) {
    this.needId = needId;
    this.dayOfWeek = dayOfWeek;
    this.startTime = startTime;
    this.endTime = endTime;
  }

  public UUID getNeedId() {
    return needId;
  }

  public Short getDayOfWeek() {
    return dayOfWeek;
  }

  public LocalTime getStartTime() {
    return startTime;
  }

  public LocalTime getEndTime() {
    return endTime;
  }
}
