package vn.edufit.progress.domain.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import vn.edufit.progress.domain.model.PlanTemplate;

/** Cổng kho dữ liệu cho mẫu lộ trình. */
public interface PlanTemplateDomainRepository {

  /** Lưu mẫu; sai phiên bản (sửa đồng thời) ném lỗi xung đột. */
  PlanTemplate save(PlanTemplate template);

  Optional<PlanTemplate> findById(UUID templateId);

  /** Mẫu hệ thống và mẫu riêng của gia sư ({@code ownerTutorId} null = chỉ mẫu hệ thống). */
  List<PlanTemplate> findVisibleTo(UUID ownerTutorId);

  void delete(UUID templateId);
}
