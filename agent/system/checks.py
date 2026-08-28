"""Sondes optionnelles additionnelles pour l'agent systeme.

Chaque fonction est independante et desactivee par defaut (retourne None
quand la variable d'environnement correspondante n'est pas configuree),
afin qu'un agent minimal (CPU/RAM/disque) reste utilisable sans
configuration supplementaire.
"""

import json
import logging
import os
import re
import socket
import time

import psutil

logger = logging.getLogger("system_agent.checks")


def read_load_ratio() -> float | None:
    if not hasattr(os, "getloadavg"):
        return None
    try:
        load1min, _, _ = os.getloadavg()
    except OSError:
        return None
    cpu_count = os.cpu_count() or 1
    return round(load1min / cpu_count, 3)


def read_resource_limits() -> tuple[int | None, int | None]:
    if os.name == "nt":
        return None, None

    import resource

    open_files = None
    process_limit = None
    try:
        open_files = resource.getrlimit(resource.RLIMIT_NOFILE)[0]
    except (ValueError, OSError, AttributeError):
        pass
    try:
        process_limit = resource.getrlimit(resource.RLIMIT_NPROC)[0]
    except (ValueError, OSError, AttributeError):
        pass
    return open_files, process_limit


def parse_tcp_targets(raw: str) -> list[tuple[str, int]]:
    targets: list[tuple[str, int]] = []
    for entry in raw.split(","):
        entry = entry.strip()
        if not entry:
            continue
        host, _, port_str = entry.rpartition(":")
        host = host or "localhost"
        try:
            targets.append((host, int(port_str)))
        except ValueError:
            logger.warning("Cible TCP invalide ignoree: %s", entry)
    return targets


def count_tcp_services_down(targets: list[tuple[str, int]], timeout: float) -> int | None:
    if not targets:
        return None
    down = 0
    for host, port in targets:
        try:
            with socket.create_connection((host, port), timeout=timeout):
                pass
        except OSError:
            down += 1
    return down


def check_dns_latency(hostname: str, timeout: float) -> float | None:
    if not hostname:
        return None
    original_timeout = socket.getdefaulttimeout()
    socket.setdefaulttimeout(timeout)
    try:
        start = time.monotonic()
        socket.getaddrinfo(hostname, None)
        return round((time.monotonic() - start) * 1000, 2)
    except OSError:
        logger.warning("Resolution DNS echouee pour %s", hostname)
        return None
    finally:
        socket.setdefaulttimeout(original_timeout)


def load_state(path: str) -> dict:
    try:
        with open(path, encoding="utf-8") as state_file:
            return json.load(state_file)
    except (OSError, json.JSONDecodeError):
        return {}


def save_state(path: str, state: dict) -> None:
    try:
        with open(path, "w", encoding="utf-8") as state_file:
            json.dump(state, state_file)
    except OSError:
        logger.warning("Impossible d'ecrire le fichier d'etat: %s", path)


def check_log_file(path: str, pattern: str | None, state: dict) -> tuple[int | None, int | None]:
    if not path:
        return None, None
    try:
        size = os.path.getsize(path)
    except OSError:
        logger.warning("Fichier log introuvable ou illisible: %s", path)
        return None, None

    if "log_offset" not in state:
        # Premier cycle : on ne compte pas l'historique existant, seulement
        # ce qui est ecrit a partir de maintenant (comme la plupart des
        # outils de suivi de logs au premier demarrage).
        state["log_offset"] = size
        return 0, 0

    offset = state["log_offset"]
    if size < offset:
        offset = 0  # rotation ou troncature detectee : on repart du debut

    compiled_pattern = re.compile(pattern) if pattern else None
    lines_total = 0
    lines_match = 0
    try:
        with open(path, encoding="utf-8", errors="replace") as log_file:
            log_file.seek(offset)
            for line in log_file:
                lines_total += 1
                if compiled_pattern and compiled_pattern.search(line):
                    lines_match += 1
            state["log_offset"] = log_file.tell()
    except OSError:
        logger.warning("Impossible de lire le fichier log: %s", path)
        return None, None

    return lines_total, lines_match


def check_watched_file(path: str) -> tuple[int | None, int | None]:
    if not path:
        return None, None
    if not os.path.isfile(path):
        return 0, None
    try:
        return 1, os.path.getsize(path)
    except OSError:
        return 1, None


def read_sensors() -> tuple[float | None, float | None]:
    temperature = None
    fan_speed = None

    sensors_temperatures = getattr(psutil, "sensors_temperatures", None)
    if sensors_temperatures:
        try:
            readings = [
                entry.current
                for entries in sensors_temperatures().values()
                for entry in entries
                if entry.current is not None
            ]
            if readings:
                temperature = max(readings)
        except (OSError, NotImplementedError):
            pass

    sensors_fans = getattr(psutil, "sensors_fans", None)
    if sensors_fans:
        try:
            for entries in sensors_fans().values():
                for entry in entries:
                    if entry.current:
                        fan_speed = entry.current
                        break
                if fan_speed is not None:
                    break
        except (OSError, NotImplementedError):
            pass

    return temperature, fan_speed


def read_modbus_register(host: str, port: int, unit_id: int, address: int, register_type: str) -> float | None:
    """Lecture Modbus TCP optionnelle - desactivee sauf si MODBUS_HOST est defini.

    Non verifiee contre un vrai automate/PLC : aucun equipement Modbus
    n'est disponible dans cet environnement de developpement. La mecanique
    (connexion, lecture de registre) suit l'API pymodbus 3.x standard, mais
    n'a pas ete testee en conditions reelles - a valider sur le premier
    equipement industriel reel avant mise en production.
    """
    if not host:
        return None

    try:
        from pymodbus.client import ModbusTcpClient
    except ImportError:
        logger.warning("pymodbus n'est pas installe (pip install -r requirements.txt) : lecture Modbus ignoree.")
        return None

    client = ModbusTcpClient(host, port=port)
    try:
        if not client.connect():
            logger.warning("Connexion Modbus impossible vers %s:%s", host, port)
            return None

        if register_type == "input":
            result = client.read_input_registers(address, count=1, slave=unit_id)
        else:
            result = client.read_holding_registers(address, count=1, slave=unit_id)

        if result.isError():
            logger.warning("Erreur de lecture Modbus (registre %s): %s", address, result)
            return None

        return float(result.registers[0])
    except Exception:
        logger.exception("Erreur inattendue lors de la lecture Modbus.")
        return None
    finally:
        client.close()
