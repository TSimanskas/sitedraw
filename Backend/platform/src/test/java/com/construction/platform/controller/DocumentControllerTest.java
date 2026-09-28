package com.construction.platform.controller;

import com.jayway.jsonpath.JsonPath;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class DocumentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void uploadListAndDownloadPdfRespectsRolePermissions() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String directorToken = login("director@construction.local", "Director123!");

        String pmEmail = "pm-doc-" + suffix + "@construction.local";
        String workerEmail = "worker-doc-" + suffix + "@construction.local";
        createUser(directorToken, pmEmail, "Doc PM", "PROJECT_MANAGER");
        String workerUserId = createUser(directorToken, workerEmail, "Doc Worker", "SITE_WORKER");

        String pmToken = login(pmEmail, "Password123!");
        String workerToken = login(workerEmail, "Password123!");

        MvcResult projectResult = mockMvc.perform(post("/api/projects")
                        .header("Authorization", "Bearer " + pmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Doc Site %s","siteAddress":"1 Main","clientName":"Client"}
                                """.formatted(suffix)))
                .andExpect(status().isCreated())
                .andReturn();

        String projectId = JsonPath.read(projectResult.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(post("/api/projects/" + projectId + "/members")
                        .header("Authorization", "Bearer " + pmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":"%s"}
                                """.formatted(workerUserId)))
                .andExpect(status().isOk());

        MockMultipartFile pdf = samplePdf("drawing.pdf");

        mockMvc.perform(multipart("/api/projects/" + projectId + "/documents")
                        .file(pdf)
                        .header("Authorization", "Bearer " + workerToken))
                .andExpect(status().isForbidden());

        MvcResult uploadResult = mockMvc.perform(multipart("/api/projects/" + projectId + "/documents")
                        .file(pdf)
                        .param("title", "Site Drawing")
                        .param("category", "Plans")
                        .header("Authorization", "Bearer " + pmToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Site Drawing"))
                .andExpect(jsonPath("$.pageCount").value(1))
                .andExpect(jsonPath("$.versionNumber").value(1))
                .andReturn();

        String documentId = JsonPath.read(uploadResult.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(get("/api/projects/" + projectId + "/documents")
                        .header("Authorization", "Bearer " + workerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(documentId));

        mockMvc.perform(get("/api/documents/" + documentId + "/versions/1/file")
                        .header("Authorization", "Bearer " + workerToken))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("Site_Drawing.pdf")));

        mockMvc.perform(multipart("/api/documents/" + documentId + "/versions")
                        .file(samplePdf("revision.pdf"))
                        .header("Authorization", "Bearer " + workerToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(multipart("/api/documents/" + documentId + "/versions")
                        .file(samplePdf("revision.pdf"))
                        .header("Authorization", "Bearer " + pmToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.versionNumber").value(2))
                .andExpect(jsonPath("$.versionCount").value(2));

        mockMvc.perform(get("/api/documents/" + documentId + "/versions")
                        .header("Authorization", "Bearer " + workerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].versionNumber").value(2))
                .andExpect(jsonPath("$[0].source").value("UPLOAD"))
                .andExpect(jsonPath("$[1].source").value("ORIGINAL"));

        mockMvc.perform(get("/api/projects/" + projectId + "/documents?q=Site")
                        .header("Authorization", "Bearer " + pmToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(documentId));

        mockMvc.perform(get("/api/projects/" + projectId + "/documents?q=does-not-exist")
                        .header("Authorization", "Bearer " + pmToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        mockMvc.perform(delete("/api/documents/" + documentId)
                        .header("Authorization", "Bearer " + workerToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/documents/" + documentId)
                        .header("Authorization", "Bearer " + pmToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/projects/" + projectId + "/documents")
                        .header("Authorization", "Bearer " + pmToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void savingMarkupKeepsOriginalVersionAndCreatesEditableCopy() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String directorToken = login("director@construction.local", "Director123!");

        String pmEmail = "pm-markup-" + suffix + "@construction.local";
        String workerEmail = "worker-markup-" + suffix + "@construction.local";
        createUser(directorToken, pmEmail, "Markup PM", "PROJECT_MANAGER");
        String workerUserId = createUser(directorToken, workerEmail, "Markup Worker", "SITE_WORKER");

        String pmToken = login(pmEmail, "Password123!");
        String workerToken = login(workerEmail, "Password123!");

        MvcResult projectResult = mockMvc.perform(post("/api/projects")
                        .header("Authorization", "Bearer " + pmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Markup Site %s","siteAddress":"1 Main","clientName":"Client"}
                                """.formatted(suffix)))
                .andExpect(status().isCreated())
                .andReturn();

        String projectId = JsonPath.read(projectResult.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(post("/api/projects/" + projectId + "/members")
                        .header("Authorization", "Bearer " + pmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":"%s"}
                                """.formatted(workerUserId)))
                .andExpect(status().isOk());

        MvcResult uploadResult = mockMvc.perform(multipart("/api/projects/" + projectId + "/documents")
                        .file(samplePdf("drawing.pdf"))
                        .param("title", "Site Drawing")
                        .header("Authorization", "Bearer " + pmToken))
                .andExpect(status().isCreated())
                .andReturn();

        String documentId = JsonPath.read(uploadResult.getResponse().getContentAsString(), "$.id");

        MvcResult createResult = mockMvc.perform(post("/api/documents/" + documentId + "/versions/1/annotations")
                        .header("Authorization", "Bearer " + pmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "pageNumber": 1,
                                  "type": "HIGHLIGHT",
                                  "data": {
                                    "left": 0.1,
                                    "top": 0.2,
                                    "width": 0.3,
                                    "height": 0.1,
                                    "fill": "rgba(255,255,0,0.35)"
                                  }
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.createdByName").value("Markup PM"))
                .andReturn();

        String annotationId = JsonPath.read(createResult.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(post("/api/documents/" + documentId + "/versions/1/markup-versions")
                        .header("Authorization", "Bearer " + workerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "annotations": [
                                    {
                                      "pageNumber": 1,
                                      "type": "HIGHLIGHT",
                                      "data": {
                                        "left": 0.1,
                                        "top": 0.2,
                                        "width": 0.3,
                                        "height": 0.1,
                                        "fill": "rgba(255,255,0,0.35)"
                                      },
                                      "sourceAnnotationId": "%s"
                                    }
                                  ]
                                }
                                """.formatted(annotationId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.versionNumber").value(2))
                .andExpect(jsonPath("$.source").value("MARKUP"))
                .andExpect(jsonPath("$.uploadedByName").value("Markup Worker"));

        mockMvc.perform(get("/api/documents/" + documentId + "/versions")
                        .header("Authorization", "Bearer " + workerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].versionNumber").value(2))
                .andExpect(jsonPath("$[0].source").value("MARKUP"))
                .andExpect(jsonPath("$[0].uploadedByName").value("Markup Worker"))
                .andExpect(jsonPath("$[1].versionNumber").value(1))
                .andExpect(jsonPath("$[1].source").value("ORIGINAL"))
                .andExpect(jsonPath("$[1].uploadedByName").value("Markup PM"));

        mockMvc.perform(get("/api/documents/" + documentId + "/versions/1/annotations")
                        .header("Authorization", "Bearer " + workerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(annotationId))
                .andExpect(jsonPath("$[0].createdByName").value("Markup PM"));

        mockMvc.perform(get("/api/documents/" + documentId + "/versions/2/annotations")
                        .header("Authorization", "Bearer " + workerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].type").value("HIGHLIGHT"))
                .andExpect(jsonPath("$[0].createdByName").value("Markup PM"));
    }

    private MockMultipartFile samplePdf(String filename) throws IOException {
        byte[] content;
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            document.save(outputStream);
            content = outputStream.toByteArray();
        }

        return new MockMultipartFile(
                "file",
                filename,
                MediaType.APPLICATION_PDF_VALUE,
                content
        );
    }

    private String login(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn();

        return JsonPath.read(result.getResponse().getContentAsString(), "$.token");
    }

    private String createUser(String directorToken, String email, String fullName, String role) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/users")
                        .header("Authorization", "Bearer " + directorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email":"%s",
                                  "password":"Password123!",
                                  "fullName":"%s",
                                  "role":"%s"
                                }
                                """.formatted(email, fullName, role)))
                .andExpect(status().isCreated())
                .andReturn();

        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }
}
