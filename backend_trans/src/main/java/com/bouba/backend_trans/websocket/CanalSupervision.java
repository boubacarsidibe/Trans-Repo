package com.bouba.backend_trans.websocket;

/**
 * Les trois points de terminaison temps réel du cahier de spécifications (§8.2).
 */
public enum CanalSupervision {

	METRICS("/ws/metrics"),
	ALERTS("/ws/alerts"),
	STATUS("/ws/status");

	private final String chemin;

	CanalSupervision(String chemin) {
		this.chemin = chemin;
	}

	public String getChemin() {
		return chemin;
	}

	/**
	 * Retrouve le canal à partir du chemin de la requête d'ouverture. Renvoie
	 * {@code null} pour un chemin inconnu : la session est alors refusée plutôt
	 * que rattachée à un canal arbitraire.
	 */
	public static CanalSupervision parChemin(String chemin) {
		if (chemin == null) {
			return null;
		}
		for (CanalSupervision canal : values()) {
			if (canal.chemin.equals(chemin)) {
				return canal;
			}
		}
		return null;
	}
}
