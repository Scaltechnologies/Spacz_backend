package com.spacz.notification.service;

import com.spacz.notification.dto.PreferenceResponse;
import com.spacz.notification.dto.UpdatePreferencesRequest;
import com.spacz.notification.entity.NotificationCategory;
import com.spacz.notification.entity.NotificationPreference;
import com.spacz.notification.repository.NotificationPreferenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PreferenceService {

    private final NotificationPreferenceRepository preferenceRepository;

    /** Every category, with push on unless the account turned it off. */
    @Transactional(readOnly = true)
    public List<PreferenceResponse> list(Long userId) {
        Map<NotificationCategory, NotificationPreference> stored = stored(userId);
        return Arrays.stream(NotificationCategory.values())
                .map(category -> new PreferenceResponse(category, category.getLabel(),
                        !stored.containsKey(category) || stored.get(category).isPushEnabled()))
                .toList();
    }

    @Transactional
    public List<PreferenceResponse> update(Long userId, UpdatePreferencesRequest request) {
        Map<NotificationCategory, NotificationPreference> stored = stored(userId);
        request.preferences().forEach(item -> {
            NotificationPreference preference = stored.computeIfAbsent(item.category(),
                    category -> NotificationPreference.of(userId, category));
            preference.setPushEnabled(item.pushEnabled());
        });
        preferenceRepository.saveAll(stored.values());
        return list(userId);
    }

    private Map<NotificationCategory, NotificationPreference> stored(Long userId) {
        Map<NotificationCategory, NotificationPreference> map = new EnumMap<>(NotificationCategory.class);
        preferenceRepository.findByUserId(userId).forEach(p -> map.put(p.getCategory(), p));
        return map;
    }
}
