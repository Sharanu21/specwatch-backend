package com.specwatch.repository;

import com.specwatch.model.ChangeReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;

public interface ChangeReportRepository extends JpaRepository<ChangeReport, Long> {
    Page<ChangeReport> findByProjectIdOrderByCreatedAtDesc(Long projectId, Pageable pageable);
    List<ChangeReport> findTop5ByProjectIdOrderByCreatedAtDesc(Long projectId);
    List<ChangeReport> findByProjectIdAndHasBreakingChangesTrue(Long projectId);
}
