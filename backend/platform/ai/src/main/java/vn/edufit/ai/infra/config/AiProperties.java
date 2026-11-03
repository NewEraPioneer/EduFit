package vn.edufit.ai.infra.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Lớp cấu hình nạp các thuộc tính {@code edufit.ai.*} từ file {@code application.yml} hoặc biến môi
 * trường.
 *
 * <p>Mục đích thiết kế:
 *
 * <ul>
 *   <li><b>Ánh xạ cấu hình chuẩn 12-Factor App:</b> Cho phép ghi đè linh hoạt qua biến môi trường
 *       ({@code AI_PROVIDER}, {@code GEMINI_API_KEY}, {@code GEMINI_MODEL}) mà không làm lộ secret
 *       trên Git.
 *   <li><b>Khống chế Timeout chặt chẽ (NFR-10):</b> Giá trị mặc định là 15 giây ({@code 15s}), được
 *       dùng để cấu hình trực tiếp vào tầng Socket của {@link
 *       org.springframework.web.client.RestClient}.
 *   <li><b>Tính bất biến (Immutability):</b> Được hiện thực dưới dạng Java Record, an toàn tuyệt
 *       đối khi nạp cấu hình trong môi trường đa luồng.
 * </ul>
 *
 * @param provider Tên nhà cung cấp AI muốn kích hoạt ("gemini" hoặc "stub"), bắt buộc đặt qua AI_PROVIDER, không có giá trị mặc định.
 * @param timeout Thời gian chờ tối đa cho mỗi cuộc gọi LLM (mặc định 15 giây theo NFR-10).
 * @param gemini Các thông số kết nối chi tiết tới Google Gemini API.
 */
@ConfigurationProperties(prefix = "edufit.ai")
public record AiProperties(
    String provider,
    @DefaultValue("15s") Duration timeout,
    @DefaultValue GeminiProperties gemini) {

  /**
   * Cấu hình chi tiết dành riêng cho nhà cung cấp Google Gemini.
   *
   * @param apiKey Khóa bí mật API của Google Gemini (đọc từ biến môi trường GEMINI_API_KEY).
   * @param model Model ID, đặt qua GEMINI_MODEL; không có giá trị mặc định và bắt buộc khi provider là gemini.
   * @param baseUrl Địa chỉ gốc của Google Generative Language API.
   */
  public record GeminiProperties(
      @DefaultValue("") String apiKey,
      String model,
      @DefaultValue("https://generativelanguage.googleapis.com") String baseUrl) {

    public GeminiProperties {
      if (apiKey == null) {
        apiKey = "";
      }
      if (model == null) {
        model = "";
      }
      if (baseUrl == null || baseUrl.isBlank()) {
        baseUrl = "https://generativelanguage.googleapis.com";
      }
    }

    @Override
    public String toString() {
      return "GeminiProperties[apiKey=<redacted>, model=<configured>, baseUrl=<configured>]";
    }
  }

  public AiProperties {
    if (provider == null) {
      throw new IllegalArgumentException("AI_PROVIDER is required (stub or gemini)");
    }
    if (!provider.equals("stub") && !provider.equals("gemini")) {
      throw new IllegalArgumentException("AI_PROVIDER must be stub or gemini");
    }
    if (timeout == null || timeout.isNegative() || timeout.isZero()) {
      timeout = Duration.ofSeconds(15);
    }
    if (gemini == null) {
      gemini = new GeminiProperties("", "", "");
    }
    if (provider.equals("gemini")) {
      if (gemini.apiKey().isBlank()) {
        throw new IllegalArgumentException("GEMINI_API_KEY is required when AI_PROVIDER=gemini");
      }
      if (!gemini.model().matches("[A-Za-z0-9][A-Za-z0-9._-]*")) {
        throw new IllegalArgumentException(
            "GEMINI_MODEL must be a model ID without a path or query");
      }
    }
  }
}
