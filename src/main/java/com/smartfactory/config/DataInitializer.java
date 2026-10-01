package com.smartfactory.config;

import com.smartfactory.entity.User;
import com.smartfactory.repository.UserRepository;
import com.smartfactory.security.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        if (userRepository.count() == 0) {
            log.info("No users found. Creating default admin user...");
            User admin = new User(
                    "Admin",
                    "SmartFactory",
                    "admin@smartfactory.com",
                    passwordEncoder.encode("adminpassword123"),
                    Role.ADMIN,
                    "ACTIVE"
            );
            userRepository.save(admin);
            log.info("Default admin created: admin@smartfactory.com");
        }
    }
}
