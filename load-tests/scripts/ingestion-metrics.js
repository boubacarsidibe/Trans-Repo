import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Rate } from 'k6/metrics';

// ---------------------------------------------------------------------------
// Test de charge — ingestion de métriques
//   POST /api/v1/metrics/system
//   POST /api/v1/metrics/network
//
// Simule des agents de supervision qui poussent leurs métriques périodiques,
// comme le fait le vrai agent de collecte (cf. dossier `agent/`).
//
// Authentification : clé API par équipement, en-tête X-API-Key — conforme à
// ApiKeyAuthenticationFilter (backend_trans/.../equipement/security). Le
// contrôleur vérifie en plus que la clé correspond bien à l'equipment_id du
// corps de la requête (verifyAgentOwnsEquipment) : une clé et un
// equipment_id désassortis produisent un 403, pas un 201.
//
// Prérequis : au moins un équipement existant en base, avec une clé API
// (cleApi) non vide et un état différent de INACTIF. Voir le README.md du
// dossier load-tests/ pour la procédure de création (login admin puis
// POST /api/v1/equipments).
//
// Variables d'environnement :
//   BASE_URL        URL du backend (défaut: http://localhost:8080)
//   AGENTS          JSON: [{"equipmentId":"<uuid>","apiKey":"<clé>"}, ...]
//                   Si absent, retombe sur EQUIPMENT_ID + API_KEY (un seul
//                   agent, réutilisé par tous les VUs — suffisant pour
//                   mesurer la capacité de l'endpoint, mais ne teste pas
//                   l'isolation entre équipements distincts).
//   EQUIPMENT_ID    UUID d'un équipement (fallback si AGENTS absent)
//   API_KEY         clé API de cet équipement (fallback si AGENTS absent)
//   VUS_MAX         nombre max de VUs simultanés (défaut: 20)
//   RAMP_DURATION   durée de montée/descente en charge (défaut: 30s)
//   HOLD_DURATION   durée du palier à charge max (défaut: 2m)
// ---------------------------------------------------------------------------

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const VUS_MAX = parseInt(__ENV.VUS_MAX || '20', 10);
const RAMP_DURATION = __ENV.RAMP_DURATION || '30s';
const HOLD_DURATION = __ENV.HOLD_DURATION || '2m';

function loadAgents() {
  if (__ENV.AGENTS) {
    let parsed;
    try {
      parsed = JSON.parse(__ENV.AGENTS);
    } catch (e) {
      throw new Error(`AGENTS n'est pas un JSON valide : ${e.message}`);
    }
    if (!Array.isArray(parsed) || parsed.length === 0) {
      throw new Error('AGENTS doit être un tableau JSON non vide de {equipmentId, apiKey}.');
    }
    return parsed;
  }
  if (!__ENV.EQUIPMENT_ID || !__ENV.API_KEY) {
    throw new Error(
      'Définir soit AGENTS (JSON), soit EQUIPMENT_ID + API_KEY. Voir load-tests/README.md.'
    );
  }
  return [{ equipmentId: __ENV.EQUIPMENT_ID, apiKey: __ENV.API_KEY }];
}

const AGENTS = loadAgents();

export const options = {
  stages: [
    { duration: RAMP_DURATION, target: VUS_MAX },
    { duration: HOLD_DURATION, target: VUS_MAX },
    { duration: RAMP_DURATION, target: 0 },
  ],
  thresholds: {
    // Écriture unique, payload léger, pas de jointure : on vise une marge
    // confortable par rapport au cycle de collecte réel (60 s, propriété
    // app.collecte.intervalle-secondes) et au délai de détection du
    // watchdog (30 s, app.watchdog.periode-ms) — l'ingestion ne doit jamais
    // devenir le facteur limitant de ces cycles.
    http_req_duration: ['p(95)<200', 'p(99)<500'],
    http_req_failed: ['rate<0.01'],
    ingest_system_duration: ['p(95)<200'],
    ingest_network_duration: ['p(95)<200'],
  },
};

const systemDuration = new Trend('ingest_system_duration');
const networkDuration = new Trend('ingest_network_duration');
const errorRate = new Rate('ingest_errors');

function randomBetween(min, max) {
  return Math.round((Math.random() * (max - min) + min) * 100) / 100;
}

function buildSystemPayload(equipmentId) {
  return JSON.stringify({
    equipment_id: equipmentId,
    cpu_percent: randomBetween(5, 95),
    memory_percent: randomBetween(10, 90),
    disk_percent: randomBetween(10, 85),
    swap_percent: randomBetween(0, 20),
    process_count: randomBetween(80, 400),
    listening_ports_count: randomBetween(5, 40),
    disk_read_kbps: randomBetween(0, 5000),
    disk_write_kbps: randomBetween(0, 5000),
    network_in_kbps: randomBetween(0, 10000),
    network_out_kbps: randomBetween(0, 10000),
    uptime_seconds: randomBetween(1000, 5000000),
    load_1min: randomBetween(0, 4),
    memory_total_mb: 8192,
    memory_used_mb: randomBetween(1000, 7000),
    disk_total_gb: 100,
    disk_used_gb: randomBetween(10, 90),
  });
}

function buildNetworkPayload(equipmentId) {
  return JSON.stringify({
    equipment_id: equipmentId,
    bandwidth_mbps: randomBetween(1, 1000),
    latency_ms: randomBetween(0.5, 50),
    error_rate_percent: randomBetween(0, 2),
    uptime_seconds: randomBetween(1000, 5000000),
    interface_up: 1,
  });
}

export default function () {
  const agent = AGENTS[(__VU - 1) % AGENTS.length];
  const headers = {
    'Content-Type': 'application/json',
    'X-API-Key': agent.apiKey,
  };

  const systemRes = http.post(
    `${BASE_URL}/api/v1/metrics/system`,
    buildSystemPayload(agent.equipmentId),
    { headers, tags: { name: 'POST /api/v1/metrics/system' } }
  );
  systemDuration.add(systemRes.timings.duration);
  const systemOk = check(systemRes, {
    'system: 201 Created': (r) => r.status === 201,
  });
  errorRate.add(!systemOk);

  const networkRes = http.post(
    `${BASE_URL}/api/v1/metrics/network`,
    buildNetworkPayload(agent.equipmentId),
    { headers, tags: { name: 'POST /api/v1/metrics/network' } }
  );
  networkDuration.add(networkRes.timings.duration);
  const networkOk = check(networkRes, {
    'network: 201 Created': (r) => r.status === 201,
  });
  errorRate.add(!networkOk);

  sleep(1);
}
