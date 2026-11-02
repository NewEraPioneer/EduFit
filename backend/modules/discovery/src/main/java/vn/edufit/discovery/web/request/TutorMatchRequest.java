package vn.edufit.discovery.web.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record TutorMatchRequest(
    @NotNull(message = "needId không được để trống")
    UUID needId,

    @Min(value = 1, message = "topN phải từ 1 đến 10")
    @Max(value = 10, message = "topN phải từ 1 đến 10")
    Integer topN
) {

  public int resolvedTopN() {
    return topN == null ? 5 : topN;
  }
}
