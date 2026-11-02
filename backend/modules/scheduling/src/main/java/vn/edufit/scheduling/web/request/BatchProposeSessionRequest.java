package vn.edufit.scheduling.web.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import vn.edufit.scheduling.domain.model.SessionMode;

/**
 * Request DTO cho API đề xuất hàng loạt buổi học (Batch Propose Sessions).
 */
public record BatchProposeSessionRequest(
    @NotNull(message = "classId không được để trống")
    UUID classId,
    UUID tutorId,
    UUID studentId,
    @NotEmpty(message = "Danh sách buổi học không được để trống")
    List<@Valid BatchProposeItemRequest> items
) {
  public record BatchProposeItemRequest(
      @NotNull(message = "startAt không được để trống")
      Instant startAt,
      @NotNull(message = "endAt không được để trống")
      Instant endAt,
      @NotNull(message = "mode không được để trống")
      SessionMode mode,
      String placeOrLink,
      String repeatNote,
      String message
  ) {}
}
