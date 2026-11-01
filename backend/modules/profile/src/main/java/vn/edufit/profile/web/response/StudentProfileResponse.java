package vn.edufit.profile.web.response;

import java.time.Instant;
import java.util.UUID;

public record StudentProfileResponse(
    UUID studentId,
    UUID userId,
    Integer educationLevelId,
    String educationLevelName,
    Integer provinceId,
    String provinceName,
    Integer districtId,
    String districtName,
    Boolean profileComplete,
    Instant createdAt,
    Instant updatedAt
) {}
