package vn.edufit.connection.web.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EndClassBody(
    @NotBlank(message = "Lý do kết thúc lớp học không được để trống")
    @Size(max = 300, message = "Lý do kết thúc lớp học không được vượt quá 300 ký tự")
    String reason
) {}
