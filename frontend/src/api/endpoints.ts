import { apiClient } from "./client";
import type {
	Alerte,
	AuthResponse,
	EntreeJournal,
	Equipement,
	EquipementRequest,
	Metrique,
	Rapport,
	RapportGenerateRequest,
	SeuilAlerte,
	SeuilAlerteRequest,
	StatutAlerte,
	Utilisateur,
	UtilisateurCreate,
	UtilisateurUpdate,
} from "../types/api";

export function login(email: string, password: string) {
	return apiClient.post<AuthResponse>("/api/auth/login", { email, password }).then((r) => r.data);
}

export function fetchEquipements() {
	return apiClient.get<Equipement[]>("/api/v1/equipments").then((r) => r.data);
}

export function fetchEquipement(id: string) {
	return apiClient.get<Equipement>(`/api/v1/equipments/${id}`).then((r) => r.data);
}

/** The only response that carries `cleApi` — it is never returned again. */
export function createEquipement(body: EquipementRequest) {
	return apiClient.post<Equipement>("/api/v1/equipments", body).then((r) => r.data);
}

export function updateEquipement(id: string, body: EquipementRequest) {
	return apiClient.put<Equipement>(`/api/v1/equipments/${id}`, body).then((r) => r.data);
}

export function archiveEquipement(id: string) {
	return apiClient.delete<void>(`/api/v1/equipments/${id}`).then(() => undefined);
}

/** Suppression réelle de la ligne — réservée à l'administrateur, refusée si l'équipement conserve un historique. */
export function supprimerEquipementDefinitivement(id: string) {
	return apiClient.delete<void>(`/api/v1/equipments/${id}/definitif`).then(() => undefined);
}

/**
 * Historique borné côté serveur (§7.9). `taille` compte les mesures *tous types
 * confondus* : l'enregistreur en demande large pour garder assez de points sur
 * chacune des métriques une fois le tri fait.
 */
export function fetchEquipementMetriques(equipementId: string, taille?: number) {
	return apiClient
		.get<Metrique[]>(`/api/v1/equipments/${equipementId}/metrics`, {
			params: taille ? { taille } : undefined,
		})
		.then((r) => r.data);
}

export function fetchAlertes(statut?: StatutAlerte) {
	return apiClient
		.get<Alerte[]>("/api/v1/alerts", { params: statut ? { statut } : undefined })
		.then((r) => r.data);
}

export function prendreEnCompteAlerte(id: string) {
	return apiClient.put<Alerte>(`/api/v1/alerts/${id}/acknowledge`).then((r) => r.data);
}

export function resoudreAlerte(id: string) {
	return apiClient.put<Alerte>(`/api/v1/alerts/${id}/resolve`).then((r) => r.data);
}

export function fetchRapports() {
	return apiClient.get<Rapport[]>("/api/v1/reports").then((r) => r.data);
}

export function genererRapport(body: RapportGenerateRequest) {
	return apiClient.post<Rapport>("/api/v1/reports/generate", body).then((r) => r.data);
}

/** Récupère le PDF du rapport. Le jeton est porté par l'intercepteur axios. */
export function telechargerRapport(id: string) {
	return apiClient
		.get(`/api/v1/reports/${id}/download`, { responseType: "blob" })
		.then((r) => r.data as Blob);
}

/** Récupère le CSV du rapport, régénéré à la volée côté serveur. */
export function telechargerRapportCsv(id: string) {
	return apiClient
		.get(`/api/v1/reports/${id}/download-csv`, { responseType: "blob" })
		.then((r) => r.data as Blob);
}

export function fetchSeuils() {
	return apiClient.get<SeuilAlerte[]>("/api/v1/thresholds").then((r) => r.data);
}

export function createSeuil(body: SeuilAlerteRequest) {
	return apiClient.post<SeuilAlerte>("/api/v1/thresholds", body).then((r) => r.data);
}

export function updateSeuil(id: string, body: SeuilAlerteRequest) {
	return apiClient.put<SeuilAlerte>(`/api/v1/thresholds/${id}`, body).then((r) => r.data);
}

export function supprimerSeuil(id: string) {
	return apiClient.delete<void>(`/api/v1/thresholds/${id}`).then(() => undefined);
}

export function fetchJournal() {
	return apiClient.get<EntreeJournal[]>("/api/v1/audit-log").then((r) => r.data);
}

export function fetchUtilisateurs() {
	return apiClient.get<Utilisateur[]>("/api/v1/users").then((r) => r.data);
}

export function createUtilisateur(body: UtilisateurCreate) {
	return apiClient.post<Utilisateur>("/api/v1/users", body).then((r) => r.data);
}

export function updateUtilisateur(id: number, body: UtilisateurUpdate) {
	return apiClient.put<Utilisateur>(`/api/v1/users/${id}`, body).then((r) => r.data);
}

export function desactiverUtilisateur(id: number) {
	return apiClient.delete<void>(`/api/v1/users/${id}`).then(() => undefined);
}
