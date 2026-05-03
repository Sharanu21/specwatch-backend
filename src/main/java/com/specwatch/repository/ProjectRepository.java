package com.specwatch.repository;

import com.specwatch.model.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    List<Project> findByUserId(Long userId);

    // 🚨 FIX: Case-insensitive search + grabs the first match to prevent 500 errors
    @Query("SELECT p FROM Project p WHERE LOWER(p.repoFullName) = LOWER(:repoFullName) AND LOWER(p.branch) = LOWER(:branch)")
    Optional<Project> findFirstByRepoFullNameAndBranch(
            @Param("repoFullName") String repoFullName,
            @Param("branch") String branch
    );

    int countByUserId(Long userId);

    Optional<Project> findByWebhookToken(String webhookToken);

    long countByCreatedAtAfter(java.time.LocalDateTime date);
}