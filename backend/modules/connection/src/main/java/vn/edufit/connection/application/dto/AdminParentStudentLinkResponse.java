package vn.edufit.connection.application.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO đại diện cho thông tin liên kết chi tiết giữa Phụ huynh và Học sinh cho Quản trị viên theo dõi.
 */
public record AdminParentStudentLinkResponse(
    UUID linkId,
    UUID parentUserId,
    String parentFullName,
    String parentEmail,
    UUID studentId,
    UUID studentUserId,
    String studentFullName,
    String studentEmail,
    String status,
    Instant confirmedAt,
    Instant revokedAt,
    String revokeReason
) {}
