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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AnnotationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void annotationCrudRollbackAndPermissions() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String directorToken = login("director@construction.local", "Director123!");

        String pmEmail = "pm-ann-" + suffix + "@construction.local";
        String workerEmail = "worker-ann-" + suffix + "@construction.local";
        String worker2Email = "worker2-ann-" + suffix + "@construction.local";
        createUser(directorToken, pmEmail, "Ann PM", "PROJECT_MANAGER");
        String workerUserId = createUser(directorToken, workerEmail, "Ann Worker", "SITE_WORKER");
        String worker2UserId = createUser(directorToken, worker2Email, "Ann Worker 2", "SITE_WORKER");

        String pmToken = login(pmEmail, "Password123!");
        String workerToken = login(workerEmail, "Password123!");
        String worker2Token = login(worker2Email, "Password123!");

        MvcResult projectResult = mockMvc.perform(post("/api/projects")
                        .header("Authorization", "Bearer " + pmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Ann Site %s","siteAddress":"1 Main","clientName":"Client"}
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

        mockMvc.perform(post("/api/projects/" + projectId + "/members")
                        .header("Authorization", "Bearer " + pmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":"%s"}
                                """.formatted(worker2UserId)))
                .andExpect(status().isOk());

        MockMultipartFile pdf = samplePdf("drawing.pdf");
        MvcResult uploadResult = mockMvc.perform(multipart("/api/projects/" + projectId + "/documents")
                        .file(pdf)
                        .param("title", "Site Drawing")
                        .header("Authorization", "Bearer " + pmToken))
                .andExpect(status().isCreated())
                .andReturn();

        String documentId = JsonPath.read(uploadResult.getResponse().getContentAsString(), "$.id");

        MvcResult createResult = mockMvc.perform(post("/api/documents/" + documentId + "/versions/1/annotations")
                        .header("Authorization", "Bearer " + workerToken)
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
                .andExpect(jsonPath("$.type").value("HIGHLIGHT"))
                .andReturn();

        String annotationId = JsonPath.read(createResult.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(get("/api/documents/" + documentId + "/versions/1/annotations?page=1")
                        .header("Authorization", "Bearer " + workerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(annotationId));

        mockMvc.perform(put("/api/annotations/" + annotationId)
                        .header("Authorization", "Bearer " + pmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "data": {
                                    "left": 0.15,
                                    "top": 0.25,
                                    "width": 0.35,
                                    "height": 0.12,
                                    "fill": "rgba(255,255,0,0.35)"
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.left").value(0.15));

        mockMvc.perform(put("/api/annotations/" + annotationId)
                        .header("Authorization", "Bearer " + worker2Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"data":{"left":0.2,"top":0.2,"width":0.3,"height":0.1,"fill":"rgba(255,255,0,0.35)"}}
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/annotations/" + annotationId + "/revisions")
                        .header("Authorization", "Bearer " + workerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].action").value("UPDATE"));

        mockMvc.perform(post("/api/annotations/" + annotationId + "/rollback")
                        .header("Authorization", "Bearer " + pmToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.left").value(0.1));

        mockMvc.perform(delete("/api/annotations/" + annotationId)
                        .header("Authorization", "Bearer " + pmToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/documents/" + documentId + "/versions/1/annotations?page=1")
                        .header("Authorization", "Bearer " + workerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        mockMvc.perform(put("/api/documents/" + documentId + "/versions/1/calibration")
                        .header("Authorization", "Bearer " + workerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"pixelsPerUnit": 12.5, "unitLabel": "m"}
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/documents/" + documentId + "/versions/1/calibration")
                        .header("Authorization", "Bearer " + pmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"pixelsPerUnit": 12.5, "unitLabel": "m"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unitLabel").value("m"));
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
