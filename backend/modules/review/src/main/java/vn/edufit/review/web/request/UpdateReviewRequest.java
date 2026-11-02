package vn.edufit.review.web.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request payload chỉnh sửa đánh giá của chính mình (BR-57, BR-58).
 */
public record UpdateReviewRequest(
    @NotNull(message = "Điểm đánh giá không được để trống")
    @Min(value = 1, message = "Điểm đánh giá tối thiểu là 1 sao")
    @Max(value = 5, message = "Điểm đánh giá tối đa là 5 sao")
    Integer rating,

    @NotBlank(message = "Nội dung nhận xét không được để trống")
    @Size(min = 10, max = 1000, message = "Nội dung nhận xét phải từ 10 đến 1000 ký tự")
    String comment
) {}

