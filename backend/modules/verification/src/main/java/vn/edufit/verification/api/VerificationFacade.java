package vn.edufit.verification.api;

import java.util.UUID;
import vn.edufit.verification.api.dto.VerificationStatusDto;

public interface VerificationFacade {

  boolean isTutorVerified(UUID tutorId);

  VerificationStatusDto getTutorVerificationStatus(UUID tutorId);

  long countPendingRequests();
}
