package com.hotelpms.stay.repository;

import com.hotelpms.stay.domain.AlloggiatiTipdoc;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for {@link AlloggiatiTipdoc} lookup records.
 */
@Repository
public interface AlloggiatiTipdocRepository extends JpaRepository<AlloggiatiTipdoc, String> {
}
