package vn.edufit.notification;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;

@TestConfiguration(proxyBeanMethods = false)
@Import({NotificationSinkTestBridge.class})
public class ConnectionNotificationTestConfig {}
