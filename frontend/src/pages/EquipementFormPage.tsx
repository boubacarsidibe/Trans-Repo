import { useEffect, useState, type FormEvent } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { BASE_URL } from "../api/client";
import { createEquipement, fetchEquipement, updateEquipement } from "../api/endpoints";
import { Champ } from "../components/Champ";
import { Chargement, Message } from "../components/Retours";
import { useSupervision } from "../supervision/SupervisionContext";
import { ETAT_EQUIPEMENT, TYPE_EQUIPEMENT } from "../supervision/libelles";
import type { Equipement, EtatEquipement, TypeEquipement } from "../types/api";

const VIDE = {
	nom: "",
	adresseIp: "",
	type: "SERVEUR" as TypeEquipement,
	etat: "ACTIF" as EtatEquipement,
	localisation: "",
	description: "",
	snmpCommunity: "public",
	snmpPort: "161",
	interfaceIndex: "1",
};

export function EquipementFormPage() {
	const { id } = useParams();
	const navigate = useNavigate();
	const { rafraichir } = useSupervision();
	const [saisie, setSaisie] = useState(VIDE);
	const [chargement, setChargement] = useState(Boolean(id));
	const [envoi, setEnvoi] = useState(false);
	const [erreur, setErreur] = useState<string | null>(null);
	const [cree, setCree] = useState<Equipement | null>(null);

	useEffect(() => {
		if (!id) return;
		fetchEquipement(id)
			.then((poste) =>
				setSaisie({
					nom: poste.nom,
					adresseIp: poste.adresseIp,
					type: poste.type,
					etat: poste.etat,
					localisation: poste.localisation ?? "",
					description: poste.description ?? "",
					snmpCommunity: poste.snmpCommunity ?? "public",
					snmpPort: String(poste.snmpPort ?? 161),
					interfaceIndex: String(poste.interfaceIndex ?? 1),
				}),
			)
			.catch(() => setErreur("Cet équipement n'a pas pu être lu."))
			.finally(() => setChargement(false));
	}, [id]);

	function modifier<C extends keyof typeof VIDE>(champ: C, valeur: (typeof VIDE)[C]) {
		setSaisie((actuelle) => ({ ...actuelle, [champ]: valeur }));
	}

	async function enregistrer(evenement: FormEvent) {
		evenement.preventDefault();
		setEnvoi(true);
		setErreur(null);
		const reseau = saisie.type !== "SERVEUR";
		const corps = {
			...saisie,
			localisation: saisie.localisation.trim() || null,
			description: saisie.description.trim() || null,
			snmpCommunity: reseau ? saisie.snmpCommunity.trim() || undefined : undefined,
			snmpPort: reseau ? Number(saisie.snmpPort) || undefined : undefined,
			interfaceIndex: reseau ? Number(saisie.interfaceIndex) || undefined : undefined,
		};
		try {
			if (id) {
				await updateEquipement(id, corps);
				rafraichir();
				navigate(`/equipements?poste=${id}`);
			} else {
				setCree(await createEquipement(corps));
				rafraichir();
			}
		} catch (cause) {
			const statut = (cause as { response?: { status?: number } })?.response?.status;
			setErreur(
				statut === 403
					? "Votre rôle ne permet pas de modifier le parc."
					: "L'enregistrement a échoué. Vérifiez le nom et l'adresse IP, puis réessayez.",
			);
		} finally {
			setEnvoi(false);
		}
	}

	if (chargement) return <Chargement quoi="de la fiche" />;
	if (cree) return <CleGeneree poste={cree} />;

	return (
		<section className="section section-premiere">
			<div className="section-entete">
				<h1 className="plaque-titre">{id ? `Modifier ${saisie.nom}` : "Déclarer un équipement"}</h1>
			</div>

			<form className="encart formulaire" onSubmit={enregistrer}>
				<div className="grille-champs">
					<Champ libelle="Nom" aide="Le nom porté sur l'étiquette de la baie.">
						{(champ) => (
							<input
								className="champ-saisie"
								id={champ}
								value={saisie.nom}
								onChange={(e) => modifier("nom", e.target.value)}
								required
							/>
						)}
					</Champ>

					<Champ libelle="Adresse IP">
						{(champ) => (
							<input
								className="champ-saisie"
								id={champ}
								value={saisie.adresseIp}
								onChange={(e) => modifier("adresseIp", e.target.value)}
								placeholder="10.20.0.1"
								required
							/>
						)}
					</Champ>

					<Champ libelle="Nature">
						{(champ) => (
							<select
								className="champ-saisie"
								id={champ}
								value={saisie.type}
								onChange={(e) => modifier("type", e.target.value as TypeEquipement)}
							>
								{Object.entries(TYPE_EQUIPEMENT).map(([valeur, libelle]) => (
									<option key={valeur} value={valeur}>
										{libelle}
									</option>
								))}
							</select>
						)}
					</Champ>

					<Champ libelle="État">
						{(champ) => (
							<select
								className="champ-saisie"
								id={champ}
								value={saisie.etat}
								onChange={(e) => modifier("etat", e.target.value as EtatEquipement)}
							>
								{Object.entries(ETAT_EQUIPEMENT).map(([valeur, libelle]) => (
									<option key={valeur} value={valeur}>
										{libelle}
									</option>
								))}
							</select>
						)}
					</Champ>

					<Champ libelle="Emplacement" aide="Regroupe les lampes du bandeau.">
						{(champ) => (
							<input
								className="champ-saisie"
								id={champ}
								value={saisie.localisation}
								onChange={(e) => modifier("localisation", e.target.value)}
								placeholder="Salle serveurs — CRI"
							/>
						)}
					</Champ>

					<Champ libelle="Description">
						{(champ) => (
							<input
								className="champ-saisie"
								id={champ}
								value={saisie.description}
								onChange={(e) => modifier("description", e.target.value)}
							/>
						)}
					</Champ>

					{saisie.type !== "SERVEUR" && (
						<>
							<Champ libelle="Communauté SNMP">
								{(champ) => (
									<input
										className="champ-saisie"
										id={champ}
										value={saisie.snmpCommunity}
										onChange={(e) => modifier("snmpCommunity", e.target.value)}
										placeholder="public"
									/>
								)}
							</Champ>

							<Champ libelle="Port SNMP">
								{(champ) => (
									<input
										className="champ-saisie"
										id={champ}
										type="number"
										min={1}
										max={65535}
										value={saisie.snmpPort}
										onChange={(e) => modifier("snmpPort", e.target.value)}
									/>
								)}
							</Champ>

							<Champ libelle="Index d'interface" aide="Index SNMP de l'interface à surveiller (ifTable).">
								{(champ) => (
									<input
										className="champ-saisie"
										id={champ}
										type="number"
										min={1}
										value={saisie.interfaceIndex}
										onChange={(e) => modifier("interfaceIndex", e.target.value)}
									/>
								)}
							</Champ>
						</>
					)}
				</div>

				{erreur && (
					<div className="formulaire-pied">
						<Message ton="echec">{erreur}</Message>
					</div>
				)}

				<div className="formulaire-pied">
					<button className="bouton bouton-principal" type="submit" disabled={envoi}>
						{envoi ? "Enregistrement…" : id ? "Enregistrer les modifications" : "Déclarer l'équipement"}
					</button>
					<Link className="bouton" to="/equipements">
						Annuler
					</Link>
				</div>
			</form>
		</section>
	);
}

/**
 * La clé n'est renvoyée qu'à la création. On la montre une fois — c'est le
 * seul moment où elle peut encore être copiée.
 *
 * Pour un équipement réseau, c'est tout ce qu'il y a à transporter : le
 * collecteur relit ensuite lui-même l'IP et les paramètres SNMP de cette
 * fiche à chaque cycle (`GET /api/v1/agents/self`), donc les modifier ici
 * plus tard suffit — rien à resynchroniser côté agent.
 */
function CleGeneree({ poste }: { poste: Equipement }) {
	const [copie, setCopie] = useState(false);
	const agentSysteme = poste.type === "SERVEUR";

	const configuration = agentSysteme
		? `BACKEND_URL=${BASE_URL}\nEQUIPMENT_ID=${poste.id}\nAPI_KEY=${poste.cleApi}\nINTERVAL_SECONDS=60`
		: poste.cleApi ?? "";

	return (
		<section className="section section-premiere">
			<div className="section-entete">
				<h1 className="plaque-titre">{poste.nom} est déclaré</h1>
			</div>

			<div className="encart cle">
				<p className="etat-vide-titre">Clé d'accès de l'agent</p>
				<code className="cle-valeur">{poste.cleApi}</code>
				<p className="champ-aide">
					Copiez-la maintenant : la plateforme ne l'affichera plus. Pour en obtenir une autre, il faudra
					redéclarer l'équipement.
				</p>

				<p className="etat-vide-titre" style={{ marginTop: 22 }}>
					{agentSysteme
						? "Fichier agent/system/.env"
						: "À ajouter à la liste agent/network/equipments.json"}
				</p>
				<code className="cle-commande">{configuration}</code>

				<div className="commandes">
					<button
						className="bouton"
						type="button"
						onClick={() => {
							void navigator.clipboard.writeText(configuration);
							setCopie(true);
						}}
					>
						{copie ? "Configuration copiée" : "Copier la configuration"}
					</button>
					<Link className="bouton bouton-principal" to={`/equipements?poste=${poste.id}`}>
						Ouvrir la fiche
					</Link>
				</div>
			</div>
		</section>
	);
}
