package vn.edufit.discovery.web;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.edufit.discovery.application.service.DiscoveryQueryService;
import vn.edufit.discovery.application.service.TutorMatchingService;
import vn.edufit.discovery.web.request.TutorMatchRequest;
import vn.edufit.discovery.web.request.TutorSearchRequest;
import vn.edufit.discovery.web.response.TutorDetailResponse;
import vn.edufit.discovery.web.response.TutorDiscoveryCardResponse;
import vn.edufit.discovery.web.response.TutorMatchResponse;
import vn.edufit.shared.auth.CurrentUser;
import vn.edufit.shared.exception.ForbiddenOperationException;
import vn.edufit.shared.exception.InvalidOperationException;
import vn.edufit.shared.response.ApiResponse;

@RestController
@RequestMapping("/api/v1/discovery")
public class DiscoveryController {

  private static final int DEFAULT_PAGE = 0;
  private static final int DEFAULT_SIZE = 10;
  private static final int MAX_SIZE = 100;

  private final DiscoveryQueryService discoveryQueryService;
  private final TutorMatchingService tutorMatchingService;
  private final ObjectProvider<CurrentUser> currentUserProvider;

  public DiscoveryController(
      DiscoveryQueryService discoveryQueryService,
      TutorMatchingService tutorMatchingService,
      ObjectProvider<CurrentUser> currentUserProvider
  ) {
    this.discoveryQueryService = discoveryQueryService;
    this.tutorMatchingService = tutorMatchingService;
    this.currentUserProvider = currentUserProvider;
  }

  @GetMapping("/tutors")
  public ResponseEntity<ApiResponse<Page<TutorDiscoveryCardResponse>>> searchTutors(
      @Valid TutorSearchRequest request,
      @RequestParam(defaultValue = "" + DEFAULT_PAGE) int page,
      @RequestParam(defaultValue = "" + DEFAULT_SIZE) int size,
      @RequestParam(defaultValue = "relevance") String sortBy,
      @RequestParam(defaultValue = "desc") String sortDirection
  ) {
    boolean ascending = switch (sortDirection.toLowerCase(Locale.ROOT)) {
      case "asc" -> true;
      case "desc" -> false;
      default -> throw new InvalidOperationException("sortDirection phải là asc hoặc desc");
    };
    Page<TutorDiscoveryCardResponse> response = discoveryQueryService
        .searchTutors(request.toCriteria(), request.needId(), currentUserProvider.getIfAvailable(),
            DiscoveryQueryService.parseSort(sortBy), ascending,
            Math.max(page, 0), Math.min(Math.max(size, 1), MAX_SIZE))
        .map(TutorDiscoveryCardResponse::from);
    return ResponseEntity.ok(ApiResponse.success(response, "Tìm kiếm gia sư thành công"));
  }

  @GetMapping("/tutors/{tutorId}")
  public ResponseEntity<ApiResponse<TutorDetailResponse>> getTutorDetail(@PathVariable UUID tutorId) {
    TutorDetailResponse response = TutorDetailResponse.from(discoveryQueryService.getTutorDetail(tutorId));
    return ResponseEntity.ok(ApiResponse.success(response, "Lấy thông tin gia sư thành công"));
  }

  /** Độ khớp một gia sư với nhu cầu học (✔/⚠ từng tiêu chí), dùng trước khi gửi yêu cầu kết nối. */
  @GetMapping("/needs/{needId}/tutors/{tutorId}/fit")
  public ResponseEntity<ApiResponse<TutorDiscoveryCardResponse>> fit(
      @PathVariable UUID needId, @PathVariable UUID tutorId) {
    TutorDiscoveryCardResponse response = TutorDiscoveryCardResponse.from(
        discoveryQueryService.fit(currentUser(), needId, tutorId));
    return ResponseEntity.ok(ApiResponse.success(response, "Đánh giá độ khớp thành công"));
  }

  @PostMapping("/match")
  public ResponseEntity<ApiResponse<List<TutorMatchResponse>>> matchTutors(
      @Valid @RequestBody TutorMatchRequest request
  ) {
    List<TutorMatchResponse> response = tutorMatchingService
        .match(currentUser(), request.needId(), request.resolvedTopN())
        .stream().map(TutorMatchResponse::from).toList();
    return ResponseEntity.ok(ApiResponse.success(response, "Ghép đôi gia sư thành công"));
  }

  private CurrentUser currentUser() {
    CurrentUser currentUser = currentUserProvider.getIfAvailable();
    if (currentUser == null || currentUser.getUserId() == null) {
      throw new ForbiddenOperationException("Yêu cầu cần được xác thực hoặc phiên đăng nhập đã hết hạn.");
    }
    return currentUser;
  }
}
