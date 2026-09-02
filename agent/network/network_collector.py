"""Collecteur reseau (SNMP + ICMP) pour le PFE Monitoring EPT.

Contrairement a l'agent systeme (installe sur chaque serveur), ce collecteur
est centralise : un seul processus sonde a distance tous les equipements
reseau (routeurs, switches, points d'acces) definis dans equipments.json,
puis pousse les resultats vers le backend (POST /api/v1/metrics/network),
authentifie par la cle API propre a chaque equipement (en-tete X-API-Key).

SNMP (v2c) donne la bande passante, le taux d'erreur et l'etat de
l'interface via les compteurs MIB-II (ifTable), ainsi que l'uptime
systeme (sysUpTime). ICMP donne la latence et la disponibilite au niveau
reseau (independante de l'etat administratif de l'interface).

En plus du polling periodique, un recepteur de traps SNMP (v1/v2c) tourne
en tache de fond : a la reception d'un trap (linkDown/linkUp, coldStart,
...) depuis un equipement declare dans equipments.json, l'equipement
concerne est sonde immediatement (meme pipeline que le cycle normal)
plutot que d'attendre le prochain cycle -- voir handle_trap().
"""

import asyncio
import contextlib
import json
import logging
import os
import signal
import sys
import threading
import time
from dataclasses import dataclass

import requests
from dotenv import load_dotenv
from icmplib import ping as icmp_ping
from pysnmp.carrier.asyncio.dgram import udp as trap_udp
from pysnmp.entity import config as trap_config
from pysnmp.entity.rfc3413 import ntfrcv
from pysnmp.hlapi.v3arch.asyncio import (
    CommunityData,
    ContextData,
    ObjectIdentity,
    ObjectType,
    SnmpEngine,
    UdpTransportTarget,
    get_cmd,
)

load_dotenv()

logger = logging.getLogger("network_collector")

OID_TEMPLATES = {
    "in_octets": "1.3.6.1.2.1.2.2.1.10.{index}",
    "out_octets": "1.3.6.1.2.1.2.2.1.16.{index}",
    "in_errors": "1.3.6.1.2.1.2.2.1.14.{index}",
    "out_errors": "1.3.6.1.2.1.2.2.1.20.{index}",
    "in_packets": "1.3.6.1.2.1.2.2.1.11.{index}",
    "out_packets": "1.3.6.1.2.1.2.2.1.17.{index}",
    # ifOperStatus (MIB-II) : 1 = up, tout le reste (down, testing, ...) compte comme indisponible.
    "if_oper_status": "1.3.6.1.2.1.2.2.1.8.{index}",
    # sysUpTime (MIB-II) : pas indexe par interface, {index} reste tel quel dans le gabarit.
    "sys_up_time": "1.3.6.1.2.1.1.3.0",
}

# snmpTrapOID.0 (SNMPv2-MIB) : porte le type du trap dans les varbinds de la notification.
SNMP_TRAP_OID = "1.3.6.1.6.3.1.1.4.1.0"

# Traps generiques standard (RFC 1215 / SNMPv2-MIB), pour un log lisible.
# Tout trap non repertorie ici est traite de la meme facon (sondage immediat) ;
# cette table ne sert qu'a l'affichage du nom dans les logs.
STANDARD_TRAP_NAMES = {
    "1.3.6.1.6.3.1.1.5.1": "coldStart",
    "1.3.6.1.6.3.1.1.5.2": "warmStart",
    "1.3.6.1.6.3.1.1.5.3": "linkDown",
    "1.3.6.1.6.3.1.1.5.4": "linkUp",
    "1.3.6.1.6.3.1.1.5.5": "authenticationFailure",
}


@dataclass
class CollectorConfig:
    backend_url: str
    equipments_config_path: str
    interval_seconds: int
    ping_timeout_seconds: float
    snmp_timeout_seconds: float
    request_timeout_seconds: int
    trap_enabled: bool = True
    trap_bind_address: str = "0.0.0.0"
    trap_port: int = 1162
    trap_community: str = "public"


@dataclass
class NetworkEquipment:
    nom: str
    equipment_id: str
    api_key: str
    ip_address: str
    snmp_community: str
    snmp_port: int
    interface_index: int


def load_config() -> CollectorConfig:
    return CollectorConfig(
        backend_url=os.environ.get("BACKEND_URL", "http://localhost:8080").rstrip("/"),
        equipments_config_path=os.environ.get("EQUIPMENTS_CONFIG_PATH", "equipments.json"),
        interval_seconds=int(os.environ.get("INTERVAL_SECONDS", "60")),
        ping_timeout_seconds=float(os.environ.get("PING_TIMEOUT_SECONDS", "2")),
        snmp_timeout_seconds=float(os.environ.get("SNMP_TIMEOUT_SECONDS", "3")),
        request_timeout_seconds=int(os.environ.get("REQUEST_TIMEOUT_SECONDS", "10")),
        # Recepteur de traps : port non privilegie par defaut (1162) car le port
        # standard (162) exige les droits root / cap_net_bind_service -- voir README.
        trap_enabled=os.environ.get("SNMP_TRAP_ENABLED", "true").strip().lower() in ("1", "true", "yes"),
        trap_bind_address=os.environ.get("SNMP_TRAP_BIND_ADDRESS", "0.0.0.0"),
        trap_port=int(os.environ.get("SNMP_TRAP_PORT", "1162")),
        trap_community=os.environ.get("SNMP_TRAP_COMMUNITY", "public"),
    )


def load_equipments(path: str) -> list[NetworkEquipment]:
    # utf-8-sig tolere le BOM que les outils Windows (PowerShell Set-Content,
    # Notepad "Enregistrer sous") ajoutent par defaut a l'UTF-8 ; il n'a aucun
    # effet sur un fichier sans BOM.
    with open(path, encoding="utf-8-sig") as config_file:
        raw_entries = json.load(config_file)

    equipments = []
    for entry in raw_entries:
        equipments.append(NetworkEquipment(
            nom=entry["nom"],
            equipment_id=entry["equipment_id"],
            api_key=entry["api_key"],
            ip_address=entry["ip_address"],
            snmp_community=entry.get("snmp_community", "public"),
            snmp_port=int(entry.get("snmp_port", 161)),
            interface_index=int(entry.get("interface_index", 1)),
        ))
    return equipments


def ping_equipment(equipment: NetworkEquipment, timeout: float):
    try:
        result = icmp_ping(equipment.ip_address, count=1, timeout=timeout, privileged=False)
        if result.is_alive:
            return result.avg_rtt, True
        logger.warning(
            "Ping KO pour %s (%s) : equipement injoignable au niveau reseau, SNMP non tente.",
            equipment.nom, equipment.ip_address)
        return None, False
    except Exception:
        logger.exception("Erreur ICMP vers %s (%s)", equipment.nom, equipment.ip_address)
        return None, False


async def _snmp_get_async(ip_address: str, port: int, community: str, oids: list[str], timeout: float):
    engine = SnmpEngine()
    transport = await UdpTransportTarget.create((ip_address, port), timeout=timeout)
    error_indication, error_status, error_index, var_binds = await get_cmd(
        engine,
        CommunityData(community, mpModel=1),
        transport,
        ContextData(),
        *[ObjectType(ObjectIdentity(oid)) for oid in oids],
    )

    if error_indication or error_status:
        return None

    return [int(value) for _, value in var_binds]


def snmp_get_values(equipment: NetworkEquipment, timeout: float):
    """Interroge en un seul PDU les compteurs de trafic, l'etat d'interface et
    l'uptime systeme. Un equipement qui ne repond pas (ou pas completement)
    fait echouer l'ensemble du lot plutot que de renvoyer des valeurs
    partielles."""
    keys = list(OID_TEMPLATES.keys())
    oids = [OID_TEMPLATES[key].format(index=equipment.interface_index) for key in keys]

    try:
        values = asyncio.run(_snmp_get_async(
            equipment.ip_address, equipment.snmp_port, equipment.snmp_community, oids, timeout))
    except Exception:
        logger.exception("Erreur SNMP vers %s (%s)", equipment.nom, equipment.ip_address)
        return None

    if values is None:
        logger.warning(
            "SNMP KO pour %s (%s:%s, communaute \"%s\") : reseau joignable (ping OK) mais pas de "
            "reponse SNMP valide. Verifiez que le service SNMP est actif sur l'equipement, que la "
            "communaute correspond, et qu'aucun pare-feu ne bloque le port.",
            equipment.nom, equipment.ip_address, equipment.snmp_port, equipment.snmp_community)
        return None

    return dict(zip(keys, values))


def compute_rates(previous: dict, current: dict, elapsed_seconds: float):
    if elapsed_seconds <= 0:
        return None, None

    delta_octets = max(0, (current["in_octets"] - previous["in_octets"])
                        + (current["out_octets"] - previous["out_octets"]))
    bandwidth_mbps = (delta_octets * 8) / elapsed_seconds / 1_000_000

    delta_errors = max(0, (current["in_errors"] - previous["in_errors"])
                        + (current["out_errors"] - previous["out_errors"]))
    delta_packets = max(0, (current["in_packets"] - previous["in_packets"])
                         + (current["out_packets"] - previous["out_packets"]))
    error_rate_percent = (delta_errors / delta_packets * 100) if delta_packets > 0 else 0.0

    return bandwidth_mbps, error_rate_percent


def send_network_metrics(config: CollectorConfig, equipment: NetworkEquipment, payload: dict) -> None:
    url = f"{config.backend_url}/api/v1/metrics/network"
    headers = {"X-API-Key": equipment.api_key}

    try:
        response = requests.post(url, json=payload, headers=headers, timeout=config.request_timeout_seconds)
        response.raise_for_status()
        logger.info("Metriques reseau envoyees pour %s : %s", equipment.nom, payload)
    except requests.RequestException as exc:
        logger.error("Echec d'envoi des metriques reseau pour %s : %s", equipment.nom, exc)


def poll_equipment(config: CollectorConfig, equipment: NetworkEquipment, previous_readings: dict,
                    lock: "threading.Lock | None" = None) -> None:
    """Sonde un equipement (ping + SNMP) et pousse le resultat au backend.

    `lock`, si fourni, protege l'acces a `previous_readings` : ce dict est partage
    entre le cycle de polling normal et le sondage immediat declenche par un trap
    SNMP (handle_trap), tous deux susceptibles d'appeler cette fonction depuis des
    threads differents pour le meme equipement.
    """
    now = time.monotonic()
    latency_ms, reachable = ping_equipment(equipment, config.ping_timeout_seconds)

    payload = {"equipment_id": equipment.equipment_id}
    if latency_ms is not None:
        payload["latency_ms"] = round(latency_ms, 2)

    if reachable:
        counters = snmp_get_values(equipment, config.snmp_timeout_seconds)
        if counters is not None:
            with lock or contextlib.nullcontext():
                previous = previous_readings.get(equipment.equipment_id)
                counters["timestamp"] = now
                previous_readings[equipment.equipment_id] = counters

            if previous is not None:
                elapsed = now - previous["timestamp"]
                bandwidth_mbps, error_rate_percent = compute_rates(previous, counters, elapsed)
                if bandwidth_mbps is not None:
                    payload["bandwidth_mbps"] = round(bandwidth_mbps, 3)
                    payload["error_rate_percent"] = round(error_rate_percent, 3)

            # sysUpTime est en centiemes de seconde (TimeTicks).
            payload["uptime_seconds"] = round(counters["sys_up_time"] / 100, 1)
            # ifOperStatus : 1 = up (RFC 1213), toute autre valeur compte comme indisponible.
            payload["interface_up"] = 1 if counters["if_oper_status"] == 1 else 0

    send_network_metrics(config, equipment, payload)


def find_equipment_by_ip(equipments: list, ip_address: str):
    """Retrouve, parmi les equipements declares dans equipments.json, celui dont
    l'adresse IP correspond a la source d'un trap SNMP recu. None si aucun ne
    correspond (trap provenant d'un equipement non supervise)."""
    for equipment in equipments:
        if equipment.ip_address == ip_address:
            return equipment
    return None


def describe_trap(var_binds: list) -> str:
    """Extrait un nom de trap lisible a partir de snmpTrapOID.0. `var_binds` est une
    liste de tuples (oid, valeur) deja convertis en chaines. Renvoie le nom standard
    (ex. "linkDown") s'il est repertorie, l'OID brut sinon, ou "inconnu" si le trap
    ne porte pas de snmpTrapOID.0 (notification malformee)."""
    for oid, value in var_binds:
        if oid == SNMP_TRAP_OID:
            return STANDARD_TRAP_NAMES.get(value, value)
    return "inconnu"


def handle_trap(config: CollectorConfig, equipments: list, previous_readings: dict,
                 source_ip: str, var_binds: list, lock: "threading.Lock | None" = None) -> None:
    """Traite un trap SNMP deja decode (adresse source + varbinds en chaines).

    Un trap provenant d'une IP non declaree dans equipments.json est ignore (rien a
    en faire). Sinon, quel que soit son type (linkDown/linkUp/coldStart/...),
    declenche un sondage immediat de l'equipement concerne : ce sondage reutilise
    poll_equipment/send_network_metrics, le meme mecanisme de push HTTP que le cycle
    de polling normal, pour ne pas attendre le prochain cycle.
    """
    equipment = find_equipment_by_ip(equipments, source_ip)
    if equipment is None:
        logger.debug(
            "Trap SNMP recu de %s : aucun equipement correspondant dans equipments.json, ignore.",
            source_ip)
        return

    trap_name = describe_trap(var_binds)
    logger.info(
        "Trap SNMP \"%s\" recu de %s (%s) : sondage immediat hors cycle.",
        trap_name, equipment.nom, source_ip)

    try:
        poll_equipment(config, equipment, previous_readings, lock)
    except Exception:
        logger.exception(
            "Erreur lors du sondage immediat de %s declenche par un trap SNMP", equipment.nom)


def _make_trap_callback(config: CollectorConfig, equipments: list, previous_readings: dict,
                         lock: "threading.Lock | None"):
    """Construit le callback pysnmp (process_pdu) appele a chaque trap recu.

    pysnmp ne transmet pas directement l'adresse source au callback : elle est
    recuperee via le contexte d'execution du dispatcher de messages (idiome standard
    de pysnmp pour un recepteur de traps), en place uniquement pendant l'appel."""

    def on_trap(snmp_engine, state_reference, context_engine_id, context_name, var_binds, cb_ctx):
        try:
            exec_context = snmp_engine.observer.get_execution_context("rfc3412.receiveMessage:request")
            transport_address = exec_context.get("transportAddress") if exec_context else None
        except KeyError:
            transport_address = None

        if not transport_address:
            logger.warning("Trap SNMP recu sans adresse source identifiable, ignore.")
            return

        source_ip = transport_address[0]
        pairs = [(str(oid), str(value)) for oid, value in var_binds]
        handle_trap(config, equipments, previous_readings, source_ip, pairs, lock)

    return on_trap


def run_trap_receiver(config: CollectorConfig, equipments: list, previous_readings: dict,
                       lock: "threading.Lock | None" = None) -> None:
    """Ecoute les traps SNMP (v1/v2c) sur `config.trap_bind_address:config.trap_port`.

    Bloquant (boucle asyncio dediee) : concu pour tourner dans un thread demon separe
    du polling periodique, voir start_trap_receiver_thread(). N'interrompt jamais le
    polling : toute erreur au demarrage (port deja utilise, port privilegie sans
    droits root, ...) est loggee et laisse le collecteur fonctionner en polling seul.
    """
    loop = asyncio.new_event_loop()
    asyncio.set_event_loop(loop)

    # pysnmp cree le socket via asyncio.ensure_future() (open_server_mode) sans
    # l'attendre : un port deja utilise ou une liaison sans privileges suffisants
    # echoue donc de facon asynchrone, une fois la boucle demarree (apres
    # open_dispatcher() ci-dessous) -- pas dans le bloc try/except qui suit. Ce
    # handler couvre ce cas pour que l'erreur soit loggee au lieu d'etre avalee
    # silencieusement par asyncio, sans jamais faire planter la boucle ni le
    # polling periodique (thread separe).
    def _on_loop_exception(loop, context):
        logger.error(
            "Erreur dans le recepteur de traps SNMP (%s:%s) : %s -- le port 162 standard exige "
            "des privileges root (cap_net_bind_service) ; le polling periodique n'est pas affecte. "
            "Voir README.md.",
            config.trap_bind_address, config.trap_port,
            context.get("exception") or context.get("message"))

    loop.set_exception_handler(_on_loop_exception)

    try:
        snmp_engine = SnmpEngine()
        transport = trap_udp.UdpTransport(loop=loop).open_server_mode(
            (config.trap_bind_address, config.trap_port))
        trap_config.add_transport(snmp_engine, trap_udp.DOMAIN_NAME, transport)
        trap_config.add_v1_system(snmp_engine, "trap-community", config.trap_community)
        ntfrcv.NotificationReceiver(
            snmp_engine, _make_trap_callback(config, equipments, previous_readings, lock))

        logger.info(
            "Recepteur de traps SNMP demarre sur %s:%s (communaute configuree).",
            config.trap_bind_address, config.trap_port)
        snmp_engine.open_dispatcher()  # bloque : boucle asyncio de ce thread
    except Exception:
        logger.exception(
            "Impossible de demarrer le recepteur de traps SNMP sur %s:%s -- le port 162 standard "
            "exige des privileges root (cap_net_bind_service) ; le polling periodique continue "
            "normalement sans traps. Voir README.md.",
            config.trap_bind_address, config.trap_port)
    finally:
        loop.close()


def start_trap_receiver_thread(config: CollectorConfig, equipments: list, previous_readings: dict,
                                lock: "threading.Lock | None" = None) -> threading.Thread:
    """Demarre le recepteur de traps dans un thread demon.

    Thread demon : il s'arrete automatiquement avec le processus principal (pas
    d'etape d'arret explicite necessaire au signal SIGINT/SIGTERM, deja gere par
    GracefulShutdown pour la boucle de polling)."""
    thread = threading.Thread(
        target=run_trap_receiver, args=(config, equipments, previous_readings, lock),
        name="snmp-trap-receiver", daemon=True)
    thread.start()
    return thread


class GracefulShutdown:
    def __init__(self) -> None:
        self.stop_requested = False
        signal.signal(signal.SIGINT, self._handle_signal)
        signal.signal(signal.SIGTERM, self._handle_signal)

    def _handle_signal(self, signum, frame) -> None:
        logger.info("Signal d'arret recu (%s), fin du collecteur apres le cycle en cours.", signum)
        self.stop_requested = True


def run(config: CollectorConfig, equipments: list, previous_readings: dict,
        lock: "threading.Lock | None" = None) -> None:
    """Boucle de polling periodique. `previous_readings` et `lock` sont recus en
    parametres (plutot que crees ici) pour pouvoir etre partages avec le recepteur
    de traps, qui declenche des sondages immediats sur les memes equipements."""
    shutdown = GracefulShutdown()

    logger.info(
        "Collecteur reseau demarre (%d equipements, backend=%s, intervalle=%ss)",
        len(equipments), config.backend_url, config.interval_seconds,
    )

    while not shutdown.stop_requested:
        cycle_start = time.monotonic()
        for equipment in equipments:
            try:
                poll_equipment(config, equipment, previous_readings, lock)
            except Exception:
                logger.exception("Erreur inattendue lors du sondage de %s", equipment.nom)

        elapsed = time.monotonic() - cycle_start
        sleep_time = max(0.0, config.interval_seconds - elapsed)
        for _ in range(int(sleep_time)):
            if shutdown.stop_requested:
                break
            time.sleep(1)


def main() -> None:
    logging.basicConfig(
        level=os.environ.get("LOG_LEVEL", "INFO"),
        format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
    )
    config = load_config()

    try:
        equipments = load_equipments(config.equipments_config_path)
    except (OSError, json.JSONDecodeError, KeyError) as exc:
        raise SystemExit(f"Impossible de charger {config.equipments_config_path} : {exc}")

    if not equipments:
        raise SystemExit(f"Aucun equipement defini dans {config.equipments_config_path}")

    previous_readings: dict = {}
    lock = threading.Lock()

    if config.trap_enabled:
        start_trap_receiver_thread(config, equipments, previous_readings, lock)
    else:
        logger.info("Recepteur de traps SNMP desactive (SNMP_TRAP_ENABLED=false) : polling seul.")

    run(config, equipments, previous_readings, lock)


if __name__ == "__main__":
    try:
        main()
    except SystemExit as exc:
        logger.error(str(exc))
        sys.exit(1)
