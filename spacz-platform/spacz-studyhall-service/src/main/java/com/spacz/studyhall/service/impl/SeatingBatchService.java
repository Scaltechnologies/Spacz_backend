package com.spacz.studyhall.service.impl;

import com.spacz.studyhall.dto.seat.CreateBlockRequest;
import com.spacz.studyhall.dto.seat.SeatingBatchRequest;
import com.spacz.studyhall.dto.seat.SeatingBatchRequest.Operation;
import com.spacz.studyhall.dto.seat.SeatingBatchResponse;
import com.spacz.studyhall.dto.seat.UpdateBlockRequest;
import com.spacz.studyhall.exception.BusinessRuleException;
import com.spacz.studyhall.exception.SpaczException;
import com.spacz.studyhall.service.SeatingService;
import com.spacz.studyhall.service.support.VendorAccess;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Runs a seating-plan edit as one transaction. Every step goes through {@link SeatingService}, so ownership,
 * booking and grid rules are exactly those of the single routes; those methods join this transaction, and the
 * first failure rolls all of them back. The persistence context is flushed after each step so the database sees
 * the steps in order (Hibernate would otherwise insert before it deletes, e.g. a seat re-added on a freed cell).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeatingBatchService {

    private final SeatingService seatingService;
    private final VendorAccess vendorAccess;
    private final EntityManager entityManager;

    @Transactional
    public SeatingBatchResponse apply(Long vendorId, Long studyHallId, SeatingBatchRequest request) {
        vendorAccess.writableHall(vendorId, studyHallId);
        Map<String, Long> floors = new LinkedHashMap<>();
        Map<String, Long> blocks = new LinkedHashMap<>();
        int step = 0;
        for (Operation op : request.operations()) {
            step++;
            try {
                applyOne(vendorId, studyHallId, op, floors, blocks);
                entityManager.flush();
            } catch (SpaczException ex) {
                throw new SpaczException(ex.getStatus(), ex.getErrorCode(), "Layout not saved, nothing was changed. Step "
                        + step + " (" + op.type() + "): " + ex.getMessage(), ex);
            } catch (PersistenceException | DataAccessException ex) {
                log.warn("Seating batch for study hall {} failed at step {} ({})", studyHallId, step, op.type(), ex);
                throw new SpaczException(HttpStatus.CONFLICT, "LAYOUT_CONFLICT", "Layout not saved, nothing was changed. "
                        + "Step " + step + " (" + op.type() + ") conflicts with the current layout. Reload and try again.", ex);
            }
        }
        log.info("Seating batch applied to study hall {}: {} operations", studyHallId, step);
        return new SeatingBatchResponse(step, floors, blocks);
    }

    private void applyOne(Long vendorId, Long hallId, Operation op, Map<String, Long> floors, Map<String, Long> blocks) {
        switch (op.type()) {
            case DELETE_SEAT -> seatingService.deleteSeat(vendorId, hallId, need(op.seatId(), "seatId"));
            case DELETE_BLOCK -> seatingService.deleteBlock(vendorId, hallId, need(op.blockId(), "blockId"));
            case DELETE_FLOOR -> seatingService.deleteFloor(vendorId, hallId, need(op.floorId(), "floorId"));
            case CREATE_FLOOR -> {
                Long id = seatingService.createFloor(vendorId, hallId, need(op.floor(), "floor")).id();
                remember(floors, op.ref(), id);
            }
            case UPDATE_FLOOR -> seatingService.updateFloor(vendorId, hallId, need(op.floorId(), "floorId"),
                    need(op.floor(), "floor"));
            case CREATE_BLOCK -> {
                CreateBlockRequest b = need(op.createBlock(), "createBlock");
                Long floorId = floorOf(op, floors, b.floorId());
                CreateBlockRequest withFloor = new CreateBlockRequest(b.name(), b.totalRows(), b.totalColumns(),
                        b.seatNumberPrefix(), b.defaultSeatType(), b.gaps(), b.dailyPrice(), b.monthlyPrice(),
                        b.amenityIds(), floorId, b.layoutTemplate());
                Long id = seatingService.createBlock(vendorId, hallId, withFloor).id();
                remember(blocks, op.ref(), id);
            }
            case UPDATE_BLOCK -> {
                UpdateBlockRequest b = need(op.updateBlock(), "updateBlock");
                Long floorId = floorOf(op, floors, b.floorId());
                seatingService.updateBlock(vendorId, hallId, need(op.blockId(), "blockId"),
                        new UpdateBlockRequest(b.name(), b.totalRows(), b.totalColumns(), b.dailyPrice(),
                                b.monthlyPrice(), b.amenityIds(), floorId));
            }
            case UPDATE_LAYOUT -> {
                Long blockId = op.blockId() != null ? op.blockId() : resolve(blocks, op.blockRef(), "blockRef");
                seatingService.updateLayout(vendorId, hallId, need(blockId, "blockId or blockRef"),
                        need(op.layout(), "layout"));
            }
            case CREATE_SEAT -> seatingService.createSeat(vendorId, hallId, need(op.seat(), "seat"));
        }
    }

    private static Long floorOf(Operation op, Map<String, Long> floors, Long fallback) {
        if (op.floorId() != null) {
            return op.floorId();
        }
        return op.floorRef() != null ? resolve(floors, op.floorRef(), "floorRef") : fallback;
    }

    private static Long resolve(Map<String, Long> created, String ref, String field) {
        if (ref == null) {
            return null;
        }
        Long id = created.get(ref);
        if (id == null) {
            throw new BusinessRuleException("INVALID_BATCH", field + " '" + ref + "' was not created earlier in this batch");
        }
        return id;
    }

    private static void remember(Map<String, Long> created, String ref, Long id) {
        if (ref != null && created.putIfAbsent(ref, id) != null) {
            throw new BusinessRuleException("INVALID_BATCH", "ref '" + ref + "' is used twice");
        }
    }

    private static <T> T need(T value, String field) {
        if (value == null) {
            throw new BusinessRuleException("INVALID_BATCH", field + " is required for this operation");
        }
        return value;
    }
}
