package vn.edufit.discovery.application.dto;

import vn.edufit.discovery.application.matching.MatchScore;
import vn.edufit.profile.api.dto.TutorDiscoveryProfileDto;

/** Matching output before it is mapped to an HTTP response or public module summary. */
public record TutorMatchResult(
    MatchScore score,
    TutorDiscoveryProfileDto profile,
    String explanation,
    boolean aiGenerated
) {}
