package com.spacz.studyhall.repository;

import com.spacz.studyhall.entity.StudyHall;
import com.spacz.studyhall.entity.StudyHallStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface StudyHallRepository extends JpaRepository<StudyHall, Long>, JpaSpecificationExecutor<StudyHall> {

    @EntityGraph(attributePaths = "vendor")
    @Query("select h from StudyHall h where h.id = :id and h.vendor.vendorId = :vendorId")
    Optional<StudyHall> findOwned(@Param("id") Long id, @Param("vendorId") Long vendorId);

    @EntityGraph(attributePaths = "vendor")
    @Query("select h from StudyHall h where h.id = :id")
    Optional<StudyHall> findWithVendor(@Param("id") Long id);

    @EntityGraph(attributePaths = "vendor")
    Page<StudyHall> findByVendor_VendorId(Long vendorId, Pageable pageable);

    @EntityGraph(attributePaths = "vendor")
    Page<StudyHall> findByVendor_VendorIdAndStatus(Long vendorId, StudyHallStatus status, Pageable pageable);

    List<StudyHall> findByVendor_IdOrderByIdAsc(Long vendorProfileId);

    List<StudyHall> findByVendor_IdAndStatus(Long vendorProfileId, StudyHallStatus status);

    long countByStatus(StudyHallStatus status);

    long countByVendor_Id(Long vendorProfileId);

    long countByVendor_IdAndStatus(Long vendorProfileId, StudyHallStatus status);

    @Query("select h.vendor.id, count(h) from StudyHall h where h.vendor.id in :vendorIds group by h.vendor.id")
    List<Object[]> countByVendorIds(@Param("vendorIds") Collection<Long> vendorProfileIds);

    @Query("select h.vendor.id, count(h) from StudyHall h where h.vendor.id in :vendorIds and h.status = :status group by h.vendor.id")
    List<Object[]> countByVendorIdsAndStatus(@Param("vendorIds") Collection<Long> vendorProfileIds,
                                             @Param("status") StudyHallStatus status);
}
