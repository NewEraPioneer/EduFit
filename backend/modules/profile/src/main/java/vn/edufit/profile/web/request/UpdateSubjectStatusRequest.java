package vn.edufit.profile.web.request;

/**
 * Payload yêu cầu cập nhật trạng thái hoạt động của môn học.
 */
public record UpdateSubjectStatusRequest(
    Boolean isActive
) {}
