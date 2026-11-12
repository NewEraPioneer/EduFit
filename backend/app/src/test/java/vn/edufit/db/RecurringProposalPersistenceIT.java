package vn.edufit.db;

import static org.junit.jupiter.api.Assertions.*;
import java.time.*;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import vn.edufit.scheduling.domain.model.*;
import vn.edufit.scheduling.infra.persistence.adapter.JdbcRecurringProposalRepository;

@Testcontainers
class RecurringProposalPersistenceIT {
  @Container static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");
  static DriverManagerDataSource dataSource;
  static JdbcTemplate db;
  static TransactionTemplate tx;
  static final UUID CLASS = UUID.fromString("77777777-7777-7777-7777-000000000001");

  @BeforeAll static void migrate() {
    Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
        .locations("classpath:db/migration", "classpath:db/seed").load().migrate();
    dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    db = new JdbcTemplate(dataSource);
    tx = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
  }

  @Test void migrationAndRepositoryPersistProposalAcrossRecreation() {
    Instant now = Instant.now();
    var proposal = RecurringRescheduleProposal.create(UUID.randomUUID(), CLASS,
        UUID.fromString("22222222-2222-2222-2222-000000000001"),
        UUID.fromString("11111111-1111-1111-1111-000000000001"),
        UUID.fromString("22222222-2222-2222-2222-222222222222"),
        LocalDate.now().plusDays(3), LocalDate.now().plusDays(31), List.of("MONDAY 18:00-19:30"),
        SessionMode.ONLINE, "https://example.com/demo", "Đổi lịch bảo vệ", now.plusSeconds(3600), now);
    var first = new JdbcRecurringProposalRepository(db);
    tx.execute(status -> { first.lockClass(CLASS); first.save(proposal); return null; });
    var restarted = new JdbcRecurringProposalRepository(new JdbcTemplate(dataSource));
    var loaded = restarted.findById(proposal.getProposalId()).orElseThrow();
    assertEquals(proposal.getNewWeeklySlots(), loaded.getNewWeeklySlots());
    assertEquals(proposal.getMessage(), loaded.getMessage());
    tx.execute(status -> {
      restarted.lockClass(CLASS);
      var locked = restarted.findByIdForUpdate(proposal.getProposalId()).orElseThrow();
      locked.accept(UUID.fromString("11111111-1111-1111-1111-111111111111"), now);
      restarted.save(locked);
      return null;
    });
    assertEquals(ProposalStatus.ACCEPTED, first.findById(proposal.getProposalId()).orElseThrow().getStatus());
    assertTrue(first.findPendingByClassId(CLASS).isEmpty());
  }
}
