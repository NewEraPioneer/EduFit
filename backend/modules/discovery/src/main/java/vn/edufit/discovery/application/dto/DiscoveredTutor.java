package vn.edufit.discovery.application.dto;

import vn.edufit.discovery.application.matching.MatchScore;
import vn.edufit.profile.api.dto.TutorDiscoveryProfileDto;

/** Gia sư trong kết quả Discovery; {@code fit} có khi tìm kèm nhu cầu học, null khi không. */
public record DiscoveredTutor(TutorDiscoveryProfileDto profile, MatchScore fit) {}
