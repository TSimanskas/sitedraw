package com.construction.platform.controller;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ProjectControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void projectCrudRespectsRolePermissions() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String directorToken = login("director@construction.local", "Director123!");

        String pmEmail = "pm-" + suffix + "@construction.local";
        String workerEmail = "worker-" + suffix + "@construction.local";

        createUser(directorToken, pmEmail, "Step 6 PM", "PROJECT_MANAGER");
        String workerUserId = createUser(directorToken, workerEmail, "Step 6 Worker", "SITE_WORKER");

        String pmToken = login(pmEmail, "Password123!");
        String workerToken = login(workerEmail, "Password123!");

        MvcResult createResult = mockMvc.perform(post("/api/projects")
                        .header("Authorization", "Bearer " + pmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"Site %s",
                                  "siteAddress":"123 Build St",
                                  "clientName":"Client Co"
                                }
                                """.formatted(suffix)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn();

        String projectId = JsonPath.read(createResult.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(post("/api/projects")
                        .header("Authorization", "Bearer " + workerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Blocked","siteAddress":"Nowhere","clientName":"None"}
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/projects/" + projectId + "/members")
                        .header("Authorization", "Bearer " + pmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":"%s"}
                                """.formatted(workerUserId)))
                .andExpect(status().isOk());

        String inactiveEmail = "inactive-" + suffix + "@construction.local";
        String inactiveUserId = createUser(directorToken, inactiveEmail, "Inactive Worker", "SITE_WORKER");
        mockMvc.perform(put("/api/users/" + inactiveUserId)
                        .header("Authorization", "Bearer " + directorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Inactive Worker","role":"SITE_WORKER","active":false}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/projects/" + projectId + "/assignable-users")
                        .header("Authorization", "Bearer " + directorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='" + inactiveUserId + "')].active").value(false));

        mockMvc.perform(post("/api/projects/" + projectId + "/members")
                        .header("Authorization", "Bearer " + pmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":"%s"}
                                """.formatted(inactiveUserId)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/projects")
                        .header("Authorization", "Bearer " + workerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='" + projectId + "')]").exists());

        mockMvc.perform(put("/api/projects/" + projectId)
                        .header("Authorization", "Bearer " + workerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"Updated by worker",
                                  "siteAddress":"123 Build St",
                                  "clientName":"Client Co",
                                  "status":"ACTIVE"
                                }
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/projects/" + projectId)
                        .header("Authorization", "Bearer " + pmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"Updated Site",
                                  "siteAddress":"456 Build St",
                                  "clientName":"Client Co",
                                  "status":"ACTIVE"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Site"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        mockMvc.perform(delete("/api/projects/" + projectId)
                        .header("Authorization", "Bearer " + pmToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/projects/" + projectId)
                        .header("Authorization", "Bearer " + directorToken))
                .andExpect(status().isNoContent());
    }

    @Test
    void projectSearchFiltersByNameAndClient() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String directorToken = login("director@construction.local", "Director123!");
        String pmEmail = "pm-search-" + suffix + "@construction.local";
        createUser(directorToken, pmEmail, "Search PM", "PROJECT_MANAGER");
        String pmToken = login(pmEmail, "Password123!");

        mockMvc.perform(post("/api/projects")
                        .header("Authorization", "Bearer " + pmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Harbor Tower %s","siteAddress":"Dock Road","clientName":"Northwind %s"}
                                """.formatted(suffix, suffix)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/projects?q=Harbor Tower " + suffix)
                        .header("Authorization", "Bearer " + pmToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Harbor Tower " + suffix));

        mockMvc.perform(get("/api/projects?q=Northwind " + suffix)
                        .header("Authorization", "Bearer " + pmToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].clientName").value("Northwind " + suffix));

        mockMvc.perform(get("/api/projects?q=zzzz-no-match")
                        .header("Authorization", "Bearer " + pmToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void removeMemberRevokesAccessAndRespectsRoles() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String directorToken = login("director@construction.local", "Director123!");

        String pmEmail = "pm-remove-" + suffix + "@construction.local";
        String otherPmEmail = "pm2-remove-" + suffix + "@construction.local";
        String workerEmail = "worker-remove-" + suffix + "@construction.local";

        createUser(directorToken, pmEmail, "Remove PM", "PROJECT_MANAGER");
        String otherPmId = createUser(directorToken, otherPmEmail, "Remove Other PM", "PROJECT_MANAGER");
        String workerUserId = createUser(directorToken, workerEmail, "Remove Worker", "SITE_WORKER");

        String pmToken = login(pmEmail, "Password123!");
        String otherPmToken = login(otherPmEmail, "Password123!");
        String workerToken = login(workerEmail, "Password123!");

        MvcResult createResult = mockMvc.perform(post("/api/projects")
                        .header("Authorization", "Bearer " + pmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Remove Job %s","siteAddress":"1 Site St","clientName":"Client"}
                                """.formatted(suffix)))
                .andExpect(status().isCreated())
                .andReturn();

        String projectId = JsonPath.read(createResult.getResponse().getContentAsString(), "$.id");
        String pmUserId = JsonPath.read(createResult.getResponse().getContentAsString(), "$.createdById");

        mockMvc.perform(post("/api/projects/" + projectId + "/members")
                        .header("Authorization", "Bearer " + directorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":"%s"}
                                """.formatted(otherPmId)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/projects/" + projectId + "/members")
                        .header("Authorization", "Bearer " + pmToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":"%s"}
                                """.formatted(workerUserId)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/projects/" + projectId)
                        .header("Authorization", "Bearer " + workerToken))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/projects/" + projectId + "/members/" + workerUserId)
                        .header("Authorization", "Bearer " + workerToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/projects/" + projectId + "/members/" + pmUserId)
                        .header("Authorization", "Bearer " + pmToken))
                .andExpect(status().isBadRequest());

        mockMvc.perform(delete("/api/projects/" + projectId + "/members/" + otherPmId)
                        .header("Authorization", "Bearer " + pmToken))
                .andExpect(status().isBadRequest());

        mockMvc.perform(delete("/api/projects/" + projectId + "/members/" + workerUserId)
                        .header("Authorization", "Bearer " + pmToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.members[?(@.userId=='" + workerUserId + "')]").doesNotExist());

        mockMvc.perform(get("/api/projects/" + projectId)
                        .header("Authorization", "Bearer " + workerToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/projects")
                        .header("Authorization", "Bearer " + workerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='" + projectId + "')]").doesNotExist());

        mockMvc.perform(delete("/api/projects/" + projectId + "/members/" + otherPmId)
                        .header("Authorization", "Bearer " + directorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.members[?(@.userId=='" + otherPmId + "')]").doesNotExist());

        mockMvc.perform(get("/api/projects/" + projectId)
                        .header("Authorization", "Bearer " + otherPmToken))
                .andExpect(status().isForbidden());
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
