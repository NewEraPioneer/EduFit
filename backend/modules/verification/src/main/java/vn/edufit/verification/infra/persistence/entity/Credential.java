package vn.edufit.verification.infra.persistence.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "credential")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Credential {

  public enum Type {
    DEGREE,
    CERTIFICATE,
    TEACHING_LICENCE,
    STUDENT_CARD,
    OTHER;

    @com.fasterxml.jackson.annotation.JsonCreator
    public static Type fromString(String value) {
      if (value == null || value.isBlank()) {
        return null;
      }
      String normalized = value.trim().toUpperCase();
      if ("IDENTITY_CARD".equals(normalized)) {
        return OTHER;
      }
      return Type.valueOf(normalized);
    }
  }

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "credential_id")
  private UUID credentialId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "request_id", nullable = false)
  private VerificationRequest verificationRequest;

  @Enumerated(EnumType.STRING)
  @Column(name = "type", nullable = false, length = 30)
  private Type type;

  @Column(name = "institution", nullable = false, length = 200)
  private String institution;

  @Column(name = "year")
  private Short year;

  @Column(name = "note", length = 500)
  private String note;

  /** Gói dạy mà giấy tờ này làm bằng chứng cho mục tiêu premium (HSG & Thi chuyên, chứng chỉ quốc tế). */
  @Column(name = "offering_id")
  private UUID offeringId;

  @OneToMany(mappedBy = "credential", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("uploadedAt ASC")
  @org.hibernate.annotations.BatchSize(size = 20)
  private java.util.Set<CredentialFile> files = new java.util.LinkedHashSet<>();

  public static Credential create(Type type, String institution, Short year, String note, UUID offeringId) {
    Credential credential = new Credential();
    credential.type = type;
    credential.institution = institution.trim();
    credential.year = year;
    credential.note = note == null || note.isBlank() ? null : note.trim();
    credential.offeringId = offeringId;
    return credential;
  }

  void attachTo(VerificationRequest request) {
    this.verificationRequest = request;
  }

  public void addFile(CredentialFile file) {
    files.add(file);
    file.attachTo(this);
  }
}
