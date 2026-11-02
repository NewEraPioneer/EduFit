package vn.edufit.scheduling.infra.config;

import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Cấu hình Bean {@link Clock} cho phân hệ Scheduling.
 *
 * <p>Giúp phân hệ có thể hoạt động độc lập với giờ UTC chuẩn,
 * đồng thời cho phép các lớp kiểm thử Unit Test / Integration Test
 * ghi đè bằng {@code Clock.fixed(...)} để kiểm tra chính xác các ca biên thời gian.
 */
@Configuration
public class SchedulingClockConfig {

  @Bean
  @ConditionalOnMissingBean(Clock.class)
  public Clock schedulingClock() {
    return Clock.systemUTC();
  }
}
