export type Role = "ADMINISTRATEUR" | "TECHNICIEN" | "OBSERVATEUR";

export type UserType = "INDIVIDUAL" | "BUSINESS" | "PARTNER";

export interface AuthResponse {
	id: number;
	username: string;
	email: string;
	role: Role;
	token: string;
	refreshToken: string;
	tokenType: string;
}

export interface Utilisateur {
	id: number;
	username: string;
	email: string;
	role: Role;
	userType: UserType | null;
	active: boolean;
	createdAt: string;
}

export interface UtilisateurCreate {
	username: string;
	email: string;
	password: string;
	role: Role;
	userType?: UserType;
}

export interface UtilisateurUpdate {
	username: string;
	email: string;
	password?: string;
	role: Role;
	userType?: UserType;
	active: boolean;
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
	/** Dernière métrique reçue ; `null` si l'équipement n'a jamais rien remonté. */
	derniereMesure: string | null;
	/** Only returned by POST /equipments, once, at creation. */
	cleApi?: string;
	/** Paramètres SNMP (routeur/switch/point d'accès) ; sans objet pour un serveur. */
	snmpCommunity: string | null;
	snmpPort: number | null;
	interfaceIndex: number | null;
}

export interface EquipementRequest {
	nom: string;
	adresseIp: string;
	type: TypeEquipement;
	localisation?: string | null;
	etat?: EtatEquipement;
	description?: string | null;
	snmpCommunity?: string;
	snmpPort?: number;
	interfaceIndex?: number;
}

export type TypeMetrique =
	| "CPU"
	| "RAM"
	| "DISQUE"
	| "SWAP"
	| "NOMBRE_PROCESSUS"
	| "PORTS_ECOUTE"
	| "DISQUE_IO_LECTURE"
	| "DISQUE_IO_ECRITURE"
	| "RESEAU_IO_ENTRANT"
	| "RESEAU_IO_SORTANT"
	| "UPTIME"
	| "CHARGE_1MIN"
	| "RAM_TOTALE_MO"
	| "RAM_UTILISEE_MO"
	| "DISQUE_TOTAL_GO"
	| "DISQUE_UTILISE_GO"
	| "LIMITE_FICHIERS_OUVERTS"
	| "LIMITE_PROCESSUS"
	| "SERVICES_TCP_INDISPONIBLES"
	| "DNS_LATENCE"
	| "LOG_LIGNES"
	| "LOG_LIGNES_MATCH"
	| "FICHIER_EXISTE"
	| "FICHIER_TAILLE"
	| "TEMPERATURE_MAX"
	| "VENTILATEUR_RPM"
	| "MODBUS_VALEUR"
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

export type TypeAnomalie = "INDISPONIBILITE" | "CPU" | "RAM" | "DISQUE" | "RESEAU" | "MATERIEL";
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

/**
 * Seuil de déclenchement (§11.2). `equipementId` à `null` désigne le défaut
 * global ; renseigné, il surcharge ce seul équipement.
 */
export interface SeuilAlerte {
	id: string;
	typeMetrique: TypeMetrique;
	equipementId: string | null;
	equipementNom: string | null;
	avertissement: number | null;
	critique: number | null;
	/** Durée de maintien exigée avant alerte. 0 = déclenchement instantané. */
	dureeSecondes: number;
}

export interface SeuilAlerteRequest {
	typeMetrique: TypeMetrique;
	equipementId?: string | null;
	avertissement?: number | null;
	critique?: number | null;
	dureeSecondes?: number;
}

export type TypeRapport = "JOURNALIER" | "HEBDOMADAIRE" | "MENSUEL";

export interface Rapport {
	id: string;
	typeRapport: TypeRapport;
	periodeDebut: string;
	periodeFin: string;
	dateGeneration: string;
	fichierDisponible: boolean;
}

export interface RapportGenerateRequest {
	typeRapport: TypeRapport;
	periodeDebut?: string;
	periodeFin?: string;
}

export interface EntreeJournal {
	id: number;
	utilisateurEmail: string;
	action: string;
	horodatage: string;
	adresseIpSource: string | null;
}
