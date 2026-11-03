package vn.edufit.progress.web.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import vn.edufit.progress.domain.model.PeriodReport;

/** Câu tổng kết tùy chọn của gia sư cho báo cáo kỳ; chuỗi rỗng là bỏ câu tổng kết. */
public record PeriodReportSummaryRequest(
    @Size(max = PeriodReport.MAX_SUMMARY_LENGTH, message = "Câu tổng kết tối đa 300 ký tự")
    String summary,

    @NotNull(message = "version không được để trống")
    Integer version
) {}
