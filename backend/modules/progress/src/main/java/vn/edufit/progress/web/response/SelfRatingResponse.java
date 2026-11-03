package vn.edufit.progress.web.response;

import java.util.UUID;

/** Cảm nhận 1 chạm đã lưu của học sinh cho một buổi học (1 = còn khó, 2 = tạm ổn, 3 = hiểu bài). */
public record SelfRatingResponse(UUID sessionId, int rating) {}
