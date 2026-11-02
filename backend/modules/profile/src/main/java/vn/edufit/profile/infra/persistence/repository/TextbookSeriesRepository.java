package vn.edufit.profile.infra.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.edufit.profile.infra.persistence.entity.TextbookSeries;

@Repository
public interface TextbookSeriesRepository extends JpaRepository<TextbookSeries, Integer> {
}
