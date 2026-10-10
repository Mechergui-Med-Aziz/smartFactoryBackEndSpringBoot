package com.smartfactory.config;

import com.smartfactory.entity.Machine;
import com.smartfactory.entity.MachineCharacteristic;
import com.smartfactory.entity.MachineStatus;
import com.smartfactory.entity.User;
import com.smartfactory.repository.MachineRepository;
import com.smartfactory.repository.UserRepository;
import com.smartfactory.security.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final UserRepository userRepository;
    private final MachineRepository machineRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(UserRepository userRepository,
                           MachineRepository machineRepository,
                           PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.machineRepository = machineRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        User admin = userRepository.findByEmail("admin@smartfactory.com").orElse(null);
        if (admin == null) {
            log.info("No admin user found. Creating default admin user...");
            admin = new User(
                    "Admin",
                    "SmartFactory",
                    "admin@smartfactory.com",
                    passwordEncoder.encode("adminpassword123"),
                    Role.ADMIN,
                    "ACTIVE"
            );
            admin.setEmailVerified(true);
            userRepository.save(admin);
            log.info("Default admin created: admin@smartfactory.com");
        } else if (!passwordEncoder.matches("adminpassword123", admin.getPassword())) {
            admin.setPassword(passwordEncoder.encode("adminpassword123"));
            admin.setStatus("ACTIVE");
            admin.setEmailVerified(true);
            userRepository.save(admin);
            log.info("Admin user updated with default credentials: admin@smartfactory.com");
        }

        if (!machineRepository.findByCode("CNC-024").isPresent()) {
            log.info("Creating default machine CNC-024...");
            Machine defaultMachine = new Machine(
                    "Centre d'usinage CNC-024",
                    "CNC-024",
                    "CNC Milling",
                    null,
                    MachineStatus.RUNNING,
                    "Fraiseuse à commande numérique de précision",
                    List.of(
                            new MachineCharacteristic("Puissance", "15kW", "Electrique"),
                            new MachineCharacteristic("Vitesse rotation max", "24000 RPM", "Mecanique")
                    )
            );
            machineRepository.save(defaultMachine);
            log.info("Default machine created: CNC-024");
        }
    }
}
