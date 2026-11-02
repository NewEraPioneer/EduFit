package vn.edufit.ai.application;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.edufit.ai.api.AiFeature;
import vn.edufit.ai.api.AiGateway;
import vn.edufit.ai.api.AiRequest;
import vn.edufit.ai.api.AiResponse;
import vn.edufit.ai.api.AiTimeoutException;
import vn.edufit.ai.api.AiUnavailableException;
import vn.edufit.ai.api.AiUsageQuery;
import vn.edufit.ai.infra.client.LlmProvider;
import vn.edufit.ai.infra.config.AiProperties;
import vn.edufit.ai.infra.persistence.AiRequestLogRepository;

/**
 * Dịch vụ điều phối chính của phân hệ AI Gateway (Main Orchestration Service).
 *
 * <p>Mục đích thiết kế & Kiến trúc phân tầng:
 *
 * <ul>
 *   <li><b>Hiện thực hóa cả 2 Public Ports:</b>
 *       <ul>
 *         <li>{@link AiGateway}: Điều phối luồng gọi LLM, đo độ trễ, lọc chuỗi đầu ra và ghi log.
 *         <li>{@link AiUsageQuery}: Cung cấp số liệu thống kê lượt gọi phục vụ kiểm soát hạn ngạch
 *             ở tầng nghiệp vụ.
 *       </ul>
 *   <li><b>Độc lập nghiệp vụ hoàn toàn:</b> Không chứa logic về gia sư, học viên, hay thuật toán
 *       matching.
 *   <li><b>Phân định rõ ngoại lệ kỹ thuật:</b>
 *       <ul>
 *         <li>Ngoại lệ quá hạn 15s ({@link AiTimeoutException} - NFR-10).
 *         <li>Ngoại lệ lỗi kết nối hoặc phản hồi lỗi ({@link AiUnavailableException}).
 *       </ul>
 * </ul>
 */
@Service
public class AiGatewayService implements AiGateway, AiUsageQuery {

  private static final Logger log = LoggerFactory.getLogger(AiGatewayService.class);

  private final LlmProvider llmProvider;
  private final OutputSanitizer outputSanitizer;
  private final AiRequestLogger requestLogger;
  private final AiRequestLogRepository logRepository;
  private final ExecutorService providerExecutor;
  private final Duration timeout;
  private final AiMatchQuotaService matchQuota;

  public AiGatewayService(
      LlmProvider llmProvider,
      OutputSanitizer outputSanitizer,
      AiRequestLogger requestLogger,
      AiRequestLogRepository logRepository,
      @Qualifier("aiProviderExecutor") ExecutorService providerExecutor,
      AiProperties properties,
      AiMatchQuotaService matchQuota) {
    this.llmProvider = Objects.requireNonNull(llmProvider, "llmProvider không được phép null");
    this.outputSanitizer =
        Objects.requireNonNull(outputSanitizer, "outputSanitizer không được phép null");
    this.requestLogger =
        Objects.requireNonNull(requestLogger, "requestLogger không được phép null");
    this.logRepository =
        Objects.requireNonNull(logRepository, "logRepository không được phép null");
    this.providerExecutor = Objects.requireNonNull(providerExecutor, "providerExecutor");
    this.timeout = properties.timeout();
    this.matchQuota = Objects.requireNonNull(matchQuota, "matchQuota");
  }

  @Override
  public AiResponse complete(AiRequest request) {
    Objects.requireNonNull(request, "AiRequest không được phép null");

    UUID userId = request.userId();
    String featureName = request.feature().name();
    // Admission is enforced here, including direct gateway callers. Denials are not attempts.
    UUID reservation =
        request.feature() == AiFeature.MATCH_EXPLANATION
            ? matchQuota
                .tryReserve(userId)
                .orElseThrow(() -> new AiUnavailableException("Matching AI quota exhausted"))
            : null;
    long startTime = System.currentTimeMillis();
    String outcome = "ERROR";

    log.debug("Bắt đầu xử lý AI request [user: {}, feature: {}]", userId, featureName);

    try {
      // Bound the entire provider wait, not just individual socket connect/read operations.
      LlmProvider.LlmResult rawResult = callProvider(request);

      // 2. Làm sạch và kiểm duyệt chuỗi văn bản đầu ra
      String sanitizedText = outputSanitizer.sanitize(rawResult.text());
      int latency = (int) (System.currentTimeMillis() - startTime);

      outcome = "SUCCESS";

      log.info(
          "Hoàn tất AI request thành công [user: {}, feature: {}, latency: {}ms]",
          userId,
          featureName,
          latency);

      return new AiResponse(
          sanitizedText, latency, rawResult.tokenCount(), rawResult.generatedByModel());

    } catch (AiTimeoutException ex) {
      // Cuộc gọi bị quá thời gian 15 giây (NFR-10)
      int latency = (int) (System.currentTimeMillis() - startTime);
      outcome = "TIMEOUT";
      log.warn(
          "AI request bị TIMEOUT sau {}ms [user: {}, feature: {}]", latency, userId, featureName);
      throw ex;

    } catch (Exception ex) {
      // Các lỗi kết nối mạng, lỗi 5xx từ nhà cung cấp hoặc dữ liệu không hợp lệ
      int latency = (int) (System.currentTimeMillis() - startTime);
      log.error(
          "AI request gặp ERROR sau {}ms [user: {}, feature: {}, type: {}, message: {}]",
          latency,
          userId,
          featureName,
          ex.getClass().getSimpleName(),
          ex.getMessage(),
          ex);

      if (ex instanceof AiUnavailableException aue) {
        throw aue;
      }
      throw new AiUnavailableException("Dịch vụ AI hiện không khả dụng");
    } finally {
      int latency = (int) (System.currentTimeMillis() - startTime);
      if (reservation != null) matchQuota.finish(reservation, outcome);
      requestLogger.log(userId, featureName, outcome, latency);
    }
  }

  private LlmProvider.LlmResult callProvider(AiRequest request) {
    var call =
        providerExecutor.submit(
            () ->
                llmProvider.call(
                    request.systemPrompt(), request.userPrompt(), request.temperature()));
    try {
      return call.get(timeout.toNanos(), TimeUnit.NANOSECONDS);
    } catch (TimeoutException ex) {
      call.cancel(true);
      throw new AiTimeoutException("AI provider exceeded " + timeout, ex);
    } catch (InterruptedException ex) {
      call.cancel(true);
      Thread.currentThread().interrupt();
      throw new AiUnavailableException("AI provider wait interrupted", ex);
    } catch (ExecutionException ex) {
      if (ex.getCause() instanceof RuntimeException cause) {
        throw cause;
      }
      throw new AiUnavailableException("AI provider failed");
    }
  }

  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
  public long countRequestsSince(UUID userId, AiFeature feature, Instant since) {
    Objects.requireNonNull(userId, "userId không được phép null");
    Objects.requireNonNull(feature, "feature không được phép null");
    Objects.requireNonNull(since, "mốc thời gian since không được phép null");

    return logRepository.countByUserIdAndFeatureAndCreatedAtAfter(userId, feature.name(), since);
  }
}
