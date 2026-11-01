package vn.edufit.verification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.edufit.verification.infra.persistence.entity.Credential;
import vn.edufit.verification.web.request.ReviewVerificationRequest;
import vn.edufit.verification.web.request.SubmitVerificationRequest;

@DisplayName("Kiểm thử DTO & Enum Parser Xác minh & Phê duyệt KYC")
class ReviewVerificationRequestTest {

  @Test
  @DisplayName("Action enum parses standard and legacy values")
  void shouldParseActionValues() {
    assertEquals(ReviewVerificationRequest.Action.APPROVE, ReviewVerificationRequest.Action.fromString("APPROVE"));
    assertEquals(ReviewVerificationRequest.Action.APPROVE, ReviewVerificationRequest.Action.fromString("APPROVED"));
    assertEquals(ReviewVerificationRequest.Action.APPROVE, ReviewVerificationRequest.Action.fromString("approve"));
    assertEquals(ReviewVerificationRequest.Action.REJECT, ReviewVerificationRequest.Action.fromString("REJECT"));
    assertEquals(ReviewVerificationRequest.Action.REJECT, ReviewVerificationRequest.Action.fromString("REJECTED"));
    assertEquals(ReviewVerificationRequest.Action.REJECT, ReviewVerificationRequest.Action.fromString("reject"));
    assertNull(ReviewVerificationRequest.Action.fromString(null));
    assertNull(ReviewVerificationRequest.Action.fromString(""));
  }

  @Test
  @DisplayName("ReviewVerificationRequest record instantiates correctly")
  void shouldInstantiateReviewVerificationRequest() {
    ReviewVerificationRequest approveReq = new ReviewVerificationRequest(
        ReviewVerificationRequest.Action.APPROVE,
        null, null
    );
    assertNotNull(approveReq);
    assertEquals(ReviewVerificationRequest.Action.APPROVE, approveReq.action());
    assertNull(approveReq.reason());

    ReviewVerificationRequest rejectReq = new ReviewVerificationRequest(
        ReviewVerificationRequest.Action.REJECT,
        "Ảnh chứng chỉ bị mờ", null
    );
    assertNotNull(rejectReq);
    assertEquals(ReviewVerificationRequest.Action.REJECT, rejectReq.action());
    assertEquals("Ảnh chứng chỉ bị mờ", rejectReq.reason());
  }

  @Test
  @DisplayName("Credential.Type enum parses standard and legacy IDENTITY_CARD to OTHER")
  void shouldParseCredentialTypeValues() {
    assertEquals(Credential.Type.DEGREE, Credential.Type.fromString("DEGREE"));
    assertEquals(Credential.Type.CERTIFICATE, Credential.Type.fromString("CERTIFICATE"));
    assertEquals(Credential.Type.TEACHING_LICENCE, Credential.Type.fromString("TEACHING_LICENCE"));
    assertEquals(Credential.Type.STUDENT_CARD, Credential.Type.fromString("STUDENT_CARD"));
    assertEquals(Credential.Type.OTHER, Credential.Type.fromString("OTHER"));
    assertEquals(Credential.Type.OTHER, Credential.Type.fromString("IDENTITY_CARD"));
    assertNull(Credential.Type.fromString(null));
    assertNull(Credential.Type.fromString(""));
  }

  @Test
  @DisplayName("SubmitVerificationRequest accepts OTHER type with null year")
  void shouldAcceptOtherTypeAndNullYear() {
    SubmitVerificationRequest.CredentialItem item = new SubmitVerificationRequest.CredentialItem(
        Credential.Type.OTHER,
        "Cục Cảnh sát QLHC về TTXH",
        null,
        "Căn cước công dân",
        List.of(0), null
    );
    SubmitVerificationRequest request = new SubmitVerificationRequest(List.of(item));

    assertNotNull(request);
    assertEquals(1, request.credentials().size());
    assertEquals(Credential.Type.OTHER, request.credentials().getFirst().type());
    assertEquals("Cục Cảnh sát QLHC về TTXH", request.credentials().getFirst().institution());
    assertNull(request.credentials().getFirst().year());
    assertEquals("Căn cước công dân", request.credentials().getFirst().note());
  }
}
