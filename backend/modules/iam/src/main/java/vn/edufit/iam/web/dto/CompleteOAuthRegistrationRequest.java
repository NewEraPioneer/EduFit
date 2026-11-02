package vn.edufit.iam.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import vn.edufit.iam.api.AccountRole;

/**
 * Payload hoàn tất đăng ký tài khoản OAuth2 sau khi chọn vai trò (STUDENT, PARENT, TUTOR).
 */
public record CompleteOAuthRegistrationRequest(
    @NotBlank(message = "onboardingToken không được để trống")
    String onboardingToken,

    @NotNull(message = "Vai trò (role) không được để trống")
    AccountRole role
) {}
