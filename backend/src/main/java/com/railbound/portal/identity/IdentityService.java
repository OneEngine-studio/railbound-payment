package com.railbound.portal.identity;

import com.railbound.portal.common.ResourceNotFoundException;
import com.railbound.portal.security.JwtTokenProvider;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class IdentityService {

    private final UserRepository userRepository;
    private final JwtTokenProvider tokenProvider;

    public IdentityService(UserRepository userRepository, JwtTokenProvider tokenProvider) {
        this.userRepository = userRepository;
        this.tokenProvider = tokenProvider;
    }

    public User getOrCreateUser(String nakamaUserId, String displayName, String role) {
        return userRepository.findByNakamaUserId(nakamaUserId)
                .orElseGet(() -> {
                    String userId = "usr_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
                    User user = new User(userId, nakamaUserId, displayName, role != null ? role : "PLAYER");
                    return userRepository.save(user);
                });
    }

    public User getUserByUserId(String userId) {
        return userRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found for ID: " + userId));
    }

    public String generateTokenForUser(User user) {
        return tokenProvider.generateToken(
                user.getUserId(),
                user.getNakamaUserId(),
                user.getRole(),
                user.getDisplayName()
        );
    }
}
