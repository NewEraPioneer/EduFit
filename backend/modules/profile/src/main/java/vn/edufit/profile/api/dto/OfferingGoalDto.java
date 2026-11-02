package vn.edufit.profile.api.dto;

/**
 * Mục tiêu trong gói dạy. Với mục tiêu premium, {@code evidenceVerified} cho biết admin đã duyệt
 * bằng chứng chuyên môn; chưa duyệt thì vẫn được ghép nhưng hiển thị cảnh báo.
 */
public record OfferingGoalDto(
    Integer goalTagId,
    String code,
    String name,
    boolean premium,
    boolean evidenceVerified) {}
