import { apiClient } from "./client";
import type { Alerte, AuthResponse, Equipement, Metrique } from "../types/api";

export function login(email: string, password: string) {
	return apiClient.post<AuthResponse>("/api/auth/login", { email, password }).then((r) => r.data);
}

export function fetchEquipements() {
	return apiClient.get<Equipement[]>("/api/v1/equipments").then((r) => r.data);
}

export function fetchEquipementMetriques(equipementId: string) {
	return apiClient.get<Metrique[]>(`/api/v1/equipments/${equipementId}/metrics`).then((r) => r.data);
}

export function fetchAlertes() {
	return apiClient.get<Alerte[]>("/api/v1/alerts").then((r) => r.data);
}
