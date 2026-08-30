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
"""

import asyncio
import json
import logging
import os
import signal
import sys
import time
from dataclasses import dataclass

import requests
from dotenv import load_dotenv
from icmplib import ping as icmp_ping
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


@dataclass
class CollectorConfig:
    backend_url: str
    equipments_config_path: str
    interval_seconds: int
    ping_timeout_seconds: float
    snmp_timeout_seconds: float
    request_timeout_seconds: int


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
    )


def load_equipments(path: str) -> list[NetworkEquipment]:
    with open(path, encoding="utf-8") as config_file:
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


def poll_equipment(config: CollectorConfig, equipment: NetworkEquipment, previous_readings: dict) -> None:
    now = time.monotonic()
    latency_ms, reachable = ping_equipment(equipment, config.ping_timeout_seconds)

    payload = {"equipment_id": equipment.equipment_id}
    if latency_ms is not None:
        payload["latency_ms"] = round(latency_ms, 2)

    if reachable:
        counters = snmp_get_values(equipment, config.snmp_timeout_seconds)
        if counters is not None:
            previous = previous_readings.get(equipment.equipment_id)
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

            counters["timestamp"] = now
            previous_readings[equipment.equipment_id] = counters

    send_network_metrics(config, equipment, payload)


class GracefulShutdown:
    def __init__(self) -> None:
        self.stop_requested = False
        signal.signal(signal.SIGINT, self._handle_signal)
        signal.signal(signal.SIGTERM, self._handle_signal)

    def _handle_signal(self, signum, frame) -> None:
        logger.info("Signal d'arret recu (%s), fin du collecteur apres le cycle en cours.", signum)
        self.stop_requested = True


def run(config: CollectorConfig, equipments: list) -> None:
    shutdown = GracefulShutdown()
    previous_readings: dict = {}

    logger.info(
        "Collecteur reseau demarre (%d equipements, backend=%s, intervalle=%ss)",
        len(equipments), config.backend_url, config.interval_seconds,
    )

    while not shutdown.stop_requested:
        cycle_start = time.monotonic()
        for equipment in equipments:
            try:
                poll_equipment(config, equipment, previous_readings)
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

    run(config, equipments)


if __name__ == "__main__":
    try:
        main()
    except SystemExit as exc:
        logger.error(str(exc))
        sys.exit(1)
