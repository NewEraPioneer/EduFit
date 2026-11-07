package vn.edufit.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Instant;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import vn.edufit.shared.exception.ErrorCode;
import vn.edufit.shared.response.ApiError;

/**
 * Cấu hình bảo mật hệ thống toàn diện sử dụng Spring Security.
 *
 * <p>Mục đích thiết kế và tuân thủ quyết định kiến trúc:
 * <ul>
 *   <li><b>ADR-001 Decision 5 (Primary Track):</b> Cơ chế Server-side Session với Opaque Token lưu qua Cookie {@code httpOnly}.
 *       Thỏa mãn hoàn hảo <b>NFR-04</b> (hết hạn sau 60 phút không thao tác, có thể hủy phiên tức thời) và
 *       <b>FR-04</b> (hủy toàn bộ phiên đang hoạt động khác khi người dùng đổi mật khẩu).</li>
 *   <li><b>NFR-01 (Mật khẩu an toàn):</b> Bắt buộc sử dụng thuật toán băm <b>Argon2</b> thông qua {@link Argon2PasswordEncoder},
 *       chống lại các cuộc tấn công Brute-force và Rainbow Table bằng cách cấu hình chi phí bộ nhớ và thời gian băm.</li>
 *   <li><b>NFR-07 (Role-Based Access Control):</b> Kích hoạt {@link EnableMethodSecurity} để các module nghiệp vụ
 *       có thể bảo vệ từng hàm nghiệp vụ bằng {@code @PreAuthorize("hasRole('TUTOR')")}.</li>
 *   <li><b>RESTful Error Response:</b> Ghi đè {@link AuthenticationEntryPoint} và {@link AccessDeniedHandler} để khi gặp
 *       lỗi 401 hoặc 403, hệ thống trả về đúng định dạng JSON {@link ApiError} thay vì trang HTML mặc định của Spring.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

  private final ObjectMapper objectMapper;

  public SecurityConfig(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  /**
   * Cấu hình chuỗi lọc bảo mật (Security Filter Chain) cho các yêu cầu HTTP.
   */
  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
        .cors(org.springframework.security.config.Customizer.withDefaults())
        // REST clients handle 401 directly; do not persist redirect requests in anonymous sessions.
        .requestCache(cache -> cache.disable())
        // 1. Cấu hình bảo vệ chống tấn công CSRF:
        // Bảo vệ phiên làm việc dựa trên Cookie EDUFIT_SESSION với SameSite=Lax (ADR-001 / RFC 6265bis)
        .csrf(csrf -> csrf.spa().csrfTokenRepository(csrfTokenRepository())
            .ignoringRequestMatchers("/api/v1/payment/webhooks/payos"))

        // 2. Cấu hình phân quyền truy cập URL theo từng vai trò (NFR-07: Role-Based Access Control)
        .authorizeHttpRequests(auth -> auth
             .requestMatchers(HttpMethod.POST, "/api/v1/payment/webhooks/payos").permitAll()
            // 2.1. Các đường dẫn công khai (không cần đăng nhập: Xác thực, Danh mục chung, Swagger, Actuator)
            .requestMatchers(
                "/api/v1/auth/csrf",
                "/api/v1/auth/register",
                "/api/v1/auth/register/resend",
                "/api/v1/auth/register/verify",
                "/api/v1/auth/login",
                "/api/v1/auth/logout",
                "/api/v1/auth/forgot-password",
                "/api/v1/auth/forgot-password/verify",
                "/api/v1/auth/reset-password",
                "/api/v1/auth/oauth2/**",
                "/api/v1/catalogs/**",
                "/v3/api-docs/**",
                "/swagger-ui/**",
                "/swagger-ui.html",
                "/actuator/health",
                "/error"
            ).permitAll()
            .requestMatchers(HttpMethod.GET, "/api/v1/discovery/tutors", "/api/v1/discovery/tutors/**").permitAll()
            .requestMatchers(HttpMethod.GET, "/api/v1/reviews/tutors/**").permitAll()
            .requestMatchers(HttpMethod.GET, "/api/v1/auth/avatar/**").permitAll()

            // 2.2. Phân quyền dành riêng cho Quản trị viên (ADMIN)
            .requestMatchers(
                "/api/v1/admin/**",
                "/api/v1/verifications/admin/**"
            ).hasRole("ADMIN")

            // 2.3. Phân quyền dành riêng cho Gia sư (TUTOR)
            .requestMatchers(
                "/api/v1/verifications/tutors/**",
                "/api/v1/profiles/tutors/me",
                "/api/v1/profiles/tutors/me/**"
            ).hasRole("TUTOR")

            // 2.4. Phân quyền dành riêng cho Học sinh & Phụ huynh (STUDENT, PARENT)
            .requestMatchers(
                "/api/v1/profiles/students/me",
                "/api/v1/profiles/students/me/**",
                "/api/v1/learning-needs",
                "/api/v1/learning-needs/**",
                "/api/v1/reviews",
                "/api/v1/reviews/**"
            ).hasAnyRole("STUDENT", "PARENT", "ADMIN")

            // 2.5. Mọi yêu cầu API nghiệp vụ khác đều bắt buộc phải đăng nhập
            .anyRequest().authenticated()
        )

        // 3. Quản lý phiên làm việc (Session Management) theo ADR-001 Decision 5
        .sessionManagement(session -> session
            .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
            .maximumSessions(5)
            .maxSessionsPreventsLogin(false)
        )

        // 4. Xử lý phản hồi lỗi xác thực và phân quyền chuẩn REST JSON
        .exceptionHandling(exceptions -> exceptions
            .authenticationEntryPoint(restAuthenticationEntryPoint())
            .accessDeniedHandler(restAccessDeniedHandler())
        );

    return http.build();
  }

  @Bean
  public CookieCsrfTokenRepository csrfTokenRepository() {
    CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
    repository.setCookiePath("/");
    repository.setCookieCustomizer(cookie -> cookie.sameSite("Lax"));
    return repository;
  }

  @Bean
  public org.springframework.session.web.http.CookieSerializer sessionCookieSerializer() {
    var serializer = new org.springframework.session.web.http.DefaultCookieSerializer();
    serializer.setCookieName("EDUFIT_SESSION");
    serializer.setCookiePath("/");
    serializer.setUseHttpOnlyCookie(true);
    serializer.setSameSite("Lax");
    // Default secure detection follows request.isSecure(), supporting local HTTP and production HTTPS.
    return serializer;
  }

  /**
   * Bean mã hóa mật khẩu chuẩn Argon2 đáp ứng yêu cầu NFR-01.
   * <p>Sử dụng các thông số chuẩn của Spring Security: saltLength=16, hashLength=32, parallelism=1, memory=16384, iterations=2.
   */
  @Bean
  public PasswordEncoder passwordEncoder() {
    return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
  }

  /**
   * Bộ xử lý trả về HTTP 401 kèm định dạng ApiError JSON khi người dùng chưa xác thực.
   */
  @Bean
  public AuthenticationEntryPoint restAuthenticationEntryPoint() {
    return (request, response, authException) -> {
      response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
      response.setContentType(MediaType.APPLICATION_JSON_VALUE);
      response.setCharacterEncoding("UTF-8");

      ApiError apiError = ApiError.of(
          ErrorCode.UNAUTHORIZED.name(),
          "Yêu cầu cần được xác thực hoặc phiên đăng nhập đã hết hạn."
      );
      response.getWriter().write(objectMapper.writeValueAsString(apiError));
    };
  }

  /**
   * Bộ xử lý trả về HTTP 403 kèm định dạng ApiError JSON khi người dùng không đủ quyền.
   */
  @Bean
  public AccessDeniedHandler restAccessDeniedHandler() {
    return (request, response, accessDeniedException) -> {
      response.setStatus(HttpServletResponse.SC_FORBIDDEN);
      response.setContentType(MediaType.APPLICATION_JSON_VALUE);
      response.setCharacterEncoding("UTF-8");

      ApiError apiError = ApiError.of(
          ErrorCode.FORBIDDEN.name(),
          "Bạn không có quyền thực hiện hành động này trong hệ thống."
      );
      response.getWriter().write(objectMapper.writeValueAsString(apiError));
    };
  }
}
