package vn.edufit.profile.web.request;

/**
 * Hồ sơ học sinh chỉ giữ thông tin mặc định để điền sẵn khi tạo nhu cầu học.
 */
public record UpdateStudentProfileRequest(
    Integer educationLevelId,
    Integer provinceId,
    Integer districtId
) {}
