package com.construction.platform.config;

import com.construction.platform.domain.Project;
import com.construction.platform.domain.ProjectMember;
import com.construction.platform.domain.ProjectStatus;
import com.construction.platform.domain.User;
import com.construction.platform.domain.UserRole;
import com.construction.platform.repository.ProjectMemberRepository;
import com.construction.platform.repository.ProjectRepository;
import com.construction.platform.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Component
@Order(20)
@Profile({"dev", "docker", "prod"})
public class DemoDataSeedRunner implements ApplicationRunner {

    static final String DEMO_PASSWORD = "Demo123!";
    private static final String DIRECTOR_EMAIL = "director@construction.local";
    private static final String MARKER_EMAIL = "lukas.jonaitis@construction.local";

    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.seed.demo-data:false}")
    private boolean enabled;

    public DemoDataSeedRunner(
            UserRepository userRepository,
            ProjectRepository projectRepository,
            ProjectMemberRepository projectMemberRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.projectRepository = projectRepository;
        this.projectMemberRepository = projectMemberRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!enabled || userRepository.existsByEmailIgnoreCase(MARKER_EMAIL)) {
            return;
        }

        User director = userRepository.findByEmailIgnoreCase(DIRECTOR_EMAIL).orElse(null);
        if (director == null) {
            return;
        }

        String passwordHash = passwordEncoder.encode(DEMO_PASSWORD);

        List<User> riversideTeam = userRepository.saveAll(List.of(
                person("lukas.jonaitis@construction.local", "Lukas Jonaitis", UserRole.PROJECT_MANAGER, passwordHash),
                person("andrius.kazlauskas@construction.local", "Andrius Kazlauskas", UserRole.SITE_WORKER, passwordHash),
                person("monika.petraityte@construction.local", "Monika Petraitytė", UserRole.SITE_WORKER, passwordHash),
                person("dovydas.jankauskas@construction.local", "Dovydas Jankauskas", UserRole.SITE_WORKER, passwordHash),
                person("greta.simkute@construction.local", "Greta Šimkutė", UserRole.SITE_WORKER, passwordHash)
        ));

        List<User> logisticsTeam = userRepository.saveAll(List.of(
                person("ieva.paulauskiene@construction.local", "Ieva Paulauskienė", UserRole.PROJECT_MANAGER, passwordHash),
                person("martynas.vaitkus@construction.local", "Martynas Vaitkus", UserRole.SITE_WORKER, passwordHash),
                person("ruta.navickaite@construction.local", "Rūta Navickaitė", UserRole.SITE_WORKER, passwordHash),
                person("jonas.baranauskas@construction.local", "Jonas Baranauskas", UserRole.SITE_WORKER, passwordHash),
                person("egle.stankeviciute@construction.local", "Eglė Stankevičiūtė", UserRole.SITE_WORKER, passwordHash)
        ));

        Project riverside = projectRepository.save(project(
                "Kaunas Riverside Apartments",
                "Nemuno g. 14, Kaunas",
                "Baltic Homes UAB",
                LocalDate.of(2026, 3, 2),
                LocalDate.of(2027, 8, 15),
                director
        ));
        assignAll(riverside, riversideTeam);

        Project logistics = projectRepository.save(project(
                "Vilnius Logistics Hub",
                "Savanorių pr. 176, Vilnius",
                "Nord Cargo Group",
                LocalDate.of(2026, 1, 12),
                LocalDate.of(2026, 11, 30),
                director
        ));
        assignAll(logistics, logisticsTeam);
    }

    private static User person(String email, String fullName, UserRole role, String passwordHash) {
        return new User(email, passwordHash, fullName, role);
    }

    private static Project project(
            String name,
            String siteAddress,
            String clientName,
            LocalDate startDate,
            LocalDate endDate,
            User createdBy
    ) {
        Project project = new Project(name, siteAddress, clientName, createdBy);
        project.setStatus(ProjectStatus.ACTIVE);
        project.setStartDate(startDate);
        project.setEndDate(endDate);
        return project;
    }

    private void assignAll(Project project, List<User> users) {
        for (User user : users) {
            projectMemberRepository.save(new ProjectMember(project, user));
        }
    }
}
