/**
 * Dev-only fixture backend, active when VITE_MOCK_API=1.
 *
 * Lets the console be run, reviewed and demonstrated without Spring Boot and
 * PostgreSQL. Mutations are kept in memory so acknowledging an alert or adding
 * an equipment behaves like the real thing for the length of a session.
 */
import type { AxiosAdapter, AxiosResponse, InternalAxiosRequestConfig } from "axios";
import type {
	Alerte,
	EntreeJournal,
	Equipement,
	Metrique,
	Rapport,
	StatutAlerte,
	Utilisateur,
} from "../types/api";

const equipements: Equipement[] = (
	[
	{ id: "e1", nom: "srv-peda-01", adresseIp: "10.20.4.11", type: "SERVEUR", localisation: "Salle serveurs — CRI", etat: "ACTIF", description: "Hyperviseur des TP réseau" },
	{ id: "e2", nom: "srv-peda-02", adresseIp: "10.20.4.12", type: "SERVEUR", localisation: "Salle serveurs — CRI", etat: "ACTIF", description: "Hyperviseur des TP système" },
	{ id: "e3", nom: "srv-moodle", adresseIp: "10.20.4.20", type: "SERVEUR", localisation: "Salle serveurs — CRI", etat: "ACTIF", description: "Plateforme pédagogique" },
	{ id: "e4", nom: "srv-dns-01", adresseIp: "10.20.4.53", type: "SERVEUR", localisation: "Salle serveurs — CRI", etat: "EN_MAINTENANCE", description: "Résolveur DNS interne" },
	{ id: "e5", nom: "srv-fichiers", adresseIp: "10.20.4.30", type: "SERVEUR", localisation: "Salle serveurs — CRI", etat: "ACTIF", description: null },
	{ id: "e6", nom: "rt-core-01", adresseIp: "10.20.0.1", type: "ROUTEUR", localisation: "Local technique — cœur", etat: "ACTIF", description: "Routeur de cœur, VLAN 1-40" },
	{ id: "e7", nom: "sw-core-01", adresseIp: "10.20.0.2", type: "SWITCH", localisation: "Local technique — cœur", etat: "ACTIF", description: null },
	{ id: "e8", nom: "sw-core-02", adresseIp: "10.20.0.3", type: "SWITCH", localisation: "Local technique — cœur", etat: "INACTIF", description: "Ne répond plus depuis la coupure du 09/08" },
	{ id: "e9", nom: "sw-git-01", adresseIp: "10.20.12.2", type: "SWITCH", localisation: "Bâtiment GIT", etat: "ACTIF", description: null },
	{ id: "e10", nom: "ap-git-r1", adresseIp: "10.20.12.21", type: "POINT_ACCES", localisation: "Bâtiment GIT", etat: "ACTIF", description: "Amphi 1" },
	{ id: "e11", nom: "ap-git-r2", adresseIp: "10.20.12.22", type: "POINT_ACCES", localisation: "Bâtiment GIT", etat: "ACTIF", description: "Salle TP réseau" },
	{ id: "e12", nom: "ap-admin-01", adresseIp: "10.20.30.21", type: "POINT_ACCES", localisation: "Bâtiment administration", etat: "ACTIF", description: null },
	] as Omit<Equipement, "derniereMesure">[]
).map((equipement) => ({
	...equipement,
	// Le parc de démonstration remonte normalement ; seul l'équipement archivé
	// reste muet, ce qui laisse voir le comportement du watchdog.
	derniereMesure: equipement.etat === "INACTIF" ? null : new Date(Date.now() - 20_000).toISOString(),
}));

const minutesAgo = (n: number) => new Date(Date.now() - n * 60_000).toISOString();

const alertes: Alerte[] = [
	{ id: "a1", equipementId: "e3", equipementNom: "srv-moodle", typeAnomalie: "CPU", severite: "CRITIQUE", statut: "DECLENCHEE", dateDeclenchement: minutesAgo(6), dateResolution: null, utilisateurPriseEnCharge: null },
	{ id: "a2", equipementId: "e8", equipementNom: "sw-core-02", typeAnomalie: "INDISPONIBILITE", severite: "CRITIQUE", statut: "DECLENCHEE", dateDeclenchement: minutesAgo(74), dateResolution: null, utilisateurPriseEnCharge: null },
	{ id: "a3", equipementId: "e1", equipementNom: "srv-peda-01", typeAnomalie: "DISQUE", severite: "AVERTISSEMENT", statut: "PRISE_EN_COMPTE", dateDeclenchement: minutesAgo(212), dateResolution: null, utilisateurPriseEnCharge: "k.dieng@ept.sn" },
	{ id: "a4", equipementId: "e6", equipementNom: "rt-core-01", typeAnomalie: "RESEAU", severite: "AVERTISSEMENT", statut: "RESOLUE", dateDeclenchement: minutesAgo(880), dateResolution: minutesAgo(840), utilisateurPriseEnCharge: "b.sidibe@ept.sn" },
	{ id: "a5", equipementId: "e2", equipementNom: "srv-peda-02", typeAnomalie: "RAM", severite: "INFO", statut: "RESOLUE", dateDeclenchement: minutesAgo(1500), dateResolution: minutesAgo(1470), utilisateurPriseEnCharge: "b.sidibe@ept.sn" },
];

/** A plausible trace: a slow wave plus noise, so the chart reads like real telemetry. */
function serie(type: Metrique["typeMetrique"], unite: string, base: number, amplitude: number, points = 40): Metrique[] {
	return Array.from({ length: points }, (_, i) => ({
		id: i,
		typeMetrique: type,
		unite,
		horodatage: minutesAgo((points - i) * 2),
		valeur: Number(
			Math.max(0, base + Math.sin(i / 4.5) * amplitude + (Math.sin(i * 12.9898) * 43758.5453) % 4).toFixed(1),
		),
	}));
}

const metriques: Record<string, Metrique[]> = {
	e3: [...serie("CPU", "%", 78, 16), ...serie("RAM", "%", 61, 9), ...serie("DISQUE", "%", 44, 3)],
	e1: [...serie("CPU", "%", 34, 12), ...serie("RAM", "%", 57, 8), ...serie("DISQUE", "%", 86, 4)],
	e6: [...serie("LATENCE", "ms", 42, 26), ...serie("TAUX_ERREUR", "%", 0.4, 0.3), ...serie("BANDE_PASSANTE", "Mb/s", 210, 90)],
};

const rapports: Rapport[] = [
	{ id: "r1", typeRapport: "JOURNALIER", periodeDebut: minutesAgo(1440), periodeFin: minutesAgo(0), dateGeneration: minutesAgo(30), fichierDisponible: false },
	{ id: "r2", typeRapport: "HEBDOMADAIRE", periodeDebut: minutesAgo(10080), periodeFin: minutesAgo(1440), dateGeneration: minutesAgo(1430), fichierDisponible: false },
];

const journal: EntreeJournal[] = [
	{ id: 5, utilisateurEmail: "b.sidibe@ept.sn", action: "CONNEXION", horodatage: minutesAgo(4), adresseIpSource: "10.20.12.104" },
	{ id: 4, utilisateurEmail: "k.dieng@ept.sn", action: "ALERTE_PRISE_EN_COMPTE id=a3", horodatage: minutesAgo(205), adresseIpSource: "10.20.4.2" },
	{ id: 3, utilisateurEmail: "b.sidibe@ept.sn", action: "EQUIPEMENT_CREE nom=ap-admin-01", horodatage: minutesAgo(1320), adresseIpSource: "10.20.12.104" },
	{ id: 2, utilisateurEmail: "admin@ept.sn", action: "UTILISATEUR_CREE email=k.dieng@ept.sn", horodatage: minutesAgo(4300), adresseIpSource: "10.20.4.2" },
	{ id: 1, utilisateurEmail: "admin@ept.sn", action: "ECHEC_CONNEXION email=inconnu@ept.sn", horodatage: minutesAgo(4380), adresseIpSource: "41.82.14.77" },
];

const utilisateurs: Utilisateur[] = [
	{ id: 1, username: "admin", email: "admin@ept.sn", role: "ADMINISTRATEUR", userType: null, active: true, createdAt: minutesAgo(43200) },
	{ id: 2, username: "Boubacar Sidibé", email: "b.sidibe@ept.sn", role: "ADMINISTRATEUR", userType: null, active: true, createdAt: minutesAgo(20160) },
	{ id: 3, username: "Khadija Dieng", email: "k.dieng@ept.sn", role: "TECHNICIEN", userType: null, active: true, createdAt: minutesAgo(20160) },
	{ id: 4, username: "Poste accueil CRI", email: "accueil@ept.sn", role: "OBSERVATEUR", userType: null, active: false, createdAt: minutesAgo(8640) },
];

function roleDepuisEmail(email: string) {
	if (email.startsWith("obs")) return "OBSERVATEUR" as const;
	if (email.startsWith("tech")) return "TECHNICIEN" as const;
	return "ADMINISTRATEUR" as const;
}

function reponse<T>(config: InternalAxiosRequestConfig, data: T, status = 200): AxiosResponse<T> {
	return { data, status, statusText: "OK", headers: {}, config };
}

/* eslint-disable-next-line @typescript-eslint/no-explicit-any */
function echec(status: number, message: string): Promise<never> {
	const error = new Error(message) as Error & { response?: unknown; isAxiosError?: boolean };
	error.isAxiosError = true;
	error.response = { status, data: { message } };
	return Promise.reject(error);
}

export const mockAdapter: AxiosAdapter = async (config) => {
	const url = (config.url ?? "").replace(config.baseURL ?? "", "");
	const method = (config.method ?? "get").toLowerCase();
	const body = typeof config.data === "string" ? JSON.parse(config.data || "{}") : (config.data ?? {});
	await new Promise((r) => setTimeout(r, 180));

	if (url === "/api/auth/login" || url === "/api/auth/refresh") {
		const email: string = body.email ?? "admin@ept.sn";
		if (url === "/api/auth/login" && String(body.password ?? "").length < 4) {
			return echec(401, "Identifiants refusés.");
		}
		return reponse(config, {
			id: 1,
			username: email.split("@")[0],
			email,
			role: roleDepuisEmail(email),
			token: "jeton-de-demonstration",
			refreshToken: "jeton-de-renouvellement",
			tokenType: "Bearer",
		});
	}

	const metriquesMatch = url.match(/^\/api\/v1\/equipments\/([^/]+)\/metrics$/);
	if (metriquesMatch) return reponse(config, metriques[metriquesMatch[1]] ?? []);

	if (url === "/api/v1/equipments") {
		if (method === "post") {
			const cree: Equipement = { ...body, id: `e${equipements.length + 1}`, etat: body.etat ?? "ACTIF", cleApi: crypto.randomUUID() };
			equipements.push(cree);
			return reponse(config, cree, 201);
		}
		return reponse(config, equipements);
	}

	const equipementMatch = url.match(/^\/api\/v1\/equipments\/([^/]+)$/);
	if (equipementMatch) {
		const index = equipements.findIndex((e) => e.id === equipementMatch[1]);
		if (index < 0) return echec(404, "Équipement introuvable.");
		if (method === "put") {
			equipements[index] = { ...equipements[index], ...body };
			return reponse(config, equipements[index]);
		}
		if (method === "delete") {
			equipements.splice(index, 1);
			return reponse(config, undefined, 204);
		}
		return reponse(config, equipements[index]);
	}

	if (url.startsWith("/api/v1/alerts")) {
		const acquitte = url.match(/^\/api\/v1\/alerts\/([^/]+)\/acknowledge$/);
		const resolue = url.match(/^\/api\/v1\/alerts\/([^/]+)\/resolve$/);
		const cible = alertes.find((a) => a.id === (acquitte?.[1] ?? resolue?.[1]));
		if (acquitte && cible) {
			cible.statut = "PRISE_EN_COMPTE";
			cible.utilisateurPriseEnCharge = "b.sidibe@ept.sn";
			return reponse(config, cible);
		}
		if (resolue && cible) {
			cible.statut = "RESOLUE";
			cible.dateResolution = new Date().toISOString();
			return reponse(config, cible);
		}
		const statut = config.params?.statut as StatutAlerte | undefined;
		return reponse(config, statut ? alertes.filter((a) => a.statut === statut) : alertes);
	}

	if (url === "/api/v1/reports") return reponse(config, rapports);
	if (url === "/api/v1/reports/generate") {
		const cree: Rapport = {
			id: `r${rapports.length + 1}`,
			typeRapport: body.typeRapport,
			periodeDebut: body.periodeDebut ?? minutesAgo(1440),
			periodeFin: body.periodeFin ?? new Date().toISOString(),
			dateGeneration: new Date().toISOString(),
			fichierDisponible: false,
		};
		rapports.unshift(cree);
		return reponse(config, cree, 201);
	}

	if (url === "/api/v1/audit-log") return reponse(config, journal);

	if (url === "/api/v1/users") {
		if (method === "post") {
			const cree: Utilisateur = {
				id: utilisateurs.length + 1,
				username: body.username,
				email: body.email,
				role: body.role,
				userType: body.userType ?? null,
				active: true,
				createdAt: new Date().toISOString(),
			};
			utilisateurs.push(cree);
			return reponse(config, cree, 201);
		}
		return reponse(config, utilisateurs);
	}

	const utilisateurMatch = url.match(/^\/api\/v1\/users\/(\d+)$/);
	if (utilisateurMatch) {
		const cible = utilisateurs.find((u) => u.id === Number(utilisateurMatch[1]));
		if (!cible) return echec(404, "Utilisateur introuvable.");
		if (method === "delete") {
			cible.active = false;
			return reponse(config, undefined, 204);
		}
		Object.assign(cible, { username: body.username, email: body.email, role: body.role, active: body.active });
		return reponse(config, cible);
	}

	return echec(404, `Route non simulée : ${method.toUpperCase()} ${url}`);
};
