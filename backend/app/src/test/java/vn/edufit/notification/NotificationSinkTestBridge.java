package vn.edufit.notification;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import vn.edufit.notification.application.listener.NotificationEventListener;
import vn.edufit.notification.infra.persistence.entity.Notification;
import vn.edufit.notification.infra.persistence.repository.NotificationRepository;
import vn.edufit.profile.api.ProfileFacade;
import vn.edufit.shared.notification.NotificationSink;

@TestConfiguration(proxyBeanMethods = false)
@Import({NotificationEventListener.class})
public class NotificationSinkTestBridge {

  @Bean
  public NotificationRepository notificationRepository(NotificationSink sink) {
    var repository = mock(NotificationRepository.class);
    when(repository.save(any(Notification.class))).thenAnswer(call -> {
      Notification row = call.getArgument(0);
      sink.notify(
          row.getUserId(),
          row.getAboutStudentId(),
          row.getType(),
          row.getTitle(),
          row.getShortText(),
          row.getTargetType(),
          row.getTargetId()
      );
      return row;
    });
    return repository;
  }

  @Bean
  @org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean(vn.edufit.connection.api.ConnectionFacade.class)
  public vn.edufit.connection.api.ConnectionFacade connectionFacade() {
    return mock(vn.edufit.connection.api.ConnectionFacade.class);
  }

  @Bean
  @org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean(ProfileFacade.class)
  public ProfileFacade profileFacade() {
    return mock(ProfileFacade.class);
  }
}
