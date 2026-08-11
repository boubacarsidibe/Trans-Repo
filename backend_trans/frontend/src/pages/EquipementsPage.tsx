import { useEffect, useState } from "react";
import { fetchEquipements } from "../api/endpoints";
import type { Equipement } from "../types/api";
import { MetricChart } from "./MetricChart";

export function EquipementsPage() {
	const [equipements, setEquipements] = useState<Equipement[]>([]);
	const [selected, setSelected] = useState<Equipement | null>(null);
	const [loading, setLoading] = useState(true);
	const [error, setError] = useState<string | null>(null);

	useEffect(() => {
		fetchEquipements()
			.then(setEquipements)
			.catch(() => setError("Impossible de charger les équipements (droits insuffisants ?)."))
			.finally(() => setLoading(false));
	}, []);

	if (loading) return <p>Chargement...</p>;
	if (error) return <p className="error">{error}</p>;

	return (
		<div>
			<h2>Équipements</h2>
			<table className="data-table">
				<thead>
					<tr>
						<th>Nom</th>
						<th>Adresse IP</th>
						<th>Type</th>
						<th>État</th>
						<th>Localisation</th>
					</tr>
				</thead>
				<tbody>
					{equipements.map((equipement) => (
						<tr
							key={equipement.id}
							className={selected?.id === equipement.id ? "selected" : undefined}
							onClick={() => setSelected(equipement)}
						>
							<td>{equipement.nom}</td>
							<td>{equipement.adresseIp}</td>
							<td>{equipement.type}</td>
							<td>
								<span className={`badge badge-${equipement.etat.toLowerCase()}`}>{equipement.etat}</span>
							</td>
							<td>{equipement.localisation ?? "—"}</td>
						</tr>
					))}
				</tbody>
			</table>
			{equipements.length === 0 && <p>Aucun équipement enregistré.</p>}

			{selected && <MetricChart equipementId={selected.id} equipementNom={selected.nom} />}
		</div>
	);
}
