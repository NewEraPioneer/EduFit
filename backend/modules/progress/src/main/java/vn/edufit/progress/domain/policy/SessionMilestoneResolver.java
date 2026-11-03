package vn.edufit.progress.domain.policy;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import vn.edufit.progress.domain.model.SessionMilestoneLink;
import vn.edufit.progress.domain.model.SessionMilestoneLink.Type;

/**
 * Cột mốc hiệu lực của mỗi buổi học: buổi đã có liên kết "thực tế" thì dùng thực tế, không thì dùng "dự kiến".
 */
public final class SessionMilestoneResolver {

  private SessionMilestoneResolver() {}

  public record Resolved(Type type, List<UUID> milestoneIds) {}

  public static Map<UUID, Resolved> resolve(Collection<SessionMilestoneLink> links) {
    Map<UUID, Map<Type, List<UUID>>> bySession = links.stream().collect(Collectors.groupingBy(
        SessionMilestoneLink::sessionId,
        Collectors.groupingBy(SessionMilestoneLink::type,
            Collectors.mapping(SessionMilestoneLink::milestoneId, Collectors.toList()))));
    Map<UUID, Resolved> result = new HashMap<>();
    bySession.forEach((sessionId, byType) -> {
      Type winner = byType.containsKey(Type.ACTUAL) ? Type.ACTUAL : Type.PLANNED;
      result.put(sessionId, new Resolved(winner, List.copyOf(byType.get(winner))));
    });
    return result;
  }
}
