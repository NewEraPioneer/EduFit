package vn.edufit.profile.web;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.edufit.profile.api.dto.LearningNeedView;
import vn.edufit.profile.application.service.LearningNeedService;
import vn.edufit.profile.web.request.SaveLearningNeedRequest;
import vn.edufit.shared.auth.CurrentUser;
import vn.edufit.shared.response.ApiResponse;

/** Nhu cầu học của học sinh; phụ huynh truyền {@code studentId} của con đã liên kết. */
@RestController
@RequestMapping("/api/v1/learning-needs")
public class LearningNeedController {

  private final LearningNeedService needs;
  private final ObjectProvider<CurrentUser> actor;

  public LearningNeedController(LearningNeedService needs, ObjectProvider<CurrentUser> actor) {
    this.needs = needs;
    this.actor = actor;
  }

  @GetMapping
  public ResponseEntity<ApiResponse<List<LearningNeedView>>> list(
      @RequestParam(required = false) UUID studentId) {
    return ResponseEntity.ok(ApiResponse.success(
        needs.list(actor.getIfAvailable(), studentId), "Danh sách nhu cầu học"));
  }

  @PostMapping
  public ResponseEntity<ApiResponse<LearningNeedView>> create(@Valid @RequestBody SaveLearningNeedRequest input) {
    LearningNeedView created = needs.create(actor.getIfAvailable(), input);
    return ResponseEntity.created(URI.create("/api/v1/learning-needs/" + created.needId()))
        .body(ApiResponse.success(created, "Tạo nhu cầu học thành công"));
  }

  @PutMapping("/{needId}")
  public ResponseEntity<ApiResponse<LearningNeedView>> update(
      @PathVariable UUID needId, @Valid @RequestBody SaveLearningNeedRequest input) {
    return ResponseEntity.ok(ApiResponse.success(
        needs.update(actor.getIfAvailable(), needId, input), "Cập nhật nhu cầu học thành công"));
  }

  @PostMapping("/{needId}/archive")
  public ResponseEntity<ApiResponse<LearningNeedView>> archive(@PathVariable UUID needId) {
    return ResponseEntity.ok(ApiResponse.success(
        needs.archive(actor.getIfAvailable(), needId), "Lưu trữ nhu cầu học thành công"));
  }
}
