package com.fuelfinder.modules.anp.controller;

import com.fuelfinder.modules.anp.dto.AnpImportLogDTO;
import com.fuelfinder.modules.anp.dto.AnpImportRequestDTO;
import com.fuelfinder.modules.anp.entity.AnpImportLog;
import com.fuelfinder.modules.anp.service.AnpImportService;
import com.fuelfinder.modules.auth.AuthPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/admin/anp")
@PreAuthorize("hasRole('ADMIN')")
public class AnpImportController {

    private final AnpImportService anpImportService;

    public AnpImportController(AnpImportService anpImportService) {
        this.anpImportService = anpImportService;
    }

    @PostMapping("/import")
    public ResponseEntity<AnpImportLogDTO> triggerImport(
            @Valid @RequestBody AnpImportRequestDTO request,
            @AuthenticationPrincipal AuthPrincipal principal) {
        AnpImportLog result = anpImportService.executeImport(
                request.sourceUrl(),
                request.referencePeriod(),
                principal.userId());
        return ResponseEntity.accepted().body(anpImportService.toDTO(result));
    }

    @GetMapping("/imports")
    public ResponseEntity<List<AnpImportLogDTO>> listImports() {
        return ResponseEntity.ok(anpImportService.listImports().stream()
                .map(anpImportService::toDTO)
                .toList());
    }

    @GetMapping("/imports/{id}")
    public ResponseEntity<AnpImportLogDTO> getImport(@PathVariable UUID id) {
        return ResponseEntity.ok(anpImportService.toDTO(anpImportService.getImport(id)));
    }
}
