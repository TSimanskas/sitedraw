package com.construction.platform.repository;

import com.construction.platform.domain.ProjectMember;
import com.construction.platform.domain.ProjectMemberId;
import com.construction.platform.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProjectMemberRepository extends JpaRepository<ProjectMember, ProjectMemberId> {

    List<ProjectMember> findByUser(User user);
    List<ProjectMember> findByProjectIdOrderByAssignedAtAsc (UUID projectId);

    boolean existsByProjectIdAndUserId(UUID projectId, UUID userId);

    void deleteByProjectIdAndUserId(UUID projectId, UUID userId);
}
