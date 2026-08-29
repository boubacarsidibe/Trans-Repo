"""Agent de supervision systeme pour le PFE Monitoring EPT.

Collecte des metriques locales via psutil (et quelques sondes optionnelles
dans checks.py) et les pousse periodiquement vers le backend Spring Boot
(POST /api/v1/metrics/system), authentifie par la cle API propre a
l'equipement (en-tete X-API-Key). Toutes les metriques restent des valeurs
numeriques dans le temps : pas d'inventaire de processus ni de connexions
individuelles, qui releveraient d'un modele de donnees different.

Les sondes additionnelles (TCP, DNS, log, fichier, capteurs, Modbus) sont
desactivees par defaut et ne s'activent que si la variable d'environnement
correspondante est renseignee - un agent minimal fonctionne sans rien
configurer de plus que EQUIPMENT_ID/API_KEY.
"""

import logging
import os
import signal
import sys
import time
from dataclasses import dataclass

import psutil
import requests
from dotenv import load_dotenv

import checks

load_dotenv()

logger = logging.getLogger("system_agent")


@dataclass
class IoSample:
    timestamp: float
    disk_read_bytes: int
    disk_write_bytes: int
    net_bytes_recv: int
    net_bytes_sent: int


@dataclass
class AgentConfig:
    backend_url: str
    equipment_id: str
    api_key: str
    interval_seconds: int
    request_timeout_seconds: int
    send_max_retries: int
    send_retry_backoff_seconds: float
    tcp_targets: list[tuple[str, int]]
    tcp_check_timeout_seconds: float
    dns_check_hostname: str
    dns_check_timeout_seconds: float
    log_file_path: str
    log_pattern: str | None
    watched_file_path: str
    state_file_path: str
    modbus_host: str
    modbus_port: int
    modbus_unit_id: int
    modbus_register_address: int
    modbus_register_type: str


def load_config() -> AgentConfig:
    equipment_id = os.environ.get("EQUIPMENT_ID")
    api_key = os.environ.get("API_KEY")

    missing = [name for name, value in (("EQUIPMENT_ID", equipment_id), ("API_KEY", api_key)) if not value]
    if missing:
        raise SystemExit(f"Variables d'environnement manquantes : {', '.join(missing)}")

    return AgentConfig(
        backend_url=os.environ.get("BACKEND_URL", "http://localhost:8080").rstrip("/"),
        equipment_id=equipment_id,
        api_key=api_key,
        interval_seconds=int(os.environ.get("INTERVAL_SECONDS", "60")),
        request_timeout_seconds=int(os.environ.get("REQUEST_TIMEOUT_SECONDS", "10")),
        send_max_retries=int(os.environ.get("SEND_MAX_RETRIES", "3")),
        send_retry_backoff_seconds=float(os.environ.get("SEND_RETRY_BACKOFF_SECONDS", "5")),
        tcp_targets=checks.parse_tcp_targets(os.environ.get("TCP_HEALTHCHECK_PORTS", "")),
        tcp_check_timeout_seconds=float(os.environ.get("TCP_CHECK_TIMEOUT_SECONDS", "2")),
        dns_check_hostname=os.environ.get("DNS_CHECK_HOSTNAME", ""),
        dns_check_timeout_seconds=float(os.environ.get("DNS_CHECK_TIMEOUT_SECONDS", "2")),
        log_file_path=os.environ.get("LOG_FILE_PATH", ""),
        log_pattern=os.environ.get("LOG_PATTERN") or None,
        watched_file_path=os.environ.get("WATCHED_FILE_PATH", ""),
        state_file_path=os.environ.get("STATE_FILE_PATH", "agent_state.json"),
        modbus_host=os.environ.get("MODBUS_HOST", ""),
        modbus_port=int(os.environ.get("MODBUS_PORT", "502")),
        modbus_unit_id=int(os.environ.get("MODBUS_UNIT_ID", "1")),
        modbus_register_address=int(os.environ.get("MODBUS_REGISTER_ADDRESS", "0")),
        modbus_register_type=os.environ.get("MODBUS_REGISTER_TYPE", "holding"),
    )


def count_listening_ports() -> int | None:
    try:
        connections = psutil.net_connections(kind="inet")
    except (psutil.AccessDenied, PermissionError):
        logger.warning("Droits insuffisants pour lister les connexions reseau (ports en ecoute ignores).")
        return None
    return sum(1 for c in connections if c.status == psutil.CONN_LISTEN)


def collect_core_metrics(previous_io: IoSample | None) -> tuple[dict, IoSample]:
    disk_path = "C:\\" if os.name == "nt" else "/"
    now = time.monotonic()

    disk_io = psutil.disk_io_counters()
    net_io = psutil.net_io_counters()
    current_io = IoSample(
        timestamp=now,
        disk_read_bytes=disk_io.read_bytes if disk_io else 0,
        disk_write_bytes=disk_io.write_bytes if disk_io else 0,
        net_bytes_recv=net_io.bytes_recv,
        net_bytes_sent=net_io.bytes_sent,
    )

    virtual_memory = psutil.virtual_memory()
    disk_usage = psutil.disk_usage(disk_path)

    metrics: dict = {
        "equipment_id": None,  # rempli par send_metrics
        "cpu_percent": psutil.cpu_percent(interval=1),
        "memory_percent": virtual_memory.percent,
        "disk_percent": disk_usage.percent,
        "swap_percent": psutil.swap_memory().percent,
        "process_count": len(psutil.pids()),
        "uptime_seconds": round(time.time() - psutil.boot_time()),
        "memory_total_mb": round(virtual_memory.total / 1024 / 1024, 1),
        "memory_used_mb": round(virtual_memory.used / 1024 / 1024, 1),
        "disk_total_gb": round(disk_usage.total / 1024 / 1024 / 1024, 2),
        "disk_used_gb": round(disk_usage.used / 1024 / 1024 / 1024, 2),
    }

    listening_ports = count_listening_ports()
    if listening_ports is not None:
        metrics["listening_ports_count"] = listening_ports

    load_ratio = checks.read_load_ratio()
    if load_ratio is not None:
        metrics["load_1min"] = load_ratio

    open_files_limit, process_limit = checks.read_resource_limits()
    if open_files_limit is not None:
        metrics["open_files_limit"] = open_files_limit
    if process_limit is not None:
        metrics["process_limit"] = process_limit

    if previous_io is not None and current_io.timestamp > previous_io.timestamp:
        elapsed = current_io.timestamp - previous_io.timestamp
        metrics["disk_read_kbps"] = round(
            max(0, current_io.disk_read_bytes - previous_io.disk_read_bytes) / elapsed / 1024, 2)
        metrics["disk_write_kbps"] = round(
            max(0, current_io.disk_write_bytes - previous_io.disk_write_bytes) / elapsed / 1024, 2)
        metrics["network_in_kbps"] = round(
            max(0, current_io.net_bytes_recv - previous_io.net_bytes_recv) / elapsed / 1024, 2)
        metrics["network_out_kbps"] = round(
            max(0, current_io.net_bytes_sent - previous_io.net_bytes_sent) / elapsed / 1024, 2)

    return metrics, current_io


def collect_optional_metrics(config: AgentConfig, state: dict) -> dict:
    metrics: dict = {}

    tcp_down = checks.count_tcp_services_down(config.tcp_targets, config.tcp_check_timeout_seconds)
    if tcp_down is not None:
        metrics["tcp_services_down"] = tcp_down

    dns_latency = checks.check_dns_latency(config.dns_check_hostname, config.dns_check_timeout_seconds)
    if dns_latency is not None:
        metrics["dns_latency_ms"] = dns_latency

    log_lines, log_matches = checks.check_log_file(config.log_file_path, config.log_pattern, state)
    if log_lines is not None:
        metrics["log_lines_count"] = log_lines
        metrics["log_lines_match_count"] = log_matches

    file_exists, file_size = checks.check_watched_file(config.watched_file_path)
    if file_exists is not None:
        metrics["watched_file_exists"] = file_exists
        if file_size is not None:
            metrics["watched_file_size_bytes"] = file_size

    temperature, fan_speed = checks.read_sensors()
    if temperature is not None:
        metrics["temperature_max_celsius"] = temperature
    if fan_speed is not None:
        metrics["fan_speed_rpm"] = fan_speed

    modbus_value = checks.read_modbus_register(
        config.modbus_host, config.modbus_port, config.modbus_unit_id,
        config.modbus_register_address, config.modbus_register_type)
    if modbus_value is not None:
        metrics["modbus_value"] = modbus_value

    return metrics


def send_metrics(config: AgentConfig, metrics: dict) -> None:
    """Envoie les metriques, avec quelques tentatives rapprochees en cas
    d'erreur reseau passagere avant d'abandonner jusqu'au cycle suivant."""
    metrics["equipment_id"] = config.equipment_id
    url = f"{config.backend_url}/api/v1/metrics/system"
    headers = {"X-API-Key": config.api_key}

    derniere_erreur: requests.RequestException | None = None
    for tentative in range(1, config.send_max_retries + 1):
        try:
            response = requests.post(url, json=metrics, headers=headers, timeout=config.request_timeout_seconds)
            response.raise_for_status()
            logger.info(
                "Metriques envoyees (cpu=%.1f%%, ram=%.1f%%, disque=%.1f%%, swap=%.1f%%, processus=%s)",
                metrics["cpu_percent"],
                metrics["memory_percent"],
                metrics["disk_percent"],
                metrics["swap_percent"],
                metrics["process_count"],
            )
            return
        except requests.RequestException as exc:
            derniere_erreur = exc
            if tentative < config.send_max_retries:
                attente = config.send_retry_backoff_seconds * tentative
                logger.warning(
                    "Echec d'envoi des metriques (tentative %d/%d), nouvel essai dans %.0fs : %s",
                    tentative, config.send_max_retries, attente, exc,
                )
                time.sleep(attente)

    raise derniere_erreur


class GracefulShutdown:
    def __init__(self) -> None:
        self.stop_requested = False
        signal.signal(signal.SIGINT, self._handle_signal)
        signal.signal(signal.SIGTERM, self._handle_signal)

    def _handle_signal(self, signum, frame) -> None:
        logger.info("Signal d'arret recu (%s), fin de l'agent apres le cycle en cours.", signum)
        self.stop_requested = True


def run(config: AgentConfig) -> None:
    shutdown = GracefulShutdown()
    logger.info(
        "Agent systeme demarre (equipement=%s, backend=%s, intervalle=%ss)",
        config.equipment_id,
        config.backend_url,
        config.interval_seconds,
    )

    state = checks.load_state(config.state_file_path)
    previous_io: IoSample | None = None
    while not shutdown.stop_requested:
        cycle_start = time.monotonic()
        try:
            metrics, previous_io = collect_core_metrics(previous_io)
            metrics.update(collect_optional_metrics(config, state))
            checks.save_state(config.state_file_path, state)
            send_metrics(config, metrics)
        except requests.RequestException as exc:
            logger.error("Echec d'envoi des metriques : %s", exc)
        except Exception:
            logger.exception("Erreur inattendue pendant la collecte des metriques.")

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
    run(config)


if __name__ == "__main__":
    try:
        main()
    except SystemExit as exc:
        logger.error(str(exc))
        sys.exit(1)
