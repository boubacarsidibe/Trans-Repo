package com.bouba.backend_trans.equipement.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "equipements")
public class Equipement {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false, length = 150)
	private String nom;

	@Column(name = "adresse_ip", nullable = false, unique = true, length = 45)
	private String adresseIp;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private TypeEquipement type;

	@Column(length = 150)
	private String localisation;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private EtatEquipement etat = EtatEquipement.ACTIF;

	@Column(columnDefinition = "TEXT")
	private String description;

	@Column(name = "cle_api", length = 255)
	private String cleApi;

	/**
	 * Paramètres SNMP (routeur/switch/point d'accès uniquement). Renseignés une
	 * fois dans la fiche de l'équipement, ils sont ensuite lus automatiquement
	 * par le collecteur réseau via {@code GET /api/v1/agents/self} — plus besoin
	 * de les recopier à la main dans un fichier de configuration séparé.
	 */
	@Column(name = "snmp_community", length = 100)
	private String snmpCommunity;

	@Column(name = "snmp_port")
	private Integer snmpPort;

	@Column(name = "interface_index")
	private Integer interfaceIndex;

	/**
	 * Équipement dont celui-ci dépend pour être joignable — typiquement le
	 * commutateur ou le routeur qui le dessert.
	 *
	 * <p>Quand ce parent tombe, tout ce qui se trouve derrière devient
	 * injoignable en même temps. Sans cette relation, une seule panne de
	 * commutateur produit vingt alertes d'indisponibilité et noie l'alerte
	 * réelle — exactement le « faux positif qui noie les équipes » que le cahier
	 * des charges veut éviter (§1.2).
	 */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "depend_de_id")
	private Equipement dependDe;

	/**
	 * Horodatage de la dernière métrique reçue, tenu à jour à l'ingestion.
	 *
	 * <p>C'est ce qui permet au watchdog de disponibilité (règles F3 et F4) de
	 * repérer un équipement muet par une simple lecture de cette table, sans
	 * balayer la table des métriques — qui atteint plusieurs centaines de
	 * millions de lignes à 90 jours de rétention (§6.10).
	 *
	 * <p>{@code null} signifie « n'a jamais rien remonté » : un équipement
	 * déclaré mais pas encore équipé d'agent ne doit pas sonner l'alarme.
	 */
	@Column(name = "derniere_mesure")
	private LocalDateTime derniereMesure;

	public UUID getId() {
		return id;
	}

	public void setId(UUID id) {
		this.id = id;
	}

	public String getNom() {
		return nom;
	}

	public void setNom(String nom) {
		this.nom = nom;
	}

	public String getAdresseIp() {
		return adresseIp;
	}

	public void setAdresseIp(String adresseIp) {
		this.adresseIp = adresseIp;
	}

	public TypeEquipement getType() {
		return type;
	}

	public void setType(TypeEquipement type) {
		this.type = type;
	}

	public String getLocalisation() {
		return localisation;
	}

	public void setLocalisation(String localisation) {
		this.localisation = localisation;
	}

	public EtatEquipement getEtat() {
		return etat;
	}

	public void setEtat(EtatEquipement etat) {
		this.etat = etat;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getCleApi() {
		return cleApi;
	}

	public void setCleApi(String cleApi) {
		this.cleApi = cleApi;
	}

	public String getSnmpCommunity() {
		return snmpCommunity;
	}

	public void setSnmpCommunity(String snmpCommunity) {
		this.snmpCommunity = snmpCommunity;
	}

	public Integer getSnmpPort() {
		return snmpPort;
	}

	public void setSnmpPort(Integer snmpPort) {
		this.snmpPort = snmpPort;
	}

	public Integer getInterfaceIndex() {
		return interfaceIndex;
	}

	public void setInterfaceIndex(Integer interfaceIndex) {
		this.interfaceIndex = interfaceIndex;
	}

	public Equipement getDependDe() {
		return dependDe;
	}

	public void setDependDe(Equipement dependDe) {
		this.dependDe = dependDe;
	}

	public LocalDateTime getDerniereMesure() {
		return derniereMesure;
	}

	public void setDerniereMesure(LocalDateTime derniereMesure) {
		this.derniereMesure = derniereMesure;
	}
}
