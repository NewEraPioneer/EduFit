package vn.edufit.iam.infra.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import java.io.IOException;
import java.security.GeneralSecurityException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.edufit.iam.application.OAuthUserInfo;
import vn.edufit.shared.exception.ErrorCode;
import vn.edufit.shared.exception.InvalidOperationException;

class GoogleIdTokenVerifierAdapterTest {

  private static final String CLIENT_ID = "edufit-web-client-id";

  private GoogleIdTokenVerifier mockGoogleVerifier;
  private GoogleIdTokenVerifierAdapter adapter;

  @BeforeEach
  void setUp() {
    mockGoogleVerifier = mock(GoogleIdTokenVerifier.class);
    adapter = new GoogleIdTokenVerifierAdapter(mockGoogleVerifier, CLIENT_ID);
  }

  private static GoogleIdToken.Payload verifiedPayload() {
    GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
    payload.setSubject("google-sub-1001");
    payload.setEmail("student.verified@gmail.com");
    payload.setEmailVerified(true);
    payload.setAudience(CLIENT_ID);
    return payload;
  }

  private void stubToken(String rawToken, GoogleIdToken.Payload payload) throws GeneralSecurityException, IOException {
    GoogleIdToken token = mock(GoogleIdToken.class);
    when(token.getPayload()).thenReturn(payload);
    when(mockGoogleVerifier.verify(rawToken)).thenReturn(token);
  }

  @Test
  @DisplayName("Thiếu GOOGLE_CLIENT_ID thì từ chối mọi token, không bao giờ xác thực bỏ qua audience")
  void verify_withoutConfiguredClientId_shouldRejectWithoutCallingGoogle() {
    GoogleIdTokenVerifierAdapter unconfigured = new GoogleIdTokenVerifierAdapter(mockGoogleVerifier, " ");

    assertThatThrownBy(() -> unconfigured.verify("any.google.token"))
        .isInstanceOf(InvalidOperationException.class)
        .satisfies(ex -> assertThat(((InvalidOperationException) ex).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
    verifyNoInteractions(mockGoogleVerifier);
  }

  @Test
  @DisplayName("Constructor dùng bởi Spring phải khởi tạo được cả khi chưa cấu hình client-id")
  void springConstructor_shouldInstantiate() {
    assertThat(new GoogleIdTokenVerifierAdapter(CLIENT_ID)).isNotNull();
    assertThat(new GoogleIdTokenVerifierAdapter("")).isNotNull();
  }

  @Test
  @DisplayName("Token cấp cho ứng dụng khác (audience sai) phải bị từ chối")
  void verify_withForeignAudience_shouldThrowUnauthorized() throws GeneralSecurityException, IOException {
    GoogleIdToken.Payload payload = verifiedPayload();
    payload.setAudience("another-app-client-id");
    stubToken("foreign.token", payload);

    assertThatThrownBy(() -> adapter.verify("foreign.token"))
        .isInstanceOf(InvalidOperationException.class)
        .satisfies(ex -> assertThat(((InvalidOperationException) ex).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
  }

  @Test
  @DisplayName("Email chưa được Google xác minh thì không được dùng làm danh tính (chống chiếm tài khoản qua liên kết email)")
  void verify_withUnverifiedEmail_shouldThrowUnauthorized() throws GeneralSecurityException, IOException {
    GoogleIdToken.Payload payload = verifiedPayload();
    payload.setEmailVerified(false);
    stubToken("unverified.token", payload);

    assertThatThrownBy(() -> adapter.verify("unverified.token"))
        .isInstanceOf(InvalidOperationException.class)
        .satisfies(ex -> assertThat(((InvalidOperationException) ex).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
  }

  @Test
  @DisplayName("idToken rỗng hoặc null phải ném ngoại lệ VALIDATION_FAILED")
  void verify_withBlankToken_shouldThrowValidationFailed() {
    assertThatThrownBy(() -> adapter.verify(null))
        .isInstanceOf(InvalidOperationException.class)
        .satisfies(ex -> assertThat(((InvalidOperationException) ex).getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));

    assertThatThrownBy(() -> adapter.verify("   "))
        .isInstanceOf(InvalidOperationException.class)
        .satisfies(ex -> assertThat(((InvalidOperationException) ex).getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
  }

  @Test
  @DisplayName("Khi GoogleIdTokenVerifier trả về null (chữ ký sai hoặc hết hạn), phải ném UNAUTHORIZED")
  void verify_withInvalidSignatureOrExpired_shouldThrowUnauthorized() throws GeneralSecurityException, IOException {
    when(mockGoogleVerifier.verify("invalid.token")).thenReturn(null);

    assertThatThrownBy(() -> adapter.verify("invalid.token"))
        .isInstanceOf(InvalidOperationException.class)
        .satisfies(ex -> assertThat(((InvalidOperationException) ex).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED))
        .hasMessageContaining("không hợp lệ hoặc đã hết hạn");
  }

  @Test
  @DisplayName("Khi GoogleIdTokenVerifier xác thực thành công, trích xuất chính xác OAuthUserInfo")
  void verify_withValidToken_shouldReturnOAuthUserInfo() throws GeneralSecurityException, IOException {
    GoogleIdToken.Payload payload = verifiedPayload();
    payload.set("name", "Nguyễn Văn Test");
    payload.set("picture", "https://google.com/avatar.jpg");
    stubToken("valid.google.token", payload);

    OAuthUserInfo result = adapter.verify("valid.google.token");

    assertThat(result).isNotNull();
    assertThat(result.provider()).isEqualTo("GOOGLE");
    assertThat(result.providerUserId()).isEqualTo("google-sub-1001");
    assertThat(result.email()).isEqualTo("student.verified@gmail.com");
    assertThat(result.fullName()).isEqualTo("Nguyễn Văn Test");
    assertThat(result.avatarUrl()).isEqualTo("https://google.com/avatar.jpg");
  }

  @Test
  @DisplayName("Khi Google SDK ném GeneralSecurityException, adapter phải bọc lại thành UNAUTHORIZED")
  void verify_whenGoogleSdkThrowsException_shouldWrapInUnauthorized() throws GeneralSecurityException, IOException {
    when(mockGoogleVerifier.verify("corrupted.token")).thenThrow(new GeneralSecurityException("Invalid key signature"));

    assertThatThrownBy(() -> adapter.verify("corrupted.token"))
        .isInstanceOf(InvalidOperationException.class)
        .satisfies(ex -> assertThat(((InvalidOperationException) ex).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED))
        .hasMessageContaining("Không thể xác thực tính hợp lệ");
  }
}
