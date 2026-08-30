package com.bouba.backend_trans.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Active les tâches planifiées : watchdog de disponibilité (F3/F4), génération
 * nocturne des rapports (F8) et agrégation des métriques (§6.10).
 */
@Configuration
@EnableScheduling
public class PlanificationConfig {
}
