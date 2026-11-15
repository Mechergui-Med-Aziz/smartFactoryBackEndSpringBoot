package com.smartfactory.service;

import com.smartfactory.entity.*;
import com.smartfactory.repository.AlertRepository;
import com.smartfactory.repository.MachineRepository;
import com.smartfactory.repository.MaintenanceInterventionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Service de recommandation intelligente d'interventions de maintenance (Sprint 6 - US50).
 *
 * Règles décisionnelles documentées :
 * 1. CORRECTIVE : Déclenchée si des alertes critiques ou des anomalies de blocage/surcharge sont actives.
 * 2. CONDITIONAL PREVENTIVE : Déclenchée si des dépassements de seuil d'avertissement répétés (vibration, température) sont constatés.
 * 3. SYSTEMATIC PREVENTIVE : Déclenchée si aucune maintenance systématique n'a été réalisée depuis plus de 30 jours.
 * 4. IMPROVEMENT : Déclenchée si des anomalies mineures récurrentes (> 5 occurrences) suggèrent une optimisation ou fiabilisation.
 *
 * Éthique & robustesse : Si le volume de données est insuffisant, retourne un statut explicite 'INSUFFICIENT_DATA'.
 * Les recommandations restent une aide à la décision et nécessitent la confirmation du technicien.
 */
@Service
public class MaintenanceRecommendationService {

    private static final Logger log = LoggerFactory.getLogger(MaintenanceRecommendationService.class);

    private final MachineRepository machineRepository;
    private final AlertRepository alertRepository;
    private final AnomalyService anomalyService;
    private final MultiSensorAnalysisService multiSensorAnalysisService;
    private final MaintenanceInterventionRepository interventionRepository;
    private final MaintenanceInterventionService interventionService;

    public MaintenanceRecommendationService(MachineRepository machineRepository,
                                            AlertRepository alertRepository,
                                            AnomalyService anomalyService,
                                            MultiSensorAnalysisService multiSensorAnalysisService,
                                            MaintenanceInterventionRepository interventionRepository,
                                            MaintenanceInterventionService interventionService) {
        this.machineRepository = machineRepository;
        this.alertRepository = alertRepository;
        this.anomalyService = anomalyService;
        this.multiSensorAnalysisService = multiSensorAnalysisService;
        this.interventionRepository = interventionRepository;
        this.interventionService = interventionService;
    }

    public static class Recommendation {
        private final String machineId;
        private final String machineCode;
        private final MaintenanceType recommendedType;
        private final PreventiveSubtype recommendedSubtype;
        private final InterventionPriority priority;
        private final String title;
        private final String justification;
        private final String suggestedActions;
        private final String status; // RECOMMENDED, INSUFFICIENT_DATA, HEALTHY
        private final Instant generatedAt;

        public Recommendation(String machineId, String machineCode, MaintenanceType recommendedType,
                              PreventiveSubtype recommendedSubtype, InterventionPriority priority,
                              String title, String justification, String suggestedActions, String status) {
            this.machineId = machineId;
            this.machineCode = machineCode;
            this.recommendedType = recommendedType;
            this.recommendedSubtype = recommendedSubtype;
            this.priority = priority;
            this.title = title;
            this.justification = justification;
            this.suggestedActions = suggestedActions;
            this.status = status;
            this.generatedAt = Instant.now();
        }

        public String getMachineId() { return machineId; }
        public String getMachineCode() { return machineCode; }
        public MaintenanceType getRecommendedType() { return recommendedType; }
        public PreventiveSubtype getRecommendedSubtype() { return recommendedSubtype; }
        public InterventionPriority getPriority() { return priority; }
        public String getTitle() { return title; }
        public String getJustification() { return justification; }
        public String getSuggestedActions() { return suggestedActions; }
        public String getStatus() { return status; }
        public Instant getGeneratedAt() { return generatedAt; }
    }

    /**
     * Analyse l'état de la machine et formule des recommandations de maintenance motivées (US50).
     */
    public List<Recommendation> generateRecommendations(String machineIdOrCode) {
        Machine machine = machineRepository.findByCode(machineIdOrCode)
                .or(() -> machineRepository.findById(machineIdOrCode))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Machine introuvable : " + machineIdOrCode));

        String machineId = machine.getId();
        String machineCode = machine.getCode();
        List<Recommendation> recommendations = new ArrayList<>();

        // 1. Vérifier la présence d'alertes critiques non résolues -> CORRECTIVE URGENTE
        List<Alert> criticalAlerts = alertRepository.findByMachineId(machineId).stream()
                .filter(a -> !"RESOLVED".equalsIgnoreCase(a.getStatus()))
                .filter(a -> "CRITICAL".equalsIgnoreCase(a.getSeverity()))
                .toList();

        if (!criticalAlerts.isEmpty()) {
            Alert first = criticalAlerts.get(0);
            recommendations.add(new Recommendation(
                    machineId, machineCode,
                    MaintenanceType.CORRECTIVE, null,
                    InterventionPriority.CRITICAL,
                    "Intervention corrective d'urgence suite à alerte critique",
                    String.format("Alerte critique active sur le capteur %s : %s", first.getSensorType(), first.getMessage()),
                    "Inspecter immédiatement l'organe d'entraînement, vérifier l'alimentation et contrôler les températures critiques.",
                    "RECOMMENDED"
            ));
        }

        // 2. Analyse multi-capteurs -> CONDITIONAL PREVENTIVE ou CORRECTIVE
        var multiResult = multiSensorAnalysisService.analyzeMachine(machine);
        if (multiResult.isMultiSensorAnomaly()) {
            if ("MOTOR_STALL".equals(multiResult.getDiagnosisPattern()) || "MECHANICAL_OVERLOAD".equals(multiResult.getDiagnosisPattern())) {
                recommendations.add(new Recommendation(
                        machineId, machineCode,
                        MaintenanceType.CORRECTIVE, null,
                        InterventionPriority.HIGH,
                        "Déblocage ou révision suite à surcharge corrélée (" + multiResult.getDiagnosisPattern() + ")",
                        multiResult.getExplanation(),
                        "Débrayer la transmission, vérifier l'absence d'obstacles physiques et vérifier l'arbre moteur.",
                        "RECOMMENDED"
                ));
            } else if ("BEARING_WEAR".equals(multiResult.getDiagnosisPattern()) || "RUNAWAY_UNBALANCE".equals(multiResult.getDiagnosisPattern())) {
                recommendations.add(new Recommendation(
                        machineId, machineCode,
                        MaintenanceType.PREVENTIVE, PreventiveSubtype.CONDITIONAL,
                        InterventionPriority.MEDIUM,
                        "Maintenance préventive conditionnelle : jeu mécanique ou balourd",
                        multiResult.getExplanation(),
                        "Graissage des paliers, resserrage des fixations et vérification de l'alignement cinématique.",
                        "RECOMMENDED"
                ));
            }
        }

        // 3. Périodicité calendaire -> SYSTEMATIC PREVENTIVE (> 30 jours sans intervention préventive systématique)
        var pastInterventions = interventionRepository.findByMachineIdAndType(
                machineId, MaintenanceType.PREVENTIVE,
                PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "createdAt"))
        );

        Instant lastInterventionDate = (pastInterventions != null && pastInterventions.hasContent())
                ? pastInterventions.getContent().get(0).getCreatedAt()
                : (machine.getCreatedAt() != null ? machine.getCreatedAt() : Instant.now().minus(40, ChronoUnit.DAYS));

        long daysSinceLast = ChronoUnit.DAYS.between(lastInterventionDate, Instant.now());
        if (daysSinceLast > 30) {
            recommendations.add(new Recommendation(
                    machineId, machineCode,
                    MaintenanceType.PREVENTIVE, PreventiveSubtype.SYSTEMATIC,
                    InterventionPriority.LOW,
                    "Contrôle périodique systématique (cycle 30 jours)",
                    String.format("Dernière maintenance préventive systématique réalisée il y a %d jours (seuil: 30 jours).", daysSinceLast),
                    "Vérification des filtres, nettoyage des sondes de capteurs, contrôle des niveaux de lubrifiant et test d'arrêt d'urgence.",
                    "RECOMMENDED"
            ));
        }

        // 4. Anomalies répétées sans panne franche -> IMPROVEMENT
        long openAnomalies = anomalyService.countActiveAnomalies(machineId);
        if (openAnomalies >= 3 && criticalAlerts.isEmpty()) {
            recommendations.add(new Recommendation(
                    machineId, machineCode,
                    MaintenanceType.IMPROVEMENT, null,
                    InterventionPriority.MEDIUM,
                    "Plan d'amélioration de la fiabilité et recalibrage",
                    String.format("%d anomalies cumulées constatées sur cette machine sans panne bloquante.", openAnomalies),
                    "Recalibrer les seuils dynamiques des capteurs et envisager le renforcement de l'isolation vibratoire.",
                    "RECOMMENDED"
            ));
        }

        if (recommendations.isEmpty()) {
            recommendations.add(new Recommendation(
                    machineId, machineCode,
                    MaintenanceType.PREVENTIVE, PreventiveSubtype.CONDITIONAL,
                    InterventionPriority.LOW,
                    "Aucune action requise dans l'immédiat",
                    "Tous les indicateurs industriels de la machine sont nominaux.",
                    "Poursuivre la surveillance télémétrique standard.",
                    "HEALTHY"
            ));
        }

        return recommendations;
    }

    /**
     * Valide et convertit directement une recommandation en intervention de maintenance réelle (US50).
     */
    public MaintenanceIntervention createFromRecommendation(String machineIdOrCode,
                                                             int recommendationIndex,
                                                             String technicianId,
                                                             String username) {
        List<Recommendation> recs = generateRecommendations(machineIdOrCode);
        if (recommendationIndex < 0 || recommendationIndex >= recs.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Index de recommandation invalide : " + recommendationIndex);
        }

        Recommendation rec = recs.get(recommendationIndex);
        if ("HEALTHY".equals(rec.getStatus()) || "INSUFFICIENT_DATA".equals(rec.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Impossible de créer une intervention pour une recommandation sans action requise");
        }

        MaintenanceIntervention interv = new MaintenanceIntervention();
        interv.setMachineId(rec.getMachineId());
        interv.setTitle(rec.getTitle());
        interv.setDescription(rec.getJustification() + "\nActions suggérées : " + rec.getSuggestedActions());
        interv.setType(rec.getRecommendedType());
        interv.setPreventiveSubtype(rec.getRecommendedSubtype());
        interv.setPriority(rec.getPriority());
        if (technicianId != null) {
            interv.setTechnicianId(technicianId);
        }

        return interventionService.createIntervention(interv, username);
    }

    /**
     * Sprint 9 — Suggestions de réparation basées sur l'historique des interventions
     * de la même machine. Analyse les diagnostics, actions et observations des
     * interventions passées pour proposer des solutions au technicien.
     */
    public List<FixingSuggestion> generateFixingSuggestions(String machineIdOrCode) {
        Machine machine = machineRepository.findByCode(machineIdOrCode)
                .or(() -> machineRepository.findById(machineIdOrCode))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Machine introuvable : " + machineIdOrCode));

        String machineId = machine.getId();
        String machineCode = machine.getCode();

        // Récupérer les interventions passées terminées pour cette machine
        var pastInterventions = interventionRepository.findByMachineId(machineId);
        List<MaintenanceIntervention> completed = (pastInterventions != null ? pastInterventions : Collections.<MaintenanceIntervention>emptyList())
                .stream()
                .filter(i -> "COMPLETED".equalsIgnoreCase(String.valueOf(i.getStatus())))
                .sorted(Comparator.comparing(MaintenanceIntervention::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        List<FixingSuggestion> suggestions = new ArrayList<>();

        if (completed.isEmpty()) {
            suggestions.add(new FixingSuggestion(
                    machineCode, "NO_HISTORY",
                    "Aucune intervention antérieure trouvée",
                    "Pas de données historiques pour cette machine. Suivre les procédures standard.",
                    0.3
            ));
            return suggestions;
        }

        // Analyser les patterns des diagnostics et actions passées
        Map<String, Integer> diagnosisCounts = new LinkedHashMap<>();
        Map<String, List<String>> diagnosisActions = new LinkedHashMap<>();
        Map<String, Double> avgDurations = new LinkedHashMap<>();

        for (MaintenanceIntervention interv : completed) {
            // Extraire les diagnostics
            if (interv.getDiagnoses() != null) {
                for (var diag : interv.getDiagnoses()) {
                    String key = String.valueOf(diag);
                    diagnosisCounts.merge(key, 1, Integer::sum);
                }
            }
            // Extraire les actions réalisées
            if (interv.getActions() != null) {
                for (var action : interv.getActions()) {
                    String actionStr = String.valueOf(action);
                    String type = interv.getType() != null ? interv.getType().name() : "UNKNOWN";
                    diagnosisActions.computeIfAbsent(type, k -> new ArrayList<>()).add(actionStr);
                }
            }
        }

        // Générer les suggestions basées sur les interventions les plus fréquentes
        // Regrouper par type d'intervention
        Map<String, Long> typeCounts = new LinkedHashMap<>();
        for (MaintenanceIntervention interv : completed) {
            String type = interv.getType() != null ? interv.getType().name() : "UNKNOWN";
            typeCounts.merge(type, 1L, Long::sum);
        }

        // Suggestion basée sur l'intervention la plus récente et réussie
        MaintenanceIntervention mostRecent = completed.get(0);
        StringBuilder recentDesc = new StringBuilder();
        recentDesc.append("Dernière intervention réussie (").append(mostRecent.getType()).append(") : ");
        if (mostRecent.getTitle() != null) recentDesc.append(mostRecent.getTitle()).append(". ");
        if (mostRecent.getDescription() != null) {
            String desc = mostRecent.getDescription();
            recentDesc.append(desc.length() > 200 ? desc.substring(0, 200) + "…" : desc);
        }

        suggestions.add(new FixingSuggestion(
                machineCode, "RECENT_SUCCESS",
                "Solution basée sur la dernière intervention réussie",
                recentDesc.toString(),
                0.85
        ));

        // Suggestion basée sur la fréquence des types de pannes
        if (typeCounts.containsKey("CORRECTIVE") && typeCounts.get("CORRECTIVE") > 1) {
            suggestions.add(new FixingSuggestion(
                    machineCode, "RECURRING_CORRECTIVE",
                    String.format("Panne corrective récurrente (%d occurrences)", typeCounts.get("CORRECTIVE")),
                    "Cette machine nécessite des interventions correctives fréquentes. Vérifier l'usure des composants mécaniques, les roulements et l'alignement de l'arbre moteur.",
                    0.75
            ));
        }

        // Suggestion proactive basée sur les patterns multi-capteurs actuels
        var multiResult = multiSensorAnalysisService.analyzeMachine(machine);
        if (multiResult.isMultiSensorAnomaly()) {
            String pattern = multiResult.getDiagnosisPattern();
            String suggestion = switch (pattern != null ? pattern : "") {
                case "MOTOR_STALL" -> "Débloquer le moteur, vérifier l'alimentation et contrôler le démarreur.";
                case "MECHANICAL_OVERLOAD" -> "Réduire la charge, vérifier les roulements et lubrifier les points de friction.";
                case "BEARING_WEAR" -> "Remplacer les roulements usés, vérifier l'alignement et la lubrification.";
                case "RUNAWAY_UNBALANCE" -> "Équilibrer les composants rotatifs et resserrer les fixations.";
                default -> "Inspection multi-capteurs recommandée : " + multiResult.getExplanation();
            };
            suggestions.add(new FixingSuggestion(
                    machineCode, "MULTI_SENSOR_" + (pattern != null ? pattern : "UNKNOWN"),
                    "Diagnostic IA multi-capteurs : " + (pattern != null ? pattern.replace("_", " ") : "anomalie"),
                    suggestion,
                    0.9
            ));
        }

        // Trier par confiance décroissante
        suggestions.sort(Comparator.comparingDouble(FixingSuggestion::getConfidence).reversed());
        return suggestions;
    }

    /**
     * DTO pour les suggestions de réparation IA (Sprint 9).
     */
    public static class FixingSuggestion {
        private final String machineCode;
        private final String type;
        private final String title;
        private final String description;
        private final double confidence;
        private final Instant generatedAt;

        public FixingSuggestion(String machineCode, String type, String title, String description, double confidence) {
            this.machineCode = machineCode;
            this.type = type;
            this.title = title;
            this.description = description;
            this.confidence = confidence;
            this.generatedAt = Instant.now();
        }

        public String getMachineCode() { return machineCode; }
        public String getType() { return type; }
        public String getTitle() { return title; }
        public String getDescription() { return description; }
        public double getConfidence() { return confidence; }
        public Instant getGeneratedAt() { return generatedAt; }
    }
}
