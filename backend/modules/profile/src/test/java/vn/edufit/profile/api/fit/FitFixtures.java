package vn.edufit.profile.api.fit;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import vn.edufit.profile.api.dto.CatalogRef;
import vn.edufit.profile.api.dto.LearningNeedView;
import vn.edufit.profile.api.dto.OfferingGoalDto;
import vn.edufit.profile.api.dto.TutorDiscoveryProfileDto;
import vn.edufit.profile.api.dto.TutorOfferingDto;
import vn.edufit.profile.api.dto.TutorSummaryDto;
import vn.edufit.profile.api.dto.WeeklyAvailabilityDto;

/** Dữ liệu mẫu cho test so khớp nhu cầu học với gia sư. */
public final class FitFixtures {

  public static final int MATH = 1;
  public static final int SCIENCE = 7;
  public static final int SCI_PHYSICS = 2;
  public static final int SCI_CHEMISTRY = 3;
  public static final int LEVEL_8 = 3;
  public static final int LEVEL_9 = 4;
  public static final int FOUNDATION = 1;
  public static final int GIFTED = 3;
  public static final int GRADE10_EXAM = 4;
  public static final int KNTT = 1;
  public static final int CTST = 2;
  public static final int HA_NOI = 1;
  public static final int DA_NANG = 4;
  public static final int CAU_GIAY = 5;
  public static final int DONG_DA = 6;

  private FitFixtures() {}

  public static WeeklyAvailabilityDto slot(int day, int fromHour, int toHour) {
    return new WeeklyAvailabilityDto((short) day, LocalTime.of(fromHour, 0), LocalTime.of(toHour, 0));
  }

  /** Builder nhu cầu học: mặc định Toán lớp 9, Lấy lại gốc + bộ sách KNTT, học trực tiếp Cầu Giấy, 300k, tối thứ 2. */
  public static final class Need {
    private UUID needId = UUID.randomUUID();
    private UUID studentId = UUID.randomUUID();
    private UUID studentUserId = UUID.randomUUID();
    private int subjectId = MATH;
    private Integer trackId;
    private int levelId = LEVEL_9;
    private int goalTagId = FOUNDATION;
    private String goalName = "Lấy lại gốc & Bám sát SGK";
    private Integer textbookId = KNTT;
    private Integer examProvinceId;
    private String mode = "OFFLINE";
    private Integer provinceId = HA_NOI;
    private Integer districtId = CAU_GIAY;
    private Long budget = 300000L;
    private List<WeeklyAvailabilityDto> slots = List.of(slot(2, 19, 21));
    private String status = "ACTIVE";

    public Need id(UUID value) { needId = value; return this; }
    public Need student(UUID id, UUID userId) { studentId = id; studentUserId = userId; return this; }
    public Need subject(int value, Integer track) { subjectId = value; trackId = track; return this; }
    public Need level(int value) { levelId = value; return this; }
    public Need goal(int value, String name) { goalTagId = value; goalName = name; return this; }
    public Need textbook(Integer value) { textbookId = value; return this; }
    public Need examProvince(Integer value) { examProvinceId = value; return this; }
    public Need mode(String value) { mode = value; return this; }
    public Need location(Integer province, Integer district) { provinceId = province; districtId = district; return this; }
    public Need budget(Long value) { budget = value; return this; }
    public Need slots(List<WeeklyAvailabilityDto> value) { slots = value; return this; }
    public Need status(String value) { status = value; return this; }

    public LearningNeedView build() {
      return new LearningNeedView(needId, studentId, studentUserId, studentUserId,
          levelId, "Lớp " + (levelId + 5), subjectId, subjectId == MATH ? "Toán" : "Khoa học tự nhiên",
          trackId, trackId == null ? null : "Nhánh " + trackId,
          goalTagId, "TAG_" + goalTagId, goalName, goalTagId == GIFTED,
          textbookId, textbookId == null ? null : "Bộ sách " + textbookId,
          examProvinceId, examProvinceId == null ? null : provinceName(examProvinceId),
          null, null,
          mode, provinceId, provinceId == null ? null : provinceName(provinceId),
          districtId, districtId == null ? null : districtName(districtId),
          budget, status, slots, "ACTIVE".equals(status) ? List.of() : List.of("mode"),
          Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T00:00:00Z"));
    }
  }

  public static Need need() {
    return new Need();
  }

  /** Builder gói dạy: mặc định Toán lớp 8–9, Lấy lại gốc, bộ sách KNTT, 250k. */
  public static final class Offering {
    private int subjectId = MATH;
    private Integer trackId;
    private long price = 250000L;
    private List<Integer> levels = List.of(LEVEL_8, LEVEL_9);
    private List<OfferingGoalDto> goals = List.of(goal(FOUNDATION, false, false));
    private List<Integer> textbooks = List.of(KNTT);
    private List<Integer> examProvinces = List.of();

    public Offering subject(int value, Integer track) { subjectId = value; trackId = track; return this; }
    public Offering price(long value) { price = value; return this; }
    public Offering levels(Integer... value) { levels = List.of(value); return this; }
    public Offering goals(OfferingGoalDto... value) { goals = List.of(value); return this; }
    public Offering textbooks(Integer... value) { textbooks = List.of(value); return this; }
    public Offering examProvinces(Integer... value) { examProvinces = List.of(value); return this; }

    public TutorOfferingDto build() {
      return new TutorOfferingDto(UUID.randomUUID(), subjectId, subjectId == MATH ? "Toán" : "Khoa học tự nhiên",
          trackId, trackId == null ? null : "Nhánh " + trackId, price,
          levels.stream().map(id -> new CatalogRef(id, "Lớp " + (id + 5))).toList(),
          goals,
          textbooks.stream().map(id -> new CatalogRef(id, "Bộ sách " + id)).toList(),
          examProvinces.stream().map(id -> new CatalogRef(id, provinceName(id))).toList());
    }
  }

  public static Offering offering() {
    return new Offering();
  }

  public static OfferingGoalDto goal(int tagId, boolean premium, boolean verified) {
    return new OfferingGoalDto(tagId, "TAG_" + tagId, "Mục tiêu " + tagId, premium, verified);
  }

  public static TutorSummaryDto summary(UUID tutorId, String mode, Integer provinceId, BigDecimal rating, int reviews) {
    return new TutorSummaryDto(tutorId, UUID.randomUUID(), "Gia sư " + tutorId.toString().substring(0, 4), null, null,
        mode, null, false, provinceId, provinceId == null ? null : provinceName(provinceId), (short) 3, null,
        "VERIFIED", Instant.parse("2026-01-01T00:00:00Z"), rating, reviews);
  }

  public static TutorDiscoveryProfileDto tutor(
      String mode, Integer provinceId, List<Integer> districts, List<WeeklyAvailabilityDto> slots,
      TutorOfferingDto... offerings) {
    return tutor(UUID.randomUUID(), mode, provinceId, districts, slots, null, 0, offerings);
  }

  public static TutorDiscoveryProfileDto tutor(
      UUID tutorId, String mode, Integer provinceId, List<Integer> districts, List<WeeklyAvailabilityDto> slots,
      BigDecimal rating, int reviews, TutorOfferingDto... offerings) {
    return new TutorDiscoveryProfileDto(summary(tutorId, mode, provinceId, rating, reviews), List.of(offerings),
        districts.stream().map(id -> new CatalogRef(id, districtName(id))).toList(), slots,
        Instant.parse("2025-01-01T00:00:00Z"));
  }

  private static String provinceName(int id) {
    return id == HA_NOI ? "Hà Nội" : id == DA_NANG ? "Đà Nẵng" : "Tỉnh " + id;
  }

  private static String districtName(int id) {
    return id == CAU_GIAY ? "Cầu Giấy" : id == DONG_DA ? "Đống Đa" : "Khu vực " + id;
  }
}
