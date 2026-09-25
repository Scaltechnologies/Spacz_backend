package com.spacz.user.service.impl;

import com.spacz.user.client.StudyHallServiceClient;
import com.spacz.user.client.dto.ProgramDto;
import com.spacz.user.dto.UserProgramRequest;
import com.spacz.user.dto.UserProgramResponse;
import com.spacz.user.entity.ActivityType;
import com.spacz.user.entity.PreparationStatus;
import com.spacz.user.entity.UserProgram;
import com.spacz.user.exception.BusinessRuleException;
import com.spacz.user.exception.DuplicateResourceException;
import com.spacz.user.exception.ResourceNotFoundException;
import com.spacz.user.mapper.UserMapper;
import com.spacz.user.repository.UserProfileRepository;
import com.spacz.user.repository.UserProgramRepository;
import com.spacz.user.service.ActivityService;
import com.spacz.user.service.UserProgramService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class UserProgramServiceImpl implements UserProgramService {

    private final UserProgramRepository programRepository;
    private final UserProfileRepository profileRepository;
    private final StudyHallServiceClient studyHallClient;
    private final ActivityService activityService;
    private final UserMapper mapper;

    @Override
    @Transactional(readOnly = true)
    public List<UserProgramResponse> list(Long userId) {
        requireProfile(userId);
        return programRepository.findByUserIdOrderByCreatedAtDesc(userId).stream().map(mapper::toResponse).toList();
    }

    @Override
    @Transactional
    public UserProgramResponse add(Long userId, UserProgramRequest request) {
        requireProfile(userId);
        if (programRepository.existsByUserIdAndProgramId(userId, request.programId())) {
            throw new DuplicateResourceException("You are already preparing for this program");
        }
        ProgramDto program = activeProgram(request.programId());
        UserProgram userProgram = UserProgram.create(userId, program.id(), program.code(), program.name());
        apply(userProgram, request);
        UserProgram saved = programRepository.saveAndFlush(userProgram);
        activityService.record(userId, ActivityType.PROGRAM_ADDED, "Started preparing for " + program.name(),
                "USER_PROGRAM", saved.getId());
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional
    public UserProgramResponse update(Long userId, Long userProgramId, UserProgramRequest request) {
        UserProgram userProgram = find(userId, userProgramId);
        if (!userProgram.getProgramId().equals(request.programId())) {
            if (programRepository.existsByUserIdAndProgramId(userId, request.programId())) {
                throw new DuplicateResourceException("You are already preparing for this program");
            }
            ProgramDto program = activeProgram(request.programId());
            userProgram.setProgramId(program.id());
            userProgram.setProgramCode(program.code());
            userProgram.setProgramName(program.name());
        }
        apply(userProgram, request);
        activityService.record(userId, ActivityType.PROGRAM_UPDATED,
                "Updated preparation for " + userProgram.getProgramName(), "USER_PROGRAM", userProgramId);
        return mapper.toResponse(programRepository.saveAndFlush(userProgram));
    }

    @Override
    @Transactional
    public void remove(Long userId, Long userProgramId) {
        UserProgram userProgram = find(userId, userProgramId);
        programRepository.delete(userProgram);
        activityService.record(userId, ActivityType.PROGRAM_REMOVED,
                "Removed preparation for " + userProgram.getProgramName(), "USER_PROGRAM", userProgramId);
    }

    private ProgramDto activeProgram(Long programId) {
        ProgramDto program = studyHallClient.findPrograms(Set.of(programId)).stream()
                .filter(p -> p.id().equals(programId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Program", programId));
        if (!program.active()) {
            throw new BusinessRuleException("Program " + program.name() + " is no longer offered");
        }
        return program;
    }

    private static void apply(UserProgram userProgram, UserProgramRequest request) {
        userProgram.setStartDate(request.startDate());
        userProgram.setTargetDate(request.targetDate());
        userProgram.setStatus(request.status() != null ? request.status() : PreparationStatus.PLANNED);
        userProgram.setNotes(request.notes() == null || request.notes().isBlank() ? null : request.notes().trim());
    }

    private UserProgram find(Long userId, Long userProgramId) {
        return programRepository.findByIdAndUserId(userProgramId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Program selection", userProgramId));
    }

    private void requireProfile(Long userId) {
        if (!profileRepository.existsByUserId(userId)) {
            throw new ResourceNotFoundException("User profile", userId);
        }
    }
}
