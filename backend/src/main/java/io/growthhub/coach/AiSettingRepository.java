package io.growthhub.coach;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
public interface AiSettingRepository extends JpaRepository<AiSetting,Integer> {
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select s from AiSetting s where s.id=1") AiSetting mutex();
}
