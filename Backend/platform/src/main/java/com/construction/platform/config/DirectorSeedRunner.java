package com.construction.platform.config;

import com.construction.platform.domain.User;
import com.construction.platform.domain.UserRole;
import com.construction.platform.repository.UserRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile({"dev", "docker"})
public class DirectorSeedRunner implements ApplicationRunner {

    private static final String DIRECTOR_EMAIL = "director@construction.local";
    private static final String DIRECTOR_PASSWORD = "Director123!";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DirectorSeedRunner(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.existsByEmailIgnoreCase(DIRECTOR_EMAIL)) {
            return;
        }

        User director = new User(
                DIRECTOR_EMAIL,
                passwordEncoder.encode(DIRECTOR_PASSWORD),
                "Default Director",
                UserRole.DIRECTOR
        );
        userRepository.save(director);
    }
}
