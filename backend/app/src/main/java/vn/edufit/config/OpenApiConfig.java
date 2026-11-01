package vn.edufit.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.List;
import java.util.Set;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springdoc.core.customizers.OpenApiCustomizer;

/**
 * Cấu hình tài liệu hóa API chuẩn OpenAPI 3 và giao diện Swagger UI (SpringDoc).
 *
 * <p>Mục đích thiết kế và tuân thủ quyết định kiến trúc:
 * <ul>
 *   <li><b>ADR-002 Decision 1:</b> OpenAPI/SpringDoc đóng vai trò là "Single Source of Truth" để tự động sinh
 *       TypeScript types và HTTP Client cho Frontend Next.js (thông qua OpenAPI Generator).</li>
 *   <li><b>Hỗ trợ kiểm thử trực quan:</b> Cung cấp trang giao diện trực quan tại {@code /swagger-ui.html} giúp
 *       các thành viên trong nhóm 4 người kiểm thử trực tiếp các endpoint API.</li>
 *   <li><b>Tích hợp cơ chế xác thực Session:</b> Khai báo Security Scheme kiểu Cookie ({@code EDUFIT_SESSION})
 *       để giao diện Swagger hiển thị nút "Authorize", cho phép gắn Session Cookie khi test các API cần đăng nhập.</li>
 * </ul>
 */
@Configuration
public class OpenApiConfig {

  private static final String COOKIE_AUTH_SCHEME_NAME = "SessionCookieAuth";
  private static final Set<String> PUBLIC_GET_PATHS =
      Set.of(
          "/api/v1/discovery/tutors",
          "/api/v1/discovery/tutors/{tutorId}",
          "/api/v1/catalogs",
          "/api/v1/catalogs/education-levels",
          "/api/v1/catalogs/subjects",
          "/api/v1/catalogs/learning",
          "/api/v1/catalogs/goal-tags");

  @Bean
  public OpenAPI customOpenAPI() {
    return new OpenAPI()
        .info(new Info()
            .title("EduFit Monolith API Specification")
            .description("Tài liệu đặc tả API chính thức của Nền tảng kết nối Gia sư và Học sinh EduFit (SWP391)")
            .version("v1.0.0")
            .contact(new Contact()
                .name("EduFit Engineering Team")
                .email("team@edufit.vn"))
            .license(new License()
                .name("Apache 2.0")
                .url("https://www.apache.org/licenses/LICENSE-2.0")))
        // Khai báo cơ chế bảo mật Session Cookie
        .components(new Components()
            .addSecuritySchemes(COOKIE_AUTH_SCHEME_NAME, new SecurityScheme()
                .type(SecurityScheme.Type.APIKEY)
                .in(SecurityScheme.In.COOKIE)
                .name("EDUFIT_SESSION")
                .description("Session Cookie định danh phiên làm việc của người dùng sau khi đăng nhập thành công.")))
        // Yêu cầu áp dụng mặc định cho các tài liệu API
        .addSecurityItem(new SecurityRequirement().addList(COOKIE_AUTH_SCHEME_NAME));
  }

  @Bean
  public OpenApiCustomizer publicOperationSecurityCustomizer() {
    return openApi -> {
      if (openApi.getPaths() == null) return;
      PUBLIC_GET_PATHS.forEach(path -> {
        var item = openApi.getPaths().get(path);
        if (item != null && item.getGet() != null) item.getGet().setSecurity(List.of());
      });
    };
  }
}
