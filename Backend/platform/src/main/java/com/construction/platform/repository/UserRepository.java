package com.construction.platform.repository;

import com.construction.platform.domain.User;
import com.construction.platform.domain.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    List<User> findByRoleInOrderByFullNameAsc(Collection<UserRole> roles);
}
