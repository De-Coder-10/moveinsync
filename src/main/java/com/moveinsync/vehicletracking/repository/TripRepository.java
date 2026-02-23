package com.moveinsync.vehicletracking.repository;

import com.moveinsync.vehicletracking.entity.Trip;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository interface for Trip entity
 */
@Repository
public interface TripRepository extends JpaRepository<Trip, Long> {

    /**
     * EC6 — Duplicate Event / Race Condition Protection
     *
     * Acquires a PESSIMISTIC_WRITE (SELECT FOR UPDATE) lock on the Trip row.
     * Used in LocationService.processLocationUpdate() to ensure only ONE
     * concurrent thread can evaluate & close a given trip at a time.
     *
     * Without this lock two async GPS pings arriving simultaneously could both
     * read trip.status == "IN_PROGRESS" and both trigger TRIP_COMPLETED.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM Trip t WHERE t.id = :id")
    Optional<Trip> findByIdForUpdate(@Param("id") Long id);
}
