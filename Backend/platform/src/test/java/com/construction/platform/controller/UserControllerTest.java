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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void directorCanCreateProjectManager() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String email = "pm-" + suffix + "@construction.local";
        String token = loginAsDirector();

        mockMvc.perform(post("/api/users")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email":"%s",
                                  "password":"Password123!",
                                  "fullName":"Test PM",
                                  "role":"PROJECT_MANAGER"
                                }
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.role").value("PROJECT_MANAGER"));
    }

    @Test
    void directorCanListUsers() throws Exception {
        String token = loginAsDirector();

        mockMvc.perform(get("/api/users")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").exists());
    }

    @Test
    void nonDirectorCannotCreateUsers() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String workerEmail = "worker-" + suffix + "@construction.local";
        String directorToken = loginAsDirector();

        createUser(directorToken, workerEmail, "Test Worker", "SITE_WORKER");
        String workerToken = login(workerEmail, "Password123!");

        mockMvc.perform(post("/api/users")
                        .header("Authorization", "Bearer " + workerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email":"another-%s@construction.local",
                                  "password":"Password123!",
                                  "fullName":"Another User",
                                  "role":"SITE_WORKER"
                                }
                                """.formatted(suffix)))
                .andExpect(status().isForbidden());
    }

    @Test
    void duplicateEmailReturnsConflict() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String email = "duplicate-" + suffix + "@construction.local";
        String token = loginAsDirector();

        String body = """
                {
                  "email":"%s",
                  "password":"Password123!",
                  "fullName":"Duplicate User",
                  "role":"SITE_WORKER"
                }
                """.formatted(email);

        mockMvc.perform(post("/api/users")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/users")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void directorRoleIsRejected() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String token = loginAsDirector();

        mockMvc.perform(post("/api/users")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email":"newdirector-%s@construction.local",
                                  "password":"Password123!",
                                  "fullName":"New Director",
                                  "role":"DIRECTOR"
                                }
                                """.formatted(suffix)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void directorCanUpdateDeactivateAndResetPassword() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String email = "worker-manage-" + suffix + "@construction.local";
        String directorToken = loginAsDirector();
        String userId = createUser(directorToken, email, "Manage Worker", "SITE_WORKER");

        mockMvc.perform(put("/api/users/" + userId)
                        .header("Authorization", "Bearer " + directorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Renamed Worker","role":"PROJECT_MANAGER","active":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Renamed Worker"))
                .andExpect(jsonPath("$.role").value("PROJECT_MANAGER"))
                .andExpect(jsonPath("$.active").value(true));

        mockMvc.perform(put("/api/users/" + userId + "/password")
                        .header("Authorization", "Bearer " + directorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"password":"NewPass123!"}
                                """))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"NewPass123!"}
                                """.formatted(email)))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/users/" + userId)
                        .header("Authorization", "Bearer " + directorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Renamed Worker","role":"PROJECT_MANAGER","active":false}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"NewPass123!"}
                                """.formatted(email)))
                .andExpect(status().isUnauthorized());
    }

    private String loginAsDirector() throws Exception {
        return login("director@construction.local", "Director123!");
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
