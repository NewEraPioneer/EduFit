package vn.edufit.profile.api.port;

import java.util.UUID;

/**
 * Cổng kiểm tra liên kết phụ huynh – học sinh đã xác nhận. Profile không phụ thuộc module Connection;
 * module Connection cung cấp hiện thực của cổng này.
 */
public interface ParentStudentLinkPort {

  boolean hasConfirmedLink(UUID parentUserId, UUID studentId);
}
