package vn.edufit.connection.web.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record InviteBody(@NotBlank @Email String targetEmail) {}
