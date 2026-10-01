package com.fuelfinder.modules.anp.repository;

import com.fuelfinder.modules.anp.entity.AnpImportLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AnpImportLogRepository extends JpaRepository<AnpImportLog, UUID> {

    List<AnpImportLog> findAllByOrderByImportStartDesc();
}
