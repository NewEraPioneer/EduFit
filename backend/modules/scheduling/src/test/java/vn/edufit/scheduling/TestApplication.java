package vn.edufit.scheduling;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot Test Root Application cho phân hệ Scheduling.
 *
 * <p>Cung cấp ngữ cảnh Spring Boot Context tối thiểu và cô lập để thực thi các bài kiểm thử
 * tích hợp (Integration Tests) và kiểm thử tranh chấp đồng thời (Concurrency Stress Tests)
 * mà không cần phải khởi động toàn bộ các phân hệ khác của EduFit.
 */
@SpringBootApplication
public class TestApplication {
}
