package vn.edufit.progress.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.edufit.progress.domain.model.SessionMilestoneLink;
import vn.edufit.progress.domain.model.SessionMilestoneLink.Type;

class SessionMilestoneResolverTest {

  @Test
  @DisplayName("Buổi đã có liên kết thực tế thì thực tế thắng dự kiến; chưa có thì dùng dự kiến")
  void actualWinsOverPlanned() {
    UUID taught = UUID.randomUUID();
    UUID upcoming = UUID.randomUUID();
    UUID plannedA = UUID.randomUUID();
    UUID actualB = UUID.randomUUID();

    var resolved = SessionMilestoneResolver.resolve(List.of(
        new SessionMilestoneLink(taught, plannedA, Type.PLANNED),
        new SessionMilestoneLink(taught, actualB, Type.ACTUAL),
        new SessionMilestoneLink(upcoming, plannedA, Type.PLANNED)));

    assertThat(resolved.get(taught)).isEqualTo(new SessionMilestoneResolver.Resolved(Type.ACTUAL, List.of(actualB)));
    assertThat(resolved.get(upcoming)).isEqualTo(new SessionMilestoneResolver.Resolved(Type.PLANNED, List.of(plannedA)));
    assertThat(SessionMilestoneResolver.resolve(List.of())).isEmpty();
  }
}
