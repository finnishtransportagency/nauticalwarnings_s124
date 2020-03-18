package fi.liikennevirasto.winvis.nauticalwarnings;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

import java.time.LocalDateTime;
import java.util.List;

public interface NauticalWarningsRepository extends JpaRepository<NauticalWarningEntity, Long> {

    @Modifying
    void deleteByExpiredTimeBefore(LocalDateTime expiryDate);

    List<NauticalWarningEntity> findAllByExpiredTimeIsNull();

    List<NauticalWarningEntity> findAllByExpiredTimeIsNotNull();

    List<NauticalWarningEntity> findAllBySmaDeliveryTimeIsNull();

}
