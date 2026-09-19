package com.bytevault.user;

import com.bytevault.user.dto.UpdateProfileRequest;
import com.bytevault.user.dto.UserProfileResponse;
import com.bytevault.user.entity.UserProfile;
import com.bytevault.user.repository.UserProfileRepository;
import com.bytevault.user.service.UserProfileService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class UserProfileServiceTest {

    @Mock
    private UserProfileRepository profileRepository;

    @InjectMocks
    private UserProfileService profileService;

    @Test
    @DisplayName("Retrieve user profile when exists")
    void testGetProfile_Existing() {
        UUID userId = UUID.randomUUID();
        UserProfile profile = UserProfile.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .email("john@bytevault.com")
                .firstName("John")
                .lastName("Doe")
                .city("San Francisco")
                .build();

        when(profileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));

        UserProfileResponse response = profileService.getProfile(userId);

        assertNotNull(response);
        assertEquals(userId, response.getUserId());
        assertEquals("John", response.getFirstName());
        assertEquals("San Francisco", response.getCity());
    }

    @Test
    @DisplayName("Update user profile details")
    void testUpdateProfile() {
        UUID userId = UUID.randomUUID();
        UserProfile existing = UserProfile.builder()
                .userId(userId)
                .email("user@bytevault.com")
                .build();

        when(profileRepository.findByUserId(userId)).thenReturn(Optional.of(existing));
        when(profileRepository.save(any(UserProfile.class))).thenAnswer(i -> i.getArgument(0));

        UpdateProfileRequest updateReq = UpdateProfileRequest.builder()
                .firstName("Jane")
                .lastName("Smith")
                .phone("+1234567890")
                .address("100 Market St")
                .city("San Francisco")
                .country("USA")
                .build();

        UserProfileResponse response = profileService.updateProfile(userId, updateReq);

        assertNotNull(response);
        assertEquals("Jane", response.getFirstName());
        assertEquals("Smith", response.getLastName());
        assertEquals("100 Market St", response.getAddress());
    }

    @Test
    @DisplayName("Create user profile from UserRegisteredEvent is idempotent when profile already exists")
    void testCreateProfileFromEvent_AlreadyExists_Idempotent() {
        UUID userId = UUID.randomUUID();
        UserProfile existing = UserProfile.builder()
                .userId(userId)
                .email("alex@bytevault.com")
                .build();
        when(profileRepository.findByUserId(userId)).thenReturn(Optional.of(existing));

        profileService.createProfileFromEvent(userId, "alex@bytevault.com", "Alex Mercer");

        verify(profileRepository, never()).save(any(UserProfile.class));
    }

    @Test
    @DisplayName("Update user profile with partial fields preserves existing values")
    void testUpdateProfile_PartialFields() {
        UUID userId = UUID.randomUUID();
        UserProfile existing = UserProfile.builder()
                .userId(userId)
                .email("user@bytevault.com")
                .firstName("ExistingFirst")
                .lastName("ExistingLast")
                .city("OldCity")
                .build();

        when(profileRepository.findByUserId(userId)).thenReturn(Optional.of(existing));
        when(profileRepository.save(any(UserProfile.class))).thenAnswer(i -> i.getArgument(0));

        UpdateProfileRequest partialReq = UpdateProfileRequest.builder()
                .city("NewCity")
                .build();

        UserProfileResponse response = profileService.updateProfile(userId, partialReq);

        assertNotNull(response);
        assertEquals("ExistingFirst", response.getFirstName());
        assertEquals("ExistingLast", response.getLastName());
        assertEquals("NewCity", response.getCity());
    }
}
