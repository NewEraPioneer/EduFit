package vn.edufit.verification.web.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import vn.edufit.verification.infra.persistence.entity.Credential;

public record SubmitVerificationRequest(
    @NotEmpty(message = "Danh sách bằng cấp không được để trống")
    @Valid
    List<CredentialItem> credentials
) {

  public record CredentialItem(
      @NotNull(message = "Loại bằng cấp là bắt buộc")
      @JsonAlias({"type", "documentType"})
      Credential.Type type,

      @NotNull(message = "Tên đơn vị cấp bằng là bắt buộc")
      @Size(min = 1, max = 200, message = "Tên đơn vị cấp bằng phải từ 1 đến 200 ký tự")
      @JsonAlias({"institution", "institutionName"})
      String institution,

      @JsonAlias({"year", "graduationYear"})
      Short year,

      @Size(max = 500, message = "Ghi chú tối đa 500 ký tự")
      @JsonAlias({"note", "notes"})
      String note,

      @NotEmpty(message = "Mỗi bằng cấp cần ít nhất một file đính kèm")
      List<Integer> fileIndexes,

      /** Gói dạy được chứng minh bởi giấy tờ này (tùy chọn, cho mục tiêu premium). */
      UUID offeringId
  ) {
  }
}
