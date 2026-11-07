package vn.edufit.progress.web;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.edufit.progress.application.dto.TopicViews.TopicView;
import vn.edufit.progress.application.service.TopicCatalogService;
import vn.edufit.progress.web.request.SaveTopicRequest;
import vn.edufit.shared.auth.CurrentUser;
import vn.edufit.shared.response.ApiResponse;

/** REST Controller cho danh mục chủ đề chuẩn (/api/v1/progress/topics). */
@RestController
@RequestMapping("/api/v1/progress/topics")
@RequiredArgsConstructor
public class TopicCatalogController {

  private final TopicCatalogService topicService;
  private final ObjectProvider<CurrentUser> currentUserProvider;

  /** Có {@code classId}: chủ đề đang dùng được cho lớp (gia sư lập lộ trình). Không có: toàn bộ danh mục (admin). */
  @GetMapping
  public ResponseEntity<ApiResponse<List<TopicView>>> list(
      @RequestParam(value = "classId", required = false) UUID classId
  ) {
    CurrentUser actor = ProgressWebSupport.currentUser(currentUserProvider);
    List<TopicView> topics = classId != null ? topicService.listForClass(actor, classId) : topicService.listAll(actor);
    return ResponseEntity.ok(ApiResponse.success(topics, "Lấy danh mục chủ đề thành công"));
  }

  @PostMapping
  public ResponseEntity<ApiResponse<TopicView>> create(@Valid @RequestBody SaveTopicRequest req) {
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
        topicService.create(ProgressWebSupport.currentUser(currentUserProvider), req.toCommand()), "Đã thêm chủ đề"));
  }

  @PutMapping("/{topicId}")
  public ResponseEntity<ApiResponse<TopicView>> update(
      @PathVariable("topicId") UUID topicId,
      @Valid @RequestBody SaveTopicRequest req
  ) {
    return ResponseEntity.ok(ApiResponse.success(
        topicService.update(ProgressWebSupport.currentUser(currentUserProvider), topicId, req.toCommand()),
        "Đã cập nhật chủ đề"));
  }
}
