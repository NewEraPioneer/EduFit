package vn.edufit.profile.infra.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.edufit.profile.infra.persistence.entity.GoalTagRule;

@Repository
public interface GoalTagRuleRepository extends JpaRepository<GoalTagRule, Integer> {
}
