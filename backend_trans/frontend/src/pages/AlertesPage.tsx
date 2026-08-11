import { useEffect, useState } from "react";
import { fetchAlertes } from "../api/endpoints";
import type { Alerte } from "../types/api";

function formatDate(iso: string) {
	return new Date(iso).toLocaleString();
}

export function AlertesPage() {
	const [alertes, setAlertes] = useState<Alerte[]>([]);
	const [loading, setLoading] = useState(true);
	const [error, setError] = useState<string | null>(null);

	useEffect(() => {
		fetchAlertes()
			.then(setAlertes)
			.catch(() => setError("Impossible de charger les alertes (droits insuffisants ?)."))
			.finally(() => setLoading(false));
	}, []);

	if (loading) return <p>Chargement...</p>;
	if (error) return <p className="error">{error}</p>;

	return (
		<div>
			<h2>Alertes</h2>
			<table className="data-table">
				<thead>
					<tr>
						<th>Équipement</th>
						<th>Anomalie</th>
						<th>Sévérité</th>
						<th>Statut</th>
						<th>Déclenchée le</th>
						<th>Prise en charge</th>
					</tr>
				</thead>
				<tbody>
					{alertes.map((alerte) => (
						<tr key={alerte.id}>
							<td>{alerte.equipementNom}</td>
							<td>{alerte.typeAnomalie}</td>
							<td>
								<span className={`badge badge-severite-${alerte.severite.toLowerCase()}`}>
									{alerte.severite}
								</span>
							</td>
							<td>
								<span className={`badge badge-statut-${alerte.statut.toLowerCase()}`}>{alerte.statut}</span>
							</td>
							<td>{formatDate(alerte.dateDeclenchement)}</td>
							<td>{alerte.utilisateurPriseEnCharge ?? "—"}</td>
						</tr>
					))}
				</tbody>
			</table>
			{alertes.length === 0 && <p>Aucune alerte.</p>}
		</div>
	);
}
