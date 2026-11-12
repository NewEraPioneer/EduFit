package vn.edufit.scheduling.infra.persistence.adapter;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import vn.edufit.scheduling.domain.model.*;
import vn.edufit.scheduling.domain.repository.RecurringProposalRepository;

@Repository
@RequiredArgsConstructor
public class JdbcRecurringProposalRepository implements RecurringProposalRepository {
  private final JdbcTemplate db;

  public void lockClass(UUID id) {
    // Use the same participant lock order as connection requests before locking the class row.
    db.query("select student_id,tutor_id from class where class_id=?", r -> {
      if (r.next()) {
        for (String key : List.of("connection:student:" + r.getObject("student_id"),
            "connection:tutor:" + r.getObject("tutor_id"))) {
          db.queryForObject("select pg_advisory_xact_lock(hashtextextended(cast(? as text),0))", String.class, key);
        }
      }
      return null;
    }, id);
    db.queryForObject("select class_id from class where class_id=? for update", UUID.class, id);
  }

  public Optional<RecurringRescheduleProposal> findById(UUID id) {
    return db.query("select * from recurring_schedule_proposal where proposal_id=?", this::map, id).stream().findFirst();
  }

  public Optional<RecurringRescheduleProposal> findByIdForUpdate(UUID id) {
    return db.query("select * from recurring_schedule_proposal where proposal_id=? for update", this::map, id).stream().findFirst();
  }

  public List<RecurringRescheduleProposal> findPendingByClassId(UUID id) {
    return db.query("select * from recurring_schedule_proposal where class_id=? and status='PENDING' order by created_at", this::map, id);
  }

  public void save(RecurringRescheduleProposal p) {
    db.update("""
        insert into recurring_schedule_proposal
          (proposal_id,class_id,tutor_id,student_id,proposed_by,effective_from,effective_to,
           weekly_slots,mode,place_or_link,message,status,responded_by,responded_at,response_reason,expires_at,created_at)
        values (?,?,?,?,?,?,?,string_to_array(?,chr(10)),?,?,?,?,?,?,?,?,?)
        on conflict (proposal_id) do update set status=excluded.status,responded_by=excluded.responded_by,
          responded_at=excluded.responded_at,response_reason=excluded.response_reason
        """, p.getProposalId(), p.getClassId(), p.getTutorId(), p.getStudentId(), p.getProposedBy(),
        java.sql.Date.valueOf(p.getEffectiveFrom()), java.sql.Date.valueOf(p.getEffectiveTo()),
        String.join("\n", p.getNewWeeklySlots()), p.getDefaultMode().name(), p.getPlaceOrLink(), p.getMessage(),
        p.getStatus().name(), p.getRespondedBy(), timestamp(p.getRespondedAt()), p.getResponseReason(),
        timestamp(p.getExpiresAt()), timestamp(p.getCreatedAt()));
  }

  private Timestamp timestamp(Instant at) { return at == null ? null : Timestamp.from(at); }
  private Instant instant(ResultSet r, String col) throws SQLException {
    Timestamp at = r.getTimestamp(col);
    return at == null ? null : at.toInstant();
  }
  private RecurringRescheduleProposal map(ResultSet r, int row) throws SQLException {
    return new RecurringRescheduleProposal(r.getObject("proposal_id", UUID.class), r.getObject("class_id", UUID.class),
        r.getObject("tutor_id", UUID.class), r.getObject("student_id", UUID.class), r.getObject("proposed_by", UUID.class),
        r.getDate("effective_from").toLocalDate(), r.getDate("effective_to").toLocalDate(),
        Arrays.asList((String[]) r.getArray("weekly_slots").getArray()), SessionMode.valueOf(r.getString("mode")),
        r.getString("place_or_link"), r.getString("message"), ProposalStatus.valueOf(r.getString("status")),
        r.getObject("responded_by", UUID.class), instant(r,"responded_at"), r.getString("response_reason"),
        instant(r,"expires_at"), instant(r,"created_at"));
  }
}
