package com.spacz.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;

/**
 * An exam / course the student is preparing for. {@code programId} references studyhall-service's
 * catalog (validated when set); code and name are snapshots so reads never need a remote call.
 */
@Entity
@Table(name = "user_programs")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserProgram {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Setter(AccessLevel.NONE)
    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "program_id", nullable = false)
    private Long programId;

    @Column(name = "program_code", length = 40)
    private String programCode;

    @Column(name = "program_name", nullable = false, length = 120)
    private String programName;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "target_date")
    private LocalDate targetDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PreparationStatus status;

    @Column(length = 500)
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static UserProgram create(Long userId, Long programId, String programCode, String programName) {
        UserProgram program = new UserProgram();
        program.userId = userId;
        program.programId = programId;
        program.programCode = programCode;
        program.programName = programName;
        program.status = PreparationStatus.PLANNED;
        return program;
    }
}
