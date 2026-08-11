"""Agent de supervision systeme pour le PFE Monitoring EPT.

Collecte des metriques locales via psutil et les pousse periodiquement vers
le backend Spring Boot (POST /api/v1/metrics/system), authentifie par la cle
API propre a l'equipement (en-tete X-API-Key). Toutes les metriques restent
des valeurs numeriques dans le temps (cahier des specifications techniques
Sec4.4) : pas d'inventaire de processus ni de connexions individuelles, qui
releveraient d'un modele de donnees different.
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
    )


def count_listening_ports() -> int | None:
    try:
        connections = psutil.net_connections(kind="inet")
    except (psutil.AccessDenied, PermissionError):
        logger.warning("Droits insuffisants pour lister les connexions reseau (ports en ecoute ignores).")
        return None
    return sum(1 for c in connections if c.status == psutil.CONN_LISTEN)


def collect_metrics(previous_io: IoSample | None) -> tuple[dict, IoSample]:
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

    metrics: dict = {
        "equipment_id": None,  # rempli par send_metrics
        "cpu_percent": psutil.cpu_percent(interval=1),
        "memory_percent": psutil.virtual_memory().percent,
        "disk_percent": psutil.disk_usage(disk_path).percent,
        "swap_percent": psutil.swap_memory().percent,
        "process_count": len(psutil.pids()),
        "uptime_seconds": round(time.time() - psutil.boot_time()),
    }

    listening_ports = count_listening_ports()
    if listening_ports is not None:
        metrics["listening_ports_count"] = listening_ports

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


def send_metrics(config: AgentConfig, metrics: dict) -> None:
    metrics["equipment_id"] = config.equipment_id
    url = f"{config.backend_url}/api/v1/metrics/system"
    headers = {"X-API-Key": config.api_key}

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

    previous_io: IoSample | None = None
    while not shutdown.stop_requested:
        cycle_start = time.monotonic()
        try:
            metrics, previous_io = collect_metrics(previous_io)
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
