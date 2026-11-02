package vn.edufit.connection.web.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RequestRespondBody(@NotNull Boolean accept, @Size(max = 300) String reason) {}
