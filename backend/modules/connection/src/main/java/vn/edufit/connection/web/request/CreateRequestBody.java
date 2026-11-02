package vn.edufit.connection.web.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record CreateRequestBody(@NotNull UUID needId, @NotNull UUID tutorId,
    @NotEmpty List<@NotBlank @Size(max=100) String> desiredSlots, @Size(max=500) String message) {}
