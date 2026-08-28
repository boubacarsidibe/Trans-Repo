import type {
	EtatEquipement,
	Role,
	Severite,
	StatutAlerte,
	TypeAnomalie,
	TypeEquipement,
	TypeMetrique,
	TypeRapport,
} from "../types/api";

/*
 * Le vocabulaire de l'interface, en un seul endroit. Une action et l'état
 * qu'elle produit partagent le même mot : « Prendre en compte » → « Prise en
 * compte », « Résoudre » → « Résolue », « Archiver » → « Archivé ».
 */

export const TYPE_EQUIPEMENT: Record<TypeEquipement, string> = {
	SERVEUR: "Serveur",
	ROUTEUR: "Routeur",
	SWITCH: "Commutateur",
	POINT_ACCES: "Point d'accès",
};

export const ETAT_EQUIPEMENT: Record<EtatEquipement, string> = {
	ACTIF: "Actif",
	INACTIF: "Inactif",
	EN_MAINTENANCE: "En maintenance",
};

export const STATUT_ALERTE: Record<StatutAlerte, string> = {
	DECLENCHEE: "Déclenchée",
	PRISE_EN_COMPTE: "Prise en compte",
	RESOLUE: "Résolue",
};

export const SEVERITE: Record<Severite, string> = {
	INFO: "Info",
	AVERTISSEMENT: "Avertissement",
	CRITIQUE: "Critique",
};

export const TYPE_ANOMALIE: Record<TypeAnomalie, string> = {
	INDISPONIBILITE: "Indisponibilité",
	CPU: "Charge CPU",
	RAM: "Mémoire",
	DISQUE: "Disque",
	RESEAU: "Réseau",
	MATERIEL: "Matériel",
};

export const TYPE_RAPPORT: Record<TypeRapport, string> = {
	JOURNALIER: "Journalier",
	HEBDOMADAIRE: "Hebdomadaire",
	MENSUEL: "Mensuel",
};

export const ROLE: Record<Role, string> = {
	ADMIN: "Administrateur",
	ADMINISTRATEUR: "Administrateur",
	TECHNICIEN: "Technicien",
	OBSERVATEUR: "Observateur",
	MANAGER: "Manager",
	OPERATOR: "Opérateur",
	CLIENT: "Client",
};

export const TYPE_METRIQUE: Record<TypeMetrique, string> = {
	CPU: "CPU",
	RAM: "RAM",
	DISQUE: "Disque",
	SWAP: "Swap",
	NOMBRE_PROCESSUS: "Processus",
	PORTS_ECOUTE: "Ports en écoute",
	DISQUE_IO_LECTURE: "Lecture disque",
	DISQUE_IO_ECRITURE: "Écriture disque",
	RESEAU_IO_ENTRANT: "Réseau entrant",
	RESEAU_IO_SORTANT: "Réseau sortant",
	UPTIME: "Temps de fonctionnement",
	CHARGE_1MIN: "Charge 1 min",
	RAM_TOTALE_MO: "RAM totale",
	RAM_UTILISEE_MO: "RAM utilisée",
	DISQUE_TOTAL_GO: "Disque total",
	DISQUE_UTILISE_GO: "Disque utilisé",
	LIMITE_FICHIERS_OUVERTS: "Limite fichiers ouverts",
	LIMITE_PROCESSUS: "Limite processus",
	SERVICES_TCP_INDISPONIBLES: "Services TCP injoignables",
	DNS_LATENCE: "Latence DNS",
	LOG_LIGNES: "Lignes de log",
	LOG_LIGNES_MATCH: "Lignes filtrées",
	FICHIER_EXISTE: "Présence fichier",
	FICHIER_TAILLE: "Taille fichier",
	TEMPERATURE_MAX: "Température max",
	VENTILATEUR_RPM: "Ventilateur",
	MODBUS_VALEUR: "Valeur Modbus",
	BANDE_PASSANTE: "Bande passante",
	LATENCE: "Latence",
	TAUX_ERREUR: "Taux d'erreur",
	DISPONIBILITE: "Disponibilité",
};

/**
 * Seuils **par défaut** du §11.2, utilisés comme repères de tracé.
 *
 * Depuis que les seuils sont configurables en base et surchargeables par
 * équipement, ces valeurs ne sont plus que les défauts d'usine : un réglage
 * fait par un administrateur n'est pas reflété ici. L'écran de configuration
 * (/seuils) fait foi.
 */
export const SEUILS: Partial<Record<TypeMetrique, { attention: number; critique: number }>> = {
	CPU: { attention: 80, critique: 95 },
	RAM: { attention: 80, critique: 95 },
	DISQUE: { attention: 85, critique: 95 },
	LATENCE: { attention: 150, critique: 400 },
	TAUX_ERREUR: { attention: 1, critique: 5 },
};

export const estAdministrateur = (role?: string) => role === "ADMIN" || role === "ADMINISTRATEUR";

/** Qui peut agir sur le parc : acquitter, créer, modifier, archiver. */
export const peutIntervenir = (role?: string) => estAdministrateur(role) || role === "TECHNICIEN";
