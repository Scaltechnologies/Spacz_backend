package com.spacz.user.service.impl;

import com.spacz.user.entity.UserDocument;
import com.spacz.user.entity.UserProfile;
import com.spacz.user.entity.UserProgram;
import com.spacz.user.repository.UserDocumentRepository;
import com.spacz.user.repository.UserProfileRepository;
import com.spacz.user.repository.UserProgramRepository;
import com.spacz.user.storage.DocumentStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountErasureServiceImplTest {

    @Mock
    private UserProfileRepository profileRepository;
    @Mock
    private UserDocumentRepository documentRepository;
    @Mock
    private UserProgramRepository programRepository;
    @Mock
    private DocumentStorage documentStorage;
    @InjectMocks
    private AccountErasureServiceImpl service;

    @Test
    void erasesPersonalFieldsDocumentsAndExamChoices() throws IOException {
        UserProfile profile = UserProfile.create(7L, "asha@example.com", "Asha");
        profile.setLastName("Rao");
        profile.setPhone("+919000000001");
        profile.setCity("Hyderabad");
        profile.setDateOfBirth(LocalDate.of(2001, 1, 1));
        UserDocument aadhaar = mock(UserDocument.class);
        when(aadhaar.getStorageKey()).thenReturn("7/aadhaar-front.jpg");
        List<UserDocument> documents = List.of(aadhaar);
        List<UserProgram> programs = List.of(mock(UserProgram.class));
        when(documentRepository.findByUserId(7L)).thenReturn(documents);
        when(programRepository.findByUserIdOrderByCreatedAtDesc(7L)).thenReturn(programs);
        when(profileRepository.findByUserId(7L)).thenReturn(Optional.of(profile));

        service.erase(7L);

        verify(documentRepository).deleteAll(documents);
        verify(programRepository).deleteAll(programs);
        verify(documentStorage).delete("7/aadhaar-front.jpg");
        assertThat(profile.getFirstName()).isEqualTo("Deleted");
        assertThat(profile.getLastName()).isEqualTo("User");
        assertThat(profile.getEmail()).isNull();
        assertThat(profile.getPhone()).isNull();
        assertThat(profile.getCity()).isNull();
        assertThat(profile.getDateOfBirth()).isNull();
        assertThat(profile.getUserId()).isEqualTo(7L);
    }

    @Test
    void aStorageFailureDoesNotUndoTheErasure() throws IOException {
        UserProfile profile = UserProfile.create(8L, null, "Ravi");
        ReflectionTestUtils.setField(profile, "id", 1L);
        UserDocument doc = mock(UserDocument.class);
        when(doc.getStorageKey()).thenReturn("8/x.jpg");
        when(documentRepository.findByUserId(8L)).thenReturn(List.of(doc));
        when(programRepository.findByUserIdOrderByCreatedAtDesc(8L)).thenReturn(List.of());
        when(profileRepository.findByUserId(8L)).thenReturn(Optional.of(profile));
        doThrow(new IOException("disk")).when(documentStorage).delete("8/x.jpg");

        service.erase(8L);

        assertThat(profile.getFirstName()).isEqualTo("Deleted");
    }

    @Test
    void unknownUserIsANoOp() {
        when(documentRepository.findByUserId(99L)).thenReturn(List.of());
        when(programRepository.findByUserIdOrderByCreatedAtDesc(99L)).thenReturn(List.of());
        when(profileRepository.findByUserId(99L)).thenReturn(Optional.empty());

        service.erase(99L);

        verify(profileRepository).findByUserId(99L);
    }
}
