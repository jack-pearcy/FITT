package com.fitt.identity.service;

import com.fitt.identity.domain.UserEntity;
import com.fitt.identity.repository.RefreshTokenRepository;
import com.fitt.identity.repository.UserBiometricsRepository;
import com.fitt.identity.repository.UserGoalRepository;
import com.fitt.identity.repository.UserRepository;
import com.fitt.identity.service.exception.IncorrectPasswordException;
import com.fitt.shared.account.UserDataEraser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);

    private final UserRepository userRepository;
    private final UserBiometricsRepository biometricsRepository;
    private final UserGoalRepository goalRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final List<UserDataEraser> erasers;

    public AccountService(
            UserRepository userRepository,
            UserBiometricsRepository biometricsRepository,
            UserGoalRepository goalRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            List<UserDataEraser> erasers
    ) {
        this.userRepository = userRepository;
        this.biometricsRepository = biometricsRepository;
        this.goalRepository = goalRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.erasers = erasers;
    }

    // Removes the account and every row stored for it. Permanent: there is no
    // soft delete and no grace period, so the email can be registered again at once.
    // One transaction, so the account is either whole or gone.
    @Transactional
    public void deleteAccount(Long userId, String password) {
        Optional<UserEntity> found = userRepository.findById(userId);
        if (found.isEmpty()) {
            // A second request that arrived while the first was deleting. The work is done.
            return;
        }

        UserEntity user = found.get();
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new IncorrectPasswordException();
        }

        // The other contexts first, then identity's own rows, then the user row last
        // so nothing is ever left pointing at an account that is not there.
        erasers.forEach(eraser -> eraser.eraseUserData(userId));
        refreshTokenRepository.deleteAllForUser(userId);
        biometricsRepository.deleteAllForUser(userId);
        goalRepository.deleteAllForUser(userId);
        userRepository.deleteById(userId);

        // The id only. The email is personal data and the account is gone.
        log.info("Deleted account {}", userId);
    }
}
