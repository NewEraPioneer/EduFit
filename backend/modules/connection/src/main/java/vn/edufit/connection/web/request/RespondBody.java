package vn.edufit.connection.web.request;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record RespondBody(@NotNull UUID invitationId, @NotNull Boolean accept) {}
