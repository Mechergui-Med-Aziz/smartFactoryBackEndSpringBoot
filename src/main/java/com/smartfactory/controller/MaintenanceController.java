package com.smartfactory.controller;

import com.smartfactory.entity.*;
import com.smartfactory.security.UserPrincipal;
import com.smartfactory.service.MaintenanceInterventionService;
import com.smartfactory.service.MaintenanceRecommendationService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Contrôleur REST pour la gestion intégrale de la maintenance et des interventions (Sprint 6 - US42 à US50).
 */
@RestController
@RequestMapping("/api/maintenance")
public class MaintenanceController {

    private final MaintenanceInterventionService interventionService;
    private final MaintenanceRecommendationService recommendationService;

    public MaintenanceController(MaintenanceInterventionService interventionService,
                                 MaintenanceRecommendationService recommendationService) {
        this.interventionService = interventionService;
        this.recommendationService = recommendationService;
    }

    /**
     * Crée une intervention de maintenance (US42, Section 6.1).
     * Autorisé uniquement pour RESPONSABLE_INDUSTRIEL (et ADMIN).
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('RESPONSABLE_INDUSTRIEL', 'ADMIN') or (hasAuthority('MAINTENANCE_WRITE') and !hasRole('TECHNICIAN') and !hasRole('OPERATOR'))")
    public ResponseEntity<MaintenanceIntervention> createIntervention(
            @RequestBody MaintenanceIntervention request,
            @AuthenticationPrincipal UserPrincipal currentUser) {

        String username = currentUser != null ? currentUser.getUsername() : "ANONYMOUS";
        MaintenanceIntervention created = interventionService.createIntervention(request, username, currentUser);
        return new ResponseEntity<>(created, HttpStatus.CREATED);
    }

    /**
     * Modifie les paramètres de planification d'une intervention (Section 6.1).
     * Réservé au RESPONSABLE_INDUSTRIEL (et ADMIN).
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('RESPONSABLE_INDUSTRIEL', 'ADMIN') or (hasAuthority('MAINTENANCE_WRITE') and !hasRole('OPERATOR'))")
    public ResponseEntity<MaintenanceIntervention> updateIntervention(
            @PathVariable String id,
            @RequestBody MaintenanceIntervention request,
            @AuthenticationPrincipal UserPrincipal currentUser) {

        String updatedBy = currentUser != null ? currentUser.getUsername() : "ANONYMOUS";
        MaintenanceIntervention updated = interventionService.updateIntervention(id, request, updatedBy, currentUser);
        return ResponseEntity.ok(updated);
    }

    /**
     * Consulte l'historique complet des interventions avec filtres et pagination (US49, Section 6.3).
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'RESPONSABLE_INDUSTRIEL', 'TECHNICIAN') or (hasAuthority('MAINTENANCE_READ') and !hasRole('OPERATOR'))")
    public ResponseEntity<Page<MaintenanceIntervention>> getInterventions(
            @RequestParam(required = false) String machineId,
            @RequestParam(required = false) String technicianId,
            @RequestParam(required = false) MaintenanceType type,
            @RequestParam(required = false) PreventiveSubtype subtype,
            @RequestParam(required = false) InterventionStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String[] sort,
            @AuthenticationPrincipal UserPrincipal currentUser) {

        Sort sortOrder = Sort.by(Sort.Direction.DESC, "createdAt");
        if (sort != null && sort.length > 0) {
            String property = sort[0];
            Sort.Direction dir = (sort.length > 1 && "asc".equalsIgnoreCase(sort[1])) ? Sort.Direction.ASC : Sort.Direction.DESC;
            sortOrder = Sort.by(dir, property);
        }

        Pageable pageable = PageRequest.of(page, size, sortOrder);
        Page<MaintenanceIntervention> result = interventionService.getInterventions(
                machineId, technicianId, type, subtype, status, from, to, pageable, currentUser
        );
        return ResponseEntity.ok(result);
    }

    /**
     * Consulte le détail d'une intervention (Section 6.3).
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'RESPONSABLE_INDUSTRIEL', 'TECHNICIAN') or (hasAuthority('MAINTENANCE_READ') and !hasRole('OPERATOR'))")
    public ResponseEntity<MaintenanceIntervention> getInterventionById(
            @PathVariable String id,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        return ResponseEntity.ok(interventionService.getInterventionById(id, currentUser));
    }

    /**
     * Assigne un ou plusieurs techniciens à une intervention (US43, Section 6.1).
     */
    @RequestMapping(value = "/{id}/assign", method = {RequestMethod.PUT, RequestMethod.POST})
    @PreAuthorize("hasAnyRole('RESPONSABLE_INDUSTRIEL', 'ADMIN') or (hasAuthority('MAINTENANCE_WRITE') and !hasRole('TECHNICIAN') and !hasRole('OPERATOR'))")
    public ResponseEntity<MaintenanceIntervention> assignTechnician(
            @PathVariable String id,
            @RequestBody(required = false) MaintenanceIntervention request,
            @AuthenticationPrincipal UserPrincipal currentUser) {

        String assignedBy = currentUser != null ? currentUser.getUsername() : "ANONYMOUS";
        MaintenanceIntervention updated;
        if (request != null && request.getTechnicianIds() != null && !request.getTechnicianIds().isEmpty()) {
            updated = interventionService.assignTechnicians(id, request.getTechnicianIds(), assignedBy);
        } else if (request != null && org.springframework.util.StringUtils.hasText(request.getTechnicianId())) {
            updated = interventionService.assignTechnician(id, request.getTechnicianId(), assignedBy);
        } else {
            updated = interventionService.assignTechnicians(id, java.util.Collections.emptyList(), assignedBy);
        }
        return ResponseEntity.ok(updated);
    }

    /**
     * Consulte l'anomalie et les mesures contextuelles associées à l'intervention (US44, Section 6.3).
     */
    @GetMapping("/{id}/anomaly")
    @PreAuthorize("hasAnyRole('ADMIN', 'RESPONSABLE_INDUSTRIEL', 'TECHNICIAN') or (hasAuthority('MAINTENANCE_READ') and !hasRole('OPERATOR'))")
    public ResponseEntity<Map<String, Object>> getAnomalyContext(
            @PathVariable String id,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        return ResponseEntity.ok(interventionService.getAnomalyContext(id, currentUser));
    }

    /**
     * Enregistre un diagnostic technique (US45, Section 6.2).
     */
    /**
     * Démarre formellement une intervention de maintenance (Statut IN_PROGRESS).
     * Notifie le responsable industriel du lancement des travaux (Section 3).
     */
    @RequestMapping(value = "/{id}/start", method = {RequestMethod.PUT, RequestMethod.POST})
    @PreAuthorize("hasAnyRole('ADMIN', 'TECHNICIAN', 'RESPONSABLE_INDUSTRIEL') or hasAuthority('MAINTENANCE_WRITE')")
    public ResponseEntity<MaintenanceIntervention> startIntervention(
            @PathVariable String id,
            @AuthenticationPrincipal UserPrincipal currentUser) {

        String startedBy = currentUser != null ? currentUser.getUsername() : "ANONYMOUS";
        MaintenanceIntervention started = interventionService.startIntervention(id, startedBy, currentUser);
        return ResponseEntity.ok(started);
    }

    /**
     * Enregistre un diagnostic technique (US45, Section 6.2).
     */
    @PostMapping("/{id}/diagnoses")
    @PreAuthorize("hasAnyRole('ADMIN', 'TECHNICIAN', 'RESPONSABLE_INDUSTRIEL')")
    public ResponseEntity<MaintenanceIntervention> addDiagnosis(
            @PathVariable String id,
            @RequestBody MaintenanceDiagnosis request,
            @AuthenticationPrincipal UserPrincipal currentUser) {

        String techId = currentUser != null && currentUser.getId() != null ? currentUser.getId() : (currentUser != null ? currentUser.getUsername() : "ANONYMOUS");
        String techName = currentUser != null ? currentUser.getUsername() : "Technicien";
        MaintenanceIntervention updated = interventionService.addDiagnosis(id, request, techId, techName, currentUser);
        return ResponseEntity.ok(updated);
    }

    /**
     * Enregistre une action de maintenance réalisée (US46, Section 6.2).
     */
    @PostMapping("/{id}/actions")
    @PreAuthorize("hasAnyRole('ADMIN', 'TECHNICIAN', 'RESPONSABLE_INDUSTRIEL')")
    public ResponseEntity<MaintenanceIntervention> addAction(
            @PathVariable String id,
            @RequestBody MaintenanceAction request,
            @AuthenticationPrincipal UserPrincipal currentUser) {

        String techId = currentUser != null && currentUser.getId() != null ? currentUser.getId() : (currentUser != null ? currentUser.getUsername() : "ANONYMOUS");
        String techName = currentUser != null ? currentUser.getUsername() : "Technicien";
        MaintenanceIntervention updated = interventionService.addAction(id, request, techId, techName, currentUser);
        return ResponseEntity.ok(updated);
    }

    /**
     * Ajoute une observation technique (US47, Section 6.2).
     */
    @PostMapping("/{id}/observations")
    @PreAuthorize("hasAnyRole('ADMIN', 'TECHNICIAN', 'RESPONSABLE_INDUSTRIEL')")
    public ResponseEntity<MaintenanceIntervention> addObservation(
            @PathVariable String id,
            @RequestBody MaintenanceObservation request,
            @AuthenticationPrincipal UserPrincipal currentUser) {

        String authorId = currentUser != null && currentUser.getId() != null ? currentUser.getId() : (currentUser != null ? currentUser.getUsername() : "ANONYMOUS");
        String authorName = currentUser != null ? currentUser.getUsername() : "Auteur";
        MaintenanceIntervention updated = interventionService.addObservation(id, request, authorId, authorName, currentUser);
        return ResponseEntity.ok(updated);
    }

    /**
     * Clôture définitive d'une intervention (US48, Section 6.2).
     */
    @PutMapping("/{id}/close")
    @PreAuthorize("hasAnyRole('ADMIN', 'TECHNICIAN', 'RESPONSABLE_INDUSTRIEL')")
    public ResponseEntity<MaintenanceIntervention> closeIntervention(
            @PathVariable String id,
            @RequestBody(required = false) MaintenanceIntervention request,
            @AuthenticationPrincipal UserPrincipal currentUser) {

        String closedBy = currentUser != null ? currentUser.getUsername() : "ANONYMOUS";
        MaintenanceIntervention closed = interventionService.closeIntervention(id, request, closedBy, currentUser);
        return ResponseEntity.ok(closed);
    }

    /**
     * Formule des recommandations d'interventions basées sur les données machines (US50).
     */
    @GetMapping("/recommendations/{machineId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'RESPONSABLE_INDUSTRIEL', 'TECHNICIAN') or (hasAuthority('MAINTENANCE_READ') and !hasRole('OPERATOR'))")
    public ResponseEntity<List<MaintenanceRecommendationService.Recommendation>> getRecommendations(
            @PathVariable String machineId) {

        List<MaintenanceRecommendationService.Recommendation> recs = recommendationService.generateRecommendations(machineId);
        return ResponseEntity.ok(recs);
    }

    /**
     * Convertit et applique une recommandation en intervention de maintenance réelle (US50, Section 6.1).
     */
    @PostMapping("/recommendations/{machineId}/apply")
    @PreAuthorize("hasAnyRole('RESPONSABLE_INDUSTRIEL', 'ADMIN') or (hasAuthority('MAINTENANCE_WRITE') and !hasRole('TECHNICIAN') and !hasRole('OPERATOR'))")
    public ResponseEntity<MaintenanceIntervention> applyRecommendation(
            @PathVariable String machineId,
            @RequestParam(defaultValue = "0") int index,
            @RequestParam(required = false) String technicianId,
            @AuthenticationPrincipal UserPrincipal currentUser) {

        String username = currentUser != null ? currentUser.getUsername() : "ANONYMOUS";
        MaintenanceIntervention created = recommendationService.createFromRecommendation(machineId, index, technicianId, username);
        return new ResponseEntity<>(created, HttpStatus.CREATED);
    }

    /**
     * Sprint 9 — Suggestions de réparation IA basées sur l'historique d'interventions
     * de la machine. Utilisé par le technicien dans l'écran de détail d'intervention
     * pour obtenir des solutions de réparation prédites.
     */
    @GetMapping("/fixing-suggestions/{machineId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'RESPONSABLE_INDUSTRIEL', 'TECHNICIAN') or (hasAuthority('MAINTENANCE_READ') and !hasRole('OPERATOR'))")
    public ResponseEntity<List<MaintenanceRecommendationService.FixingSuggestion>> getFixingSuggestions(
            @PathVariable String machineId) {

        List<MaintenanceRecommendationService.FixingSuggestion> suggestions = recommendationService.generateFixingSuggestions(machineId);
        return ResponseEntity.ok(suggestions);
    }
}
