import { useMemo } from "react";
import { useNavigate } from "react-router-dom";
import { Chargement, EtatVide, Message } from "../components/Retours";
import { Lampe } from "../components/Lampe";
import { useSupervision } from "../supervision/SupervisionContext";
import { LIBELLE_ETAT, TEINTE_ETAT, alerteOuverte, etatPoste } from "../supervision/etat";
import { TYPE_EQUIPEMENT } from "../supervision/libelles";
import type { Equipement } from "../types/api";

/**
 * Cartographie réseau auto-générée (issue #159) : les nœuds et les arêtes
 * viennent uniquement de la relation `dependDe` déjà portée par chaque
 * équipement (issue #158) — rien n'est saisi ni positionné à la main ici.
 * L'agencement est un simple empilement par niveaux (racine en haut, ce
 * qu'elle dessert en dessous), suffisant pour ce périmètre.
 */

const LARGEUR_NOEUD = 156;
const HAUTEUR_NIVEAU = 100;
const MARGE = 44;
const RAYON = 9;

interface Position {
	equipement: Equipement;
	x: number;
	y: number;
}

interface Arete {
	id: string;
	x1: number;
	y1: number;
	x2: number;
	y2: number;
	risque: boolean;
}

interface Topologie {
	positions: Position[];
	aretes: Arete[];
	largeur: number;
	hauteur: number;
}

/**
 * Regroupe les équipements par niveau de dépendance (BFS depuis les racines,
 * c'est-à-dire les équipements sans `dependDeId` ou dont le parent est
 * introuvable dans le parc courant), puis place chaque niveau sur une rangée.
 * Une boucle est impossible côté serveur (§ anti-cycle d'EquipementService),
 * mais la détection ici reste un garde-fou défensif contre une donnée
 * incohérente plutôt qu'une boucle infinie.
 */
function construireTopologie(equipements: Equipement[]): Topologie {
	const parId = new Map(equipements.map((e) => [e.id, e]));

	const niveauCache = new Map<string, number>();
	function niveauDe(id: string, chemin: Set<string>): number {
		const enCache = niveauCache.get(id);
		if (enCache !== undefined) return enCache;

		const equipement = parId.get(id);
		const parentId = equipement?.dependDeId ?? null;
		if (!parentId || !parId.has(parentId) || chemin.has(id)) {
			niveauCache.set(id, 0);
			return 0;
		}

		const niveau = 1 + niveauDe(parentId, new Set(chemin).add(id));
		niveauCache.set(id, niveau);
		return niveau;
	}

	const parNiveau = new Map<number, Equipement[]>();
	for (const equipement of equipements) {
		const niveau = niveauDe(equipement.id, new Set());
		parNiveau.set(niveau, [...(parNiveau.get(niveau) ?? []), equipement]);
	}

	const niveaux = [...parNiveau.keys()].sort((a, b) => a - b);
	const maxParNiveau = Math.max(1, ...niveaux.map((n) => parNiveau.get(n)!.length));

	const positions = new Map<string, Position>();
	for (const niveau of niveaux) {
		// Rapproche chaque nœud de la position x de son parent déjà placé : ça
		// suffit, à ce périmètre, à limiter les croisements d'arêtes sans un
		// vrai algorithme de layout de graphe.
		const rangee = [...parNiveau.get(niveau)!].sort((a, b) => {
			const xa = positions.get(a.dependDeId ?? "")?.x ?? 0;
			const xb = positions.get(b.dependDeId ?? "")?.x ?? 0;
			return xa - xb || a.nom.localeCompare(b.nom, "fr");
		});

		const largeurRangee = rangee.length * LARGEUR_NOEUD;
		const decalage = (maxParNiveau * LARGEUR_NOEUD - largeurRangee) / 2;
		rangee.forEach((equipement, index) => {
			positions.set(equipement.id, {
				equipement,
				x: MARGE + decalage + index * LARGEUR_NOEUD + LARGEUR_NOEUD / 2,
				y: MARGE + niveau * HAUTEUR_NIVEAU + HAUTEUR_NIVEAU / 2,
			});
		});
	}

	const aretes: Arete[] = [];
	for (const equipement of equipements) {
		if (!equipement.dependDeId) continue;
		const enfant = positions.get(equipement.id);
		const parent = positions.get(equipement.dependDeId);
		if (!enfant || !parent) continue;
		aretes.push({
			id: equipement.id,
			x1: enfant.x,
			y1: enfant.y - RAYON,
			x2: parent.x,
			y2: parent.y + RAYON,
			// Le parent est en panne ou hors ligne : tout ce qui en dépend
			// risque de tomber avec lui — l'arête le signale en rouge pointillé.
			risque: parent.equipement.etat === "INACTIF",
		});
	}

	return {
		positions: [...positions.values()],
		aretes,
		largeur: Math.max(320, MARGE * 2 + maxParNiveau * LARGEUR_NOEUD),
		hauteur: MARGE * 2 + niveaux.length * HAUTEUR_NIVEAU,
	};
}

export function CartographiePage() {
	const { equipements, alertes, chargement, erreur } = useSupervision();
	const navigate = useNavigate();

	const ouvertes = alertes.filter(alerteOuverte);
	const topologie = useMemo(() => construireTopologie(equipements), [equipements]);
	const liens = topologie.aretes.length;

	function ouvrirFiche(id: string) {
		navigate(`/equipements?poste=${id}`);
	}

	return (
		<>
			<section className="section section-premiere">
				<div className="section-entete">
					<h1 className="plaque-titre">Cartographie réseau</h1>
					<span className="donnee-faible">
						{equipements.length} équipement{equipements.length > 1 ? "s" : ""} · {liens} lien
						{liens > 1 ? "s" : ""} de dépendance
					</span>
				</div>
				<p className="etat-vide-texte">
					Générée depuis la relation « Dépend de » de chaque équipement — aucune saisie manuelle. Cliquez sur
					un nœud pour ouvrir sa fiche.
				</p>
			</section>

			{erreur && equipements.length === 0 && (
				<div className="section">
					<Message ton="echec">{erreur}</Message>
				</div>
			)}

			<section className="section">
				<div className="encart carte-cadre">
					{chargement && equipements.length === 0 && <Chargement quoi="du parc" />}

					{!chargement && equipements.length === 0 && !erreur && (
						<EtatVide titre="Parc vide">
							<p className="etat-vide-texte">
								Déclarez un premier équipement pour que la carte ait quelque chose à dessiner.
							</p>
						</EtatVide>
					)}

					{equipements.length > 0 && (
						<svg
							className="carte-svg"
							role="group"
							aria-label="Topologie du parc supervisé"
							width={topologie.largeur}
							height={topologie.hauteur}
							viewBox={`0 0 ${topologie.largeur} ${topologie.hauteur}`}
						>
							{topologie.aretes.map((arete) => (
								<line
									key={arete.id}
									className={arete.risque ? "carte-arete carte-arete-risque" : "carte-arete"}
									x1={arete.x1}
									y1={arete.y1}
									x2={arete.x2}
									y2={arete.y2}
								/>
							))}

							{topologie.positions.map(({ equipement, x, y }) => {
								const etat = etatPoste(equipement, ouvertes);
								return (
									<g
										key={equipement.id}
										className={`carte-noeud carte-noeud-${TEINTE_ETAT[etat]}`}
										transform={`translate(${x}, ${y})`}
										role="button"
										tabIndex={0}
										aria-label={`${equipement.nom}, ${TYPE_EQUIPEMENT[equipement.type]}, ${LIBELLE_ETAT[etat]}${equipement.dependDeNom ? `, dépend de ${equipement.dependDeNom}` : ""}`}
										onClick={() => ouvrirFiche(equipement.id)}
										onKeyDown={(evenement) => {
											if (evenement.key === "Enter" || evenement.key === " ") {
												evenement.preventDefault();
												ouvrirFiche(equipement.id);
											}
										}}
									>
										<title>
											{equipement.nom} — {equipement.adresseIp}
											{equipement.dependDeNom ? ` — dépend de ${equipement.dependDeNom}` : ""}
										</title>
										<circle className="carte-noeud-cercle" r={RAYON} />
										<text className="carte-noeud-nom" y={22}>
											{equipement.nom}
										</text>
										<text className="carte-noeud-genre" y={35}>
											{TYPE_EQUIPEMENT[equipement.type]}
										</text>
									</g>
								);
							})}
						</svg>
					)}
				</div>

				{equipements.length > 0 && liens === 0 && (
					<p className="etat-vide-texte" style={{ marginTop: 10 }}>
						Aucune dépendance déclarée pour l'instant : chaque équipement apparaît isolé. Les arêtes
						apparaissent dès qu'un équipement porte un « dépend de ».
					</p>
				)}

				{equipements.length > 0 && (
					<dl className="legende">
						<div className="legende-entree">
							<dt>
								<Lampe etat="actif" />
							</dt>
							<dd>Nominal</dd>
						</div>
						<div className="legende-entree">
							<dt>
								<Lampe etat="attention" />
							</dt>
							<dd>À surveiller</dd>
						</div>
						<div className="legende-entree">
							<dt>
								<Lampe etat="alarme" />
							</dt>
							<dd>En alarme</dd>
						</div>
						<div className="legende-entree">
							<dt>
								<Lampe etat="eteint" />
							</dt>
							<dd>Hors ligne</dd>
						</div>
						<div className="legende-entree">
							<svg width="22" height="10" aria-hidden="true">
								<line className="carte-arete carte-arete-risque" x1="1" y1="5" x2="21" y2="5" />
							</svg>
							<dd>Dépend d'un équipement hors ligne — risque de cascade</dd>
						</div>
					</dl>
				)}
			</section>
		</>
	);
}
