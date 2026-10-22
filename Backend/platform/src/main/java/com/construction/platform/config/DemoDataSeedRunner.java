package com.construction.platform.config;

import com.construction.platform.domain.Document;
import com.construction.platform.domain.DocumentVersion;
import com.construction.platform.domain.Project;
import com.construction.platform.domain.ProjectMember;
import com.construction.platform.domain.ProjectStatus;
import com.construction.platform.domain.User;
import com.construction.platform.domain.UserRole;
import com.construction.platform.repository.DocumentRepository;
import com.construction.platform.repository.DocumentVersionRepository;
import com.construction.platform.repository.ProjectMemberRepository;
import com.construction.platform.repository.ProjectRepository;
import com.construction.platform.repository.UserRepository;
import com.construction.platform.storage.StorageService;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Component
@Order(20)
@Profile({"dev", "docker", "prod"})
public class DemoDataSeedRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeedRunner.class);

    static final String DEMO_PASSWORD = "Demo123!";
    static final String DEMO_PLAN_TITLE = "Demo planas";
    private static final String DEMO_PLAN_RESOURCE = "demo/demo-plan.pdf";
    private static final String PDF_CONTENT_TYPE = "application/pdf";
    private static final String DIRECTOR_EMAIL = "director@construction.local";
    private static final String MARKER_EMAIL = "lukas.jonaitis@construction.local";
    private static final String RIVERSIDE_PROJECT = "Kaunas Riverside Apartments";

    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final DocumentRepository documentRepository;
    private final DocumentVersionRepository documentVersionRepository;
    private final StorageService storageService;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.seed.demo-data:false}")
    private boolean enabled;

    public DemoDataSeedRunner(
            UserRepository userRepository,
            ProjectRepository projectRepository,
            ProjectMemberRepository projectMemberRepository,
            DocumentRepository documentRepository,
            DocumentVersionRepository documentVersionRepository,
            StorageService storageService,
            PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.projectRepository = projectRepository;
        this.projectMemberRepository = projectMemberRepository;
        this.documentRepository = documentRepository;
        this.documentVersionRepository = documentVersionRepository;
        this.storageService = storageService;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }

        User director = userRepository.findByEmailIgnoreCase(DIRECTOR_EMAIL).orElse(null);
        if (director == null) {
            return;
        }

        if (!userRepository.existsByEmailIgnoreCase(MARKER_EMAIL)) {
            seedTeamsAndProjects(director);
        }

        seedDemoPlan(director);
    }

    private void seedTeamsAndProjects(User director) {
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
                RIVERSIDE_PROJECT,
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

    private void seedDemoPlan(User director) {
        Project project = projectRepository.findFirstByName(RIVERSIDE_PROJECT).orElse(null);
        if (project == null) {
            return;
        }

        byte[] pdf = readDemoPlan();
        if (pdf == null) {
            return;
        }

        int pageCount;
        try {
            pageCount = countPages(pdf);
        } catch (IOException exception) {
            log.warn("Demo plan PDF could not be read; skipping drawing seed");
            return;
        }

        Document document = documentRepository.findFirstByProjectAndTitle(project, DEMO_PLAN_TITLE)
                .orElseGet(() -> documentRepository.save(new Document(project, DEMO_PLAN_TITLE, null, director)));

        List<DocumentVersion> versions = documentVersionRepository.findByDocumentOrderByVersionNumberDesc(document);
        if (versions.isEmpty()) {
            storePlan(document, pdf, pageCount, director);
            project.touch();
            projectRepository.save(project);
            log.info("Seeded demo plan on {}", project.getName());
            return;
        }

        DocumentVersion version = versions.get(0);
        if (!storageService.exists(version.getStorageKey())) {
            storageService.upload(
                    version.getStorageKey(),
                    new ByteArrayInputStream(pdf),
                    pdf.length,
                    PDF_CONTENT_TYPE
            );
            log.info("Restored missing demo plan file for {}", project.getName());
        }
        if (version.getBackupStorageKey() != null && !storageService.exists(version.getBackupStorageKey())) {
            storageService.copy(version.getStorageKey(), version.getBackupStorageKey());
        }
    }

    private void storePlan(Document document, byte[] pdf, int pageCount, User uploader) {
        String storageKey = "documents/" + document.getId() + "/v1/original.pdf";
        String backupStorageKey = "documents/" + document.getId() + "/v1/backup-" + Instant.now().toEpochMilli() + ".pdf";

        storageService.upload(storageKey, new ByteArrayInputStream(pdf), pdf.length, PDF_CONTENT_TYPE);
        storageService.copy(storageKey, backupStorageKey);

        documentVersionRepository.save(new DocumentVersion(
                document,
                1,
                storageKey,
                backupStorageKey,
                pageCount,
                pdf.length,
                uploader
        ));
    }

    private byte[] readDemoPlan() {
        ClassPathResource resource = new ClassPathResource(DEMO_PLAN_RESOURCE);
        if (!resource.exists()) {
            log.warn("Demo plan PDF was not found on the classpath; skipping drawing seed");
            return null;
        }
        try (InputStream inputStream = resource.getInputStream()) {
            return inputStream.readAllBytes();
        } catch (IOException exception) {
            log.warn("Demo plan PDF could not be loaded; skipping drawing seed");
            return null;
        }
    }

    private static int countPages(byte[] pdf) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return document.getNumberOfPages();
        }
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
