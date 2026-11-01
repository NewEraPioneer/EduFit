package vn.edufit.profile.api.dto;

import java.util.UUID;

/**
 * Public DTO chứa tóm tắt thông tin hồ sơ học sinh chia sẻ giữa các module (ADR-002).
 */
public record StudentSummaryDto(
    UUID studentId,
    UUID userId,
    Integer educationLevelId,
    Integer provinceId,
    Integer districtId,
    Boolean profileComplete
) {}
