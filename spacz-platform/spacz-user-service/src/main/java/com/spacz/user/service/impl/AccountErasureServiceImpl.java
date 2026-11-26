package com.spacz.user.service.impl;

import com.spacz.user.entity.UserDocument;
import com.spacz.user.repository.UserDocumentRepository;
import com.spacz.user.repository.UserProfileRepository;
import com.spacz.user.repository.UserProgramRepository;
import com.spacz.user.service.AccountErasureService;
import com.spacz.user.storage.DocumentStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountErasureServiceImpl implements AccountErasureService {

    static final String ERASED_FIRST_NAME = "Deleted";
    static final String ERASED_LAST_NAME = "User";

    private final UserProfileRepository profileRepository;
    private final UserDocumentRepository documentRepository;
    private final UserProgramRepository programRepository;
    private final DocumentStorage documentStorage;

    @Override
    @Transactional
    public void erase(Long userId) {
        List<UserDocument> documents = documentRepository.findByUserId(userId);
        List<String> storageKeys = documents.stream().map(UserDocument::getStorageKey).toList();
        documentRepository.deleteAll(documents);
        programRepository.deleteAll(programRepository.findByUserIdOrderByCreatedAtDesc(userId));

        profileRepository.findByUserId(userId).ifPresent(profile -> {
            profile.setFirstName(ERASED_FIRST_NAME);
            profile.setLastName(ERASED_LAST_NAME);
            profile.setEmail(null);
            profile.setPhone(null);
            profile.setProfileImageUrl(null);
            profile.setDateOfBirth(null);
            profile.setCity(null);
            profile.setState(null);
            profile.setBio(null);
            profile.setEducationLevel(null);
            profile.setPreferredCity(null);
            profile.setPreferredStudySlot(null);
            profile.setDailyStudyHoursGoal(null);
        });

        // Files go only once the rows are gone for good, so a rollback never leaves a row without its file.
        if (!storageKeys.isEmpty() && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    storageKeys.forEach(key -> deleteQuietly(userId, key));
                }
            });
        } else {
            storageKeys.forEach(key -> deleteQuietly(userId, key));
        }
        log.info("Erased personal data of user {} ({} document(s))", userId, storageKeys.size());
    }

    private void deleteQuietly(Long userId, String key) {
        try {
            documentStorage.delete(key);
        } catch (IOException e) {
            log.warn("Could not delete a stored document of erased user {}: {}", userId, e.getMessage());
        }
    }
}
