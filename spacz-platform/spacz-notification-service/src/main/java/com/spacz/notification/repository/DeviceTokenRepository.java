package com.spacz.notification.repository;

import com.spacz.notification.entity.DeviceToken;
import com.spacz.notification.entity.NotificationCategory;
import com.spacz.notification.security.Role;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DeviceTokenRepository extends JpaRepository<DeviceToken, Long> {

    Optional<DeviceToken> findByToken(String token);

    List<DeviceToken> findByUserIdOrderByLastSeenAtDesc(Long userId);

    long countByRole(Role role);

    @Modifying
    @Query("delete from DeviceToken d where d.token = :token and d.userId = :userId")
    int deleteByTokenAndUserId(@Param("token") String token, @Param("userId") Long userId);

    @Modifying
    @Query("delete from DeviceToken d where d.token in :tokens")
    int deleteByTokenIn(@Param("tokens") Collection<String> tokens);

    @Modifying
    @Query("delete from DeviceToken d where d.lastSeenAt < :cutoff")
    int deleteNotSeenSince(@Param("cutoff") Instant cutoff);

    /**
     * Devices of the given roles whose account has not turned push off for the category, in id
     * order after {@code afterId} (keyset paging for large broadcasts).
     */
    @Query("""
            select d from DeviceToken d
            where d.role in :roles and d.id > :afterId
              and not exists (select p.id from NotificationPreference p
                              where p.userId = d.userId and p.category = :category and p.pushEnabled = false)
            order by d.id
            """)
    List<DeviceToken> broadcastTargets(@Param("roles") Collection<Role> roles,
                                       @Param("category") NotificationCategory category,
                                       @Param("afterId") Long afterId, Pageable limit);
}
