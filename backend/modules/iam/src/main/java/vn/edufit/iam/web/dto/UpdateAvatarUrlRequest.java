package vn.edufit.iam.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Payload yêu cầu cập nhật URL ảnh đại diện trực tiếp (cho preset avatar hoặc ảnh ngoài).
 */
public record UpdateAvatarUrlRequest(
    @NotBlank(message = "Đường dẫn ảnh đại diện không được để trống")
    @Size(max = 1000, message = "Đường dẫn ảnh đại diện không được vượt quá 1000 ký tự")
    String avatarUrl
) {
}
