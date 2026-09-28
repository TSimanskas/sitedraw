package com.construction.platform.service;

import com.construction.platform.domain.User;
import com.construction.platform.domain.UserRole;
import com.construction.platform.dto.CreateUserRequest;
import com.construction.platform.dto.ResetPasswordRequest;
import com.construction.platform.dto.UpdateUserRequest;
import com.construction.platform.dto.UserResponse;
import com.construction.platform.exception.ApiException;
import com.construction.platform.repository.UserRepository;
import com.construction.platform.security.UserPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        if (request.role() == UserRole.DIRECTOR) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Directors can only create project managers and site workers");
        }

        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            throw new ApiException(HttpStatus.CONFLICT, "Email is already registered");
        }

        User user = new User(
                request.email().trim(),
                passwordEncoder.encode(request.password()),
                request.fullName().trim(),
                request.role()
        );

        return UserResponse.from(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public List<UserResponse> listUsers() {
        return userRepository.findAll().stream()
                .sorted(Comparator.comparing(User::getFullName, String.CASE_INSENSITIVE_ORDER))
                .map(UserResponse::from)
                .toList();
    }

    @Transactional
    public UserResponse updateUser(UUID userId, UpdateUserRequest request, UserPrincipal actor) {
        User user = requireUser(userId);

        if (user.getRole() == UserRole.DIRECTOR && request.role() != UserRole.DIRECTOR) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Director role cannot be changed");
        }

        if (request.role() == UserRole.DIRECTOR && user.getRole() != UserRole.DIRECTOR) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Cannot promote a user to director");
        }

        if (!request.active() && user.getId().equals(actor.getId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "You cannot deactivate your own account");
        }

        user.setFullName(request.fullName().trim());
        user.setRole(request.role());
        user.setActive(request.active());

        return UserResponse.from(userRepository.save(user));
    }

    @Transactional
    public void resetPassword(UUID userId, ResetPasswordRequest request) {
        User user = requireUser(userId);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        userRepository.save(user);
    }

    private User requireUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));
    }
}
