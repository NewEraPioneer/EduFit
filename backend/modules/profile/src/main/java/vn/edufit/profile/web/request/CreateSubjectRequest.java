package vn.edufit.profile.web.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Payload yêu cầu tạo mới môn học từ Quản trị viên (Admin).
 */
public record CreateSubjectRequest(
    @NotBlank(message = "Tên môn học không được để trống")
    @Size(max = 100, message = "Tên môn học tối đa 100 ký tự")
    String name,

    String category,
    String code,
    String slug,
    String description
) {}
