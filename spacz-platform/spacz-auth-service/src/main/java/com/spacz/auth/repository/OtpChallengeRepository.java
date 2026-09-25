package com.spacz.auth.repository;

import com.spacz.auth.entity.OtpChallenge;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface OtpChallengeRepository extends JpaRepository<OtpChallenge, Long> {

    /** The newest unconsumed challenge, locked so parallel verifications count attempts correctly. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from OtpChallenge c where c.phone = :phone and c.consumedAt is null order by c.createdAt desc, c.id desc limit 1")
    Optional<OtpChallenge> findLatestOpenForUpdate(@Param("phone") String phone);

    Optional<OtpChallenge> findFirstByPhoneOrderByCreatedAtDescIdDesc(String phone);

    long countByPhoneAndCreatedAtAfter(String phone, Instant since);

    @Modifying
    @Query("update OtpChallenge c set c.consumedAt = :now where c.phone = :phone and c.consumedAt is null")
    int consumeAllOpen(@Param("phone") String phone, @Param("now") Instant now);

    @Modifying
    @Query("delete from OtpChallenge c where c.createdAt < :cutoff")
    int deleteCreatedBefore(@Param("cutoff") Instant cutoff);
}
