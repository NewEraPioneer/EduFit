package vn.edufit.iam.infra.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import vn.edufit.shared.exception.EntityNotFoundException;
import vn.edufit.shared.exception.ErrorCode;
import vn.edufit.shared.exception.InvalidOperationException;

/**
 * Dịch vụ quản lý lưu trữ và xác thực tệp ảnh đại diện (Avatar Storage) cho người dùng.
 */
@Service
public class AvatarStorageService {

  private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB

  private static final Set<String> ALLOWED_EXTENSIONS = Set.of(".jpg", ".jpeg", ".png", ".webp", ".gif");

  private static final Map<String, String> EXTENSION_TO_MIME = Map.of(
      ".jpg", "image/jpeg",
      ".jpeg", "image/jpeg",
      ".png", "image/png",
      ".webp", "image/webp",
      ".gif", "image/gif"
  );

  private final Path storageRoot;

  public AvatarStorageService(@Value("${edufit.storage.avatars.root:var/avatars}") String rootPath) {
    this.storageRoot = Path.of(rootPath).toAbsolutePath().normalize();
  }

  /**
   * Lưu tệp ảnh đại diện mới của người dùng vào hệ thống tệp local.
   *
   * @param accountId định danh người dùng
   * @param file      tệp ảnh multipart
   * @return đường dẫn công khai để truy xuất ảnh (ví dụ: /api/v1/auth/avatar/...)
   */
  public String storeAvatar(UUID accountId, MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw new InvalidOperationException(ErrorCode.VALIDATION_FAILED, "Tệp ảnh đại diện không được để trống");
    }

    if (file.getSize() > MAX_FILE_SIZE) {
      throw new InvalidOperationException(
          ErrorCode.VALIDATION_FAILED,
          "Kích thước tệp ảnh đại diện vượt quá giới hạn cho phép (tối đa 5MB)"
      );
    }

    String extension = extractAndValidateExtension(file.getOriginalFilename(), file.getContentType());
    String fileName = accountId + "_" + UUID.randomUUID() + extension;
    Path targetPath = resolvePath(fileName);

    try {
      Files.createDirectories(storageRoot);
      try (InputStream is = file.getInputStream()) {
        Files.copy(is, targetPath, StandardCopyOption.REPLACE_EXISTING);
      }
    } catch (IOException e) {
      throw new InvalidOperationException(
          ErrorCode.INVALID_OPERATION,
          "Không thể lưu trữ tệp ảnh đại diện vào hệ thống: " + e.getMessage()
      );
    }

    return "/api/v1/auth/avatar/" + fileName;
  }

  /**
   * Tải tệp tài nguyên ảnh đại diện từ đĩa cứng.
   *
   * @param fileName tên tệp ảnh đại diện
   * @return Resource tệp ảnh
   */
  public Resource loadAvatarResource(String fileName) {
    Path filePath = resolvePath(fileName);
    if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
      throw new EntityNotFoundException("Không tìm thấy tệp ảnh đại diện: " + fileName);
    }
    return new FileSystemResource(filePath);
  }

  /**
   * Xác định MIME type tương ứng theo đuôi tệp.
   */
  public String getMimeType(String fileName) {
    if (fileName == null) return "application/octet-stream";
    int lastDot = fileName.lastIndexOf('.');
    if (lastDot >= 0) {
      String ext = fileName.substring(lastDot).toLowerCase(Locale.ROOT);
      return EXTENSION_TO_MIME.getOrDefault(ext, "image/jpeg");
    }
    return "image/jpeg";
  }

  /**
   * Xóa tệp ảnh đại diện cũ khỏi hệ thống nếu là tệp local.
   */
  public void deleteAvatarFileIfLocal(String avatarUrl) {
    if (avatarUrl == null || !avatarUrl.startsWith("/api/v1/auth/avatar/")) {
      return;
    }
    String fileName = avatarUrl.substring("/api/v1/auth/avatar/".length());
    try {
      Path filePath = resolvePath(fileName);
      Files.deleteIfExists(filePath);
    } catch (Exception ignored) {
      // Bỏ qua lỗi xóa nếu file không tồn tại
    }
  }

  private Path resolvePath(String fileName) {
    if (fileName == null || fileName.isBlank() || fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
      throw new InvalidOperationException(ErrorCode.VALIDATION_FAILED, "Tên tệp ảnh đại diện không hợp lệ");
    }
    Path resolved = storageRoot.resolve(fileName).normalize();
    if (!resolved.startsWith(storageRoot)) {
      throw new InvalidOperationException(ErrorCode.VALIDATION_FAILED, "Đường dẫn tệp nằm ngoài thư mục lưu trữ cho phép");
    }
    return resolved;
  }

  private String extractAndValidateExtension(String originalFilename, String contentType) {
    String ext = null;
    if (originalFilename != null && originalFilename.lastIndexOf('.') >= 0) {
      ext = originalFilename.substring(originalFilename.lastIndexOf('.')).toLowerCase(Locale.ROOT);
    }

    if (ext == null || !ALLOWED_EXTENSIONS.contains(ext)) {
      if (contentType != null) {
        if (contentType.equalsIgnoreCase("image/jpeg") || contentType.equalsIgnoreCase("image/jpg")) ext = ".jpg";
        else if (contentType.equalsIgnoreCase("image/png")) ext = ".png";
        else if (contentType.equalsIgnoreCase("image/webp")) ext = ".webp";
        else if (contentType.equalsIgnoreCase("image/gif")) ext = ".gif";
      }
    }

    if (ext == null || !ALLOWED_EXTENSIONS.contains(ext)) {
      throw new InvalidOperationException(
          ErrorCode.INVALID_FILE_TYPE,
          "Định dạng tệp ảnh không hợp lệ. Hệ thống chỉ chấp nhận ảnh JPEG, PNG, WEBP hoặc GIF."
      );
    }

    return ext;
  }
}
