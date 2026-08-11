export type Role =
	| "ADMIN"
	| "MANAGER"
	| "OPERATOR"
	| "CLIENT"
	| "ADMINISTRATEUR"
	| "TECHNICIEN"
	| "OBSERVATEUR";

export interface AuthResponse {
	id: number;
	username: string;
	email: string;
	role: Role;
	token: string;
	refreshToken: string;
	tokenType: string;
}

export type TypeEquipement = "SERVEUR" | "ROUTEUR" | "SWITCH" | "POINT_ACCES";
export type EtatEquipement = "ACTIF" | "INACTIF" | "EN_MAINTENANCE";

export interface Equipement {
	id: string;
	nom: string;
	adresseIp: string;
	type: TypeEquipement;
	localisation: string | null;
	etat: EtatEquipement;
	description: string | null;
}

export type TypeMetrique =
	| "CPU"
	| "RAM"
	| "DISQUE"
	| "BANDE_PASSANTE"
	| "LATENCE"
	| "TAUX_ERREUR"
	| "DISPONIBILITE";

export interface Metrique {
	id: number;
	typeMetrique: TypeMetrique;
	valeur: number;
	unite: string;
	horodatage: string;
}

export type TypeAnomalie = "INDISPONIBILITE" | "CPU" | "RAM" | "DISQUE" | "RESEAU";
export type Severite = "INFO" | "AVERTISSEMENT" | "CRITIQUE";
export type StatutAlerte = "DECLENCHEE" | "PRISE_EN_COMPTE" | "RESOLUE";

export interface Alerte {
	id: string;
	equipementId: string;
	equipementNom: string;
	typeAnomalie: TypeAnomalie;
	severite: Severite;
	statut: StatutAlerte;
	dateDeclenchement: string;
	dateResolution: string | null;
	utilisateurPriseEnCharge: string | null;
}
