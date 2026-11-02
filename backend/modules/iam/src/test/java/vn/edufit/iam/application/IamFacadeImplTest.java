package vn.edufit.iam.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import vn.edufit.iam.api.AccountRole;
import vn.edufit.iam.api.AccountStatus;
import vn.edufit.iam.infra.persistence.Account;
import vn.edufit.iam.infra.persistence.AccountRepository;

class IamFacadeImplTest {

  private AccountRepository accountRepository;
  private IamFacadeImpl facade;

  @BeforeEach
  void setUp() {
    accountRepository = mock(AccountRepository.class);
    facade = new IamFacadeImpl(accountRepository);
  }

  @Test
  void findUserSummaryByIdReturnsSummaryWhenFound() {
    UUID id = UUID.randomUUID();
    Account account = Account.create("test@edufit.vn", "hash", "Nguyễn Văn A", AccountRole.STUDENT);
    ReflectionTestUtils.setField(account, "id", id);

    when(accountRepository.findById(id)).thenReturn(Optional.of(account));

    var summary = facade.findUserSummaryById(id);
    assertTrue(summary.isPresent());
    assertEquals(id, summary.get().id());
    assertEquals("test@edufit.vn", summary.get().email());
    assertEquals("Nguyễn Văn A", summary.get().fullName());
    assertEquals(AccountRole.STUDENT, summary.get().role());
    assertEquals(AccountStatus.ACTIVE, summary.get().status());
  }

  @Test
  void findUserSummaryByIdReturnsEmptyWhenNullOrNotFound() {
    assertTrue(facade.findUserSummaryById(null).isEmpty());
    UUID id = UUID.randomUUID();
    when(accountRepository.findById(id)).thenReturn(Optional.empty());
    assertTrue(facade.findUserSummaryById(id).isEmpty());
  }

  @Test
  void findUserSummaryByEmailNormalizesAndReturnsSummary() {
    Account account = Account.create("parent@edufit.vn", "hash", "Trần Thị B", AccountRole.PARENT);
    ReflectionTestUtils.setField(account, "id", UUID.randomUUID());

    when(accountRepository.findByEmail("parent@edufit.vn")).thenReturn(Optional.of(account));

    var summary = facade.findUserSummaryByEmail("  PARENT@edufit.vn  ");
    assertTrue(summary.isPresent());
    assertEquals("parent@edufit.vn", summary.get().email());
    assertEquals(AccountRole.PARENT, summary.get().role());
  }

  @Test
  void findUserSummaryByEmailReturnsEmptyWhenBlankOrNotFound() {
    assertTrue(facade.findUserSummaryByEmail(null).isEmpty());
    assertTrue(facade.findUserSummaryByEmail("   ").isEmpty());
    when(accountRepository.findByEmail("unknown@edufit.vn")).thenReturn(Optional.empty());
    assertTrue(facade.findUserSummaryByEmail("unknown@edufit.vn").isEmpty());
  }

  @Test
  void findUserSummariesByIdsReturnsMappedSummaries() {
    UUID id1 = UUID.randomUUID(), id2 = UUID.randomUUID();
    Account acc1 = Account.create("student@edufit.vn", "hash1", "Student One", AccountRole.STUDENT);
    ReflectionTestUtils.setField(acc1, "id", id1);

    Account acc2 = Account.create("parent@edufit.vn", "hash2", "Parent Two", AccountRole.PARENT);
    ReflectionTestUtils.setField(acc2, "id", id2);

    when(accountRepository.findAllById(List.of(id1, id2))).thenReturn(List.of(acc1, acc2));

    var map = facade.findUserSummariesByIds(List.of(id1, id2));
    assertEquals(2, map.size());
    assertTrue(map.containsKey(id1));
    assertEquals("student@edufit.vn", map.get(id1).email());
    assertEquals(AccountRole.STUDENT, map.get(id1).role());

    assertTrue(map.containsKey(id2));
    assertEquals("parent@edufit.vn", map.get(id2).email());
    assertEquals(AccountRole.PARENT, map.get(id2).role());
  }

  @Test
  void findUserSummariesByIdsReturnsEmptyMapWhenNullOrEmpty() {
    assertTrue(facade.findUserSummariesByIds(null).isEmpty());
    assertTrue(facade.findUserSummariesByIds(List.of()).isEmpty());
    verifyNoInteractions(accountRepository);
  }

  @Test
  void existsByIdReturnsTrueOrFalse() {
    assertFalse(facade.existsById(null));
    UUID id = UUID.randomUUID();
    when(accountRepository.existsById(id)).thenReturn(true);
    assertTrue(facade.existsById(id));
  }

  @Test
  void countTotalUsersReturnsRepositoryCount() {
    when(accountRepository.count()).thenReturn(42L);
    assertEquals(42L, facade.countTotalUsers());
    verify(accountRepository).count();
  }

  @Test
  void countUsersByRoleReturnsRoleCount() {
    when(accountRepository.countByRole(AccountRole.TUTOR)).thenReturn(10L);
    assertEquals(10L, facade.countUsersByRole(AccountRole.TUTOR));
    assertEquals(0L, facade.countUsersByRole(null));
    verify(accountRepository).countByRole(AccountRole.TUTOR);
  }
}
