package com.construction.platform.controller;

import com.construction.platform.dto.CreateUserRequest;
import com.construction.platform.dto.ResetPasswordRequest;
import com.construction.platform.dto.UpdateUserRequest;
import com.construction.platform.dto.UserResponse;
import com.construction.platform.security.UserPrincipal;
import com.construction.platform.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    @PreAuthorize("hasRole('DIRECTOR')")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse createUser(@Valid @RequestBody CreateUserRequest request) {
        return userService.createUser(request);
    }

    @GetMapping
    @PreAuthorize("hasRole('DIRECTOR')")
    public List<UserResponse> listUsers() {
        return userService.listUsers();
    }

    @PutMapping("/{userId}")
    @PreAuthorize("hasRole('DIRECTOR')")
    public UserResponse updateUser(
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateUserRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return userService.updateUser(userId, request, principal);
    }

    @PutMapping("/{userId}/password")
    @PreAuthorize("hasRole('DIRECTOR')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(
            @PathVariable UUID userId,
            @Valid @RequestBody ResetPasswordRequest request
    ) {
        userService.resetPassword(userId, request);
    }
}
