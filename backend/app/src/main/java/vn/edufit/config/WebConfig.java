package vn.edufit.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TimeZone;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Cấu hình tầng Web MVC và chuẩn hóa tuần tự hóa dữ liệu JSON (Jackson).
 *
 * <p>Mục đích thiết kế và liên kết yêu cầu phi chức năng (NFR):
 * <ul>
 *   <li><b>Cấu hình CORS (Cross-Origin Resource Sharing):</b> Cho phép Frontend Next.js (chạy tại {@code http://localhost:3000})
 *       gửi yêu cầu API đến Backend Spring Boot (chạy tại {@code http://localhost:8080}).
 *       Quan trọng: Cấu hình {@code allowCredentials(true)} để trình duyệt cho phép đính kèm Cookie Session.</li>
 *   <li><b>Chuẩn hóa múi giờ Việt Nam UTC+7 (NFR-18):</b> Cấu hình Jackson {@link ObjectMapper}
 *       luôn tuần tự hóa và giải tuần tự hóa ngày giờ theo múi giờ {@code Asia/Ho_Chi_Minh}, định dạng chuỗi ISO-8601,
 *       tắt ghi timestamp số nguyên để tránh việc lệch giờ giữa máy chủ và giao diện.</li>
 * </ul>
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

  /** Múi giờ chuẩn hóa toàn hệ thống theo yêu cầu NFR-18. */
  public static final ZoneId VIETNAM_ZONE_ID = ZoneId.of("Asia/Ho_Chi_Minh");

  @Value("${edufit.frontend.base-url:http://localhost:3000}")
  private String frontendBaseUrl;

  @Value("${edufit.cors.allowed-origin-patterns:http://localhost:3000,http://127.0.0.1:3000,https://*.vercel.app}")
  private String[] allowedOriginPatterns;

  /**
   * Thiết lập chính sách CORS cho phép ứng dụng Next.js kết nối an toàn.
   */
  @Override
  public void addCorsMappings(CorsRegistry registry) {
    List<String> origins = new ArrayList<>(Arrays.asList(allowedOriginPatterns));
    if (frontendBaseUrl != null && !frontendBaseUrl.isBlank()) {
      String trimmed = frontendBaseUrl.trim().replaceAll("/+$", "");
      if (!origins.contains(trimmed)) {
        origins.add(trimmed);
      }
    }

    registry.addMapping("/**")
        .allowedOriginPatterns(origins.toArray(new String[0]))
        .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
        .allowedHeaders("*")
        .allowCredentials(true)
        .maxAge(3600);
  }

  /**
   * Khởi tạo bean ObjectMapper chính cho toàn bộ ứng dụng, chuẩn hóa múi giờ và định dạng ngày giờ.
   */
  @Bean
  @Primary
  public ObjectMapper objectMapper() {
    ObjectMapper mapper = new ObjectMapper();

    // 1. Chuẩn hóa múi giờ theo NFR-18 (Asia/Ho_Chi_Minh)
    mapper.setTimeZone(TimeZone.getTimeZone(VIETNAM_ZONE_ID));

    // 2. Đăng ký module hỗ trợ các kiểu Java 8 Date/Time (Instant, LocalDateTime, LocalDate...)
    mapper.registerModule(new JavaTimeModule());

    // 3. Tắt ghi ngày giờ dưới dạng số nguyên timestamp (mili-giây), ép định dạng chuỗi ISO-8601
    mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    // 4. Bỏ qua các trường lạ không định nghĩa trong DTO mà không ném lỗi (linh hoạt cho API)
    mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    return mapper;
  }
}
