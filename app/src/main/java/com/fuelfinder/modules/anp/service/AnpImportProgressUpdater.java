package com.fuelfinder.modules.anp.service;

import com.fuelfinder.modules.anp.repository.AnpImportLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AnpImportProgressUpdater {

    private final AnpImportLogRepository importLogRepository;

    public AnpImportProgressUpdater(AnpImportLogRepository importLogRepository) {
        this.importLogRepository = importLogRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void update(UUID importId, String stage, String message, Integer percent) {
        importLogRepository.findById(importId).ifPresent(log -> {
            log.setProgressStage(stage);
            log.setProgressMessage(message);
            log.setProgressPercent(percent);
            importLogRepository.save(log);
        });
    }
}
