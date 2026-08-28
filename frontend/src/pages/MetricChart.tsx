import { useEffect, useMemo, useState } from "react";
import {
	CartesianGrid,
	Line,
	LineChart,
	ReferenceLine,
	ResponsiveContainer,
	Tooltip,
	XAxis,
	YAxis,
} from "recharts";
import { fetchEquipementMetriques } from "../api/endpoints";
import { TYPE_METRIQUE } from "../supervision/libelles";
import { useSeuils } from "../supervision/useSeuils";
import { formatHeure, formatValeur } from "../supervision/format";
import type { Metrique, TypeMetrique } from "../types/api";

const INTERVALLE_MS = 5000;
const FENETRE = 60;

/**
 * Un agent remonte une trentaine de métriques par cycle et le tri par type se
 * fait ici : il en faut donc bien plus que FENETRE pour que chaque courbe garde
 * sa fenêtre complète.
 */
const MESURES_DEMANDEES = 2000;

interface Point {
	heure: string;
	valeur: number;
}

function Infobulle({ active, payload, label }: { active?: boolean; payload?: { value: number }[]; label?: string }) {
	if (!active || !payload?.length) return null;
	return (
		<div className="infobulle">
			<div>{label}</div>
			<div>{formatValeur(payload[0].value)}</div>
		</div>
	);
}

/**
 * L'enregistreur. Un seul tracé à la fois — jamais deux échelles sur un même
 * cadre — lu contre les seuils qui déclenchent réellement les alertes.
 */
export function MetricChart({ equipementId, equipementNom }: { equipementId: string; equipementNom: string }) {
	const [metriques, setMetriques] = useState<Metrique[]>([]);
	const [mesure, setMesure] = useState<TypeMetrique | null>(null);
	const [chargement, setChargement] = useState(true);
	const [erreur, setErreur] = useState<string | null>(null);
	const [lecture, setLecture] = useState<Date | null>(null);
	const reperesDe = useSeuils();

	useEffect(() => {
		let annule = false;
		setChargement(true);
		setErreur(null);
		setMetriques([]);

		function lire() {
			if (document.hidden) return;
			fetchEquipementMetriques(equipementId, MESURES_DEMANDEES)
				.then((donnees) => {
					if (annule) return;
					setMetriques(donnees);
					setLecture(new Date());
					setErreur(null);
					const disponibles = [...new Set(donnees.map((m) => m.typeMetrique))];
					setMesure((courante) =>
						courante && disponibles.includes(courante) ? courante : (disponibles[0] ?? null),
					);
				})
				.catch(() => {
					if (!annule) setErreur("Les métriques de cet équipement n'ont pas pu être lues.");
				})
				.finally(() => {
					if (!annule) setChargement(false);
				});
		}

		lire();
		const minuteur = setInterval(lire, INTERVALLE_MS);
		return () => {
			annule = true;
			clearInterval(minuteur);
		};
	}, [equipementId]);

	const mesures = useMemo(() => [...new Set(metriques.map((m) => m.typeMetrique))], [metriques]);

	const serie = useMemo(
		() =>
			metriques
				.filter((m) => m.typeMetrique === mesure)
				.sort((a, b) => a.horodatage.localeCompare(b.horodatage))
				.slice(-FENETRE),
		[metriques, mesure],
	);

	const points: Point[] = serie.map((m) => ({ heure: formatHeure(m.horodatage), valeur: m.valeur }));
	const unite = serie.at(-1)?.unite ?? "";
	const derniere = serie.at(-1)?.valeur;
	const valeurs = serie.map((m) => m.valeur);
	const seuils = mesure ? reperesDe(equipementId, mesure) : undefined;

	if (chargement) return <p className="chargement">Lecture des métriques…</p>;
	if (erreur) return <p className="chargement">{erreur}</p>;

	if (mesures.length === 0) {
		return (
			<div className="enregistreur">
				<h3 className="plaque-titre">Enregistreur</h3>
				<p className="etat-vide-texte">
					Aucune métrique reçue de {equipementNom}. Vérifiez que son agent tourne et qu'il pousse bien vers
					/api/v1/metrics avec la clé de cet équipement.
				</p>
			</div>
		);
	}

	return (
		<div className="enregistreur">
			<h3 className="plaque-titre">Enregistreur</h3>

			<div className="enregistreur-mesures">
				{mesures.map((type) => (
					<button
						key={type}
						className={type === mesure ? "bouton bouton-menu bouton-actif" : "bouton bouton-menu"}
						type="button"
						onClick={() => setMesure(type)}
					>
						{TYPE_METRIQUE[type]}
					</button>
				))}
			</div>

			<div className="enregistreur-entete">
				<span className="enregistreur-valeur">
					{derniere === undefined ? "—" : formatValeur(derniere)}
					<span className="enregistreur-unite">{unite}</span>
				</span>
				<span className="enregistreur-lecture">
					min {formatValeur(Math.min(...valeurs))} · max {formatValeur(Math.max(...valeurs))} ·{" "}
					{valeurs.length} points
				</span>
				<span className="enregistreur-lecture">
					{lecture ? `relevé à ${formatHeure(lecture.toISOString())}` : ""}
				</span>
			</div>

			<ResponsiveContainer width="100%" height={240}>
				<LineChart data={points} margin={{ top: 10, right: 16, bottom: 0, left: 0 }}>
					<CartesianGrid className="trace-grille" vertical={false} />
					<XAxis dataKey="heure" className="trace-axe" tickLine={false} minTickGap={48} />
					<YAxis className="trace-axe" tickLine={false} width={64} unit={unite} />
					{seuils?.attention !== undefined && (
						<ReferenceLine
							y={seuils.attention}
							className="trace-seuil-attention"
							strokeDasharray="4 4"
							ifOverflow="extendDomain"
						/>
					)}
					{seuils?.critique !== undefined && (
						<ReferenceLine
							y={seuils.critique}
							className="trace-seuil-critique"
							strokeDasharray="4 4"
							ifOverflow="extendDomain"
						/>
					)}
					<Tooltip content={<Infobulle />} cursor={{ strokeDasharray: "3 3" }} />
					<Line
						className="trace-ligne"
						type="monotone"
						dataKey="valeur"
						strokeWidth={2}
						dot={false}
						isAnimationActive={false}
					/>
				</LineChart>
			</ResponsiveContainer>

			{(seuils?.attention !== undefined || seuils?.critique !== undefined) && (
				<p className="enregistreur-legende">
					{seuils.attention !== undefined && (
						<span className="enregistreur-seuil seuil-attention">
							<span className="trait-seuil" />
							Avertissement à {seuils.attention}
							{unite}
						</span>
					)}
					{seuils.critique !== undefined && (
						<span className="enregistreur-seuil seuil-critique">
							<span className="trait-seuil" />
							Critique à {seuils.critique}
							{unite}
						</span>
					)}
				</p>
			)}
		</div>
	);
}
