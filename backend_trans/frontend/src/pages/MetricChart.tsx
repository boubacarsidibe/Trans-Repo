import { useEffect, useMemo, useState } from "react";
import { CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { fetchEquipementMetriques } from "../api/endpoints";
import type { Metrique, TypeMetrique } from "../types/api";

function formatTime(iso: string) {
	return new Date(iso).toLocaleTimeString();
}

export function MetricChart({ equipementId, equipementNom }: { equipementId: string; equipementNom: string }) {
	const [metriques, setMetriques] = useState<Metrique[]>([]);
	const [selectedType, setSelectedType] = useState<TypeMetrique | null>(null);
	const [loading, setLoading] = useState(true);
	const [error, setError] = useState<string | null>(null);

	useEffect(() => {
		setLoading(true);
		setError(null);
		fetchEquipementMetriques(equipementId)
			.then((data) => {
				setMetriques(data);
				const types = Array.from(new Set(data.map((m) => m.typeMetrique)));
				setSelectedType((current) => (current && types.includes(current) ? current : (types[0] ?? null)));
			})
			.catch(() => setError("Impossible de charger les métriques."))
			.finally(() => setLoading(false));
	}, [equipementId]);

	const availableTypes = useMemo(
		() => Array.from(new Set(metriques.map((m) => m.typeMetrique))),
		[metriques],
	);

	const chartData = useMemo(() => {
		return metriques
			.filter((m) => m.typeMetrique === selectedType)
			.slice()
			.sort((a, b) => a.horodatage.localeCompare(b.horodatage))
			.map((m) => ({ time: formatTime(m.horodatage), valeur: m.valeur }));
	}, [metriques, selectedType]);

	if (loading) return <p>Chargement des métriques...</p>;
	if (error) return <p className="error">{error}</p>;
	if (availableTypes.length === 0) return <p>Aucune métrique reçue pour {equipementNom} pour l'instant.</p>;

	return (
		<div className="metric-chart">
			<div className="metric-chart-header">
				<h3>{equipementNom}</h3>
				<select value={selectedType ?? ""} onChange={(e) => setSelectedType(e.target.value as TypeMetrique)}>
					{availableTypes.map((type) => (
						<option key={type} value={type}>
							{type}
						</option>
					))}
				</select>
			</div>
			<ResponsiveContainer width="100%" height={280}>
				<LineChart data={chartData}>
					<CartesianGrid strokeDasharray="3 3" />
					<XAxis dataKey="time" />
					<YAxis unit={metriques.find((m) => m.typeMetrique === selectedType)?.unite ?? ""} />
					<Tooltip />
					<Line type="monotone" dataKey="valeur" stroke="#2563eb" dot={false} />
				</LineChart>
			</ResponsiveContainer>
		</div>
	);
}
