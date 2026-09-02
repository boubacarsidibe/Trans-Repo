import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend } from 'k6/metrics';

// ---------------------------------------------------------------------------
// Test de charge — lecture synoptique + alertes
//   GET /api/v1/equipments
//   GET /api/v1/alerts
//   GET /api/v1/equipments/{id}/metrics
//
// Ce trio est ce qu'un tableau de bord type exécute à chaque rafraîchissement
// (liste des équipements du synoptique, alertes actives, historique de
// métriques de l'équipement sélectionné).
//
// Authentification : JWT (compte OBSERVATEUR, TECHNICIEN ou ADMINISTRATEUR —
// les trois rôles ont accès en lecture à ces trois endpoints), obtenu une
// seule fois dans setup() via POST /api/auth/login puis réutilisé par tous
// les VUs pendant tout le test.
//
// ATTENTION : le jeton expire après jwt.expiration-ms (15 min par défaut,
// cf. backend_trans/src/main/resources/application.properties). Garder
// RAMP_DURATION*2 + HOLD_DURATION nettement sous ce seuil, ou relever
// JWT_EXPIRATION_MS côté backend pour un test plus long.
//
// Variables d'environnement :
//   BASE_URL        URL du backend (défaut: http://localhost:8080)
//   LOGIN_EMAIL     e-mail d'un compte existant (un OBSERVATEUR suffit)
//   LOGIN_PASSWORD  mot de passe de ce compte
//   VUS_MAX         nombre max de VUs simultanés (défaut: 20)
//   RAMP_DURATION   durée de montée/descente en charge (défaut: 30s)
//   HOLD_DURATION   durée du palier à charge max (défaut: 2m)
// ---------------------------------------------------------------------------

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const VUS_MAX = parseInt(__ENV.VUS_MAX || '20', 10);
const RAMP_DURATION = __ENV.RAMP_DURATION || '30s';
const HOLD_DURATION = __ENV.HOLD_DURATION || '2m';

export const options = {
  stages: [
    { duration: RAMP_DURATION, target: VUS_MAX },
    { duration: HOLD_DURATION, target: VUS_MAX },
    { duration: RAMP_DURATION, target: 0 },
  ],
  thresholds: {
    // Lecture paginée/triée, potentiellement sur un historique conséquent
    // (rétention 90 j, taille de page plafonnée côté contrôleur) : tolérance
    // un peu plus large qu'à l'écriture, tout en restant sous le seuil
    // généralement admis pour qu'un rafraîchissement de tableau de bord soit
    // perçu comme immédiat par l'opérateur en salle de supervision.
    http_req_duration: ['p(95)<300', 'p(99)<800'],
    http_req_failed: ['rate<0.01'],
    list_equipments_duration: ['p(95)<300'],
    list_alerts_duration: ['p(95)<300'],
    equipment_metrics_duration: ['p(95)<300'],
  },
};

const listEquipmentsDuration = new Trend('list_equipments_duration');
const listAlertsDuration = new Trend('list_alerts_duration');
const equipmentMetricsDuration = new Trend('equipment_metrics_duration');

export function setup() {
  if (!__ENV.LOGIN_EMAIL || !__ENV.LOGIN_PASSWORD) {
    throw new Error(
      'Définir LOGIN_EMAIL et LOGIN_PASSWORD (compte existant, OBSERVATEUR suffit). Voir load-tests/README.md.'
    );
  }
  const res = http.post(
    `${BASE_URL}/api/auth/login`,
    JSON.stringify({ email: __ENV.LOGIN_EMAIL, password: __ENV.LOGIN_PASSWORD }),
    { headers: { 'Content-Type': 'application/json' } }
  );
  if (res.status !== 200) {
    throw new Error(`Échec du login (setup) : HTTP ${res.status} — ${res.body}`);
  }
  const token = res.json('token');
  if (!token) {
    throw new Error('Réponse de login sans champ "token" — vérifier LOGIN_EMAIL/LOGIN_PASSWORD.');
  }
  return { token };
}

export default function (data) {
  const headers = { Authorization: `Bearer ${data.token}` };

  const equipmentsRes = http.get(`${BASE_URL}/api/v1/equipments`, {
    headers,
    tags: { name: 'GET /api/v1/equipments' },
  });
  listEquipmentsDuration.add(equipmentsRes.timings.duration);
  const equipmentsOk = check(equipmentsRes, { 'equipments: 200 OK': (r) => r.status === 200 });

  const alertsRes = http.get(`${BASE_URL}/api/v1/alerts?taille=50`, {
    headers,
    tags: { name: 'GET /api/v1/alerts' },
  });
  listAlertsDuration.add(alertsRes.timings.duration);
  check(alertsRes, { 'alerts: 200 OK': (r) => r.status === 200 });

  if (equipmentsOk) {
    let equipments = [];
    try {
      equipments = equipmentsRes.json();
    } catch (e) {
      equipments = [];
    }
    if (Array.isArray(equipments) && equipments.length > 0) {
      const pick = equipments[Math.floor(Math.random() * equipments.length)];
      const metricsRes = http.get(`${BASE_URL}/api/v1/equipments/${pick.id}/metrics`, {
        headers,
        tags: { name: 'GET /api/v1/equipments/{id}/metrics' },
      });
      equipmentMetricsDuration.add(metricsRes.timings.duration);
      check(metricsRes, { 'equipment metrics: 200 OK': (r) => r.status === 200 });
    }
  }

  sleep(2);
}
