package vn.edufit.scheduling.web.request;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;
import vn.edufit.scheduling.domain.model.SessionMode;

/**
 * Request DTO gửi lên khi tạo đề xuất buổi học mới (UC4.1).
 */
public record ProposeSessionRequest(
    @NotNull(message = "classId không được để trống")
    UUID classId,

    UUID tutorId,

    UUID studentId,

    @NotNull(message = "startAt không được để trống")
    @Future(message = "startAt phải ở tương lai")
    Instant startAt,

    @NotNull(message = "endAt không được để trống")
    @Future(message = "endAt phải ở tương lai")
    Instant endAt,

    @NotNull(message = "mode không được để trống")
    SessionMode mode,

    @Size(max = 255, message = "placeOrLink không được vượt quá 255 ký tự")
    String placeOrLink,

    @Size(max = 255, message = "repeatNote không được vượt quá 255 ký tự")
    String repeatNote,

    @Size(max = 500, message = "message không được vượt quá 500 ký tự")
    String message
) {}
