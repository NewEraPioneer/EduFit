package vn.edufit.progress.domain.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import vn.edufit.progress.domain.model.Topic;

/** Cổng kho dữ liệu cho danh mục chủ đề chuẩn. */
public interface TopicDomainRepository {

  /** Lưu chủ đề; sai phiên bản (sửa đồng thời) hoặc trùng tên trong cùng môn × khối ném lỗi xung đột. */
  Topic save(Topic topic);

  Optional<Topic> findById(UUID topicId);

  List<Topic> findByIdIn(Collection<UUID> topicIds);

  /** Toàn bộ chủ đề (kể cả đã lưu trữ), sắp theo môn, khối, thứ tự. */
  List<Topic> findAll();

  /** Chủ đề đang dùng được của một môn × khối, sắp theo thứ tự. */
  List<Topic> findActiveBySubjectAndLevel(int subjectId, int educationLevelId);
}
