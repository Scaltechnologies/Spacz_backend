package com.spacz.studyhall.mapper;

import com.spacz.studyhall.dto.enrollment.EnrollmentResponse;
import com.spacz.studyhall.dto.enrollment.StudentInfo;
import com.spacz.studyhall.entity.Enrollment;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EnrollmentMapper {

    private final StudyHallMapper hallMapper;

    public EnrollmentResponse toResponse(Enrollment e, StudentInfo student) {
        return new EnrollmentResponse(e.getId(), e.getStudyHall().getId(), e.getStudyHall().getName(), student,
                hallMapper.toRef(e.getProgram()), e.getSeat() == null ? null : e.getSeat().getId(),
                e.getSeat() == null ? null : e.getSeat().getSeatNumber(), e.getPlan(), e.getStartDate(),
                e.getEndDate(), e.getStatus(), e.getSource(), e.getNotes(), e.getCreatedAt(), e.getUpdatedAt());
    }

    public static StudentInfo guest(Enrollment e) {
        return new StudentInfo(null, e.getGuestName(), e.getGuestPhone(), e.getGuestEmail(), false);
    }
}
