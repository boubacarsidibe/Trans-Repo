import pytest
import requests

import system_agent
from system_agent import AgentConfig, IoSample


def fake_config(**overrides) -> AgentConfig:
    base = dict(
        backend_url="http://backend.local",
        equipment_id="00000000-0000-0000-0000-000000000000",
        api_key="secret",
        interval_seconds=60,
        request_timeout_seconds=10,
        send_max_retries=3,
        send_retry_backoff_seconds=5,
        tcp_targets=[],
        tcp_check_timeout_seconds=2,
        dns_check_hostname="",
        dns_check_timeout_seconds=2,
        log_file_path="",
        log_pattern=None,
        watched_file_path="",
        state_file_path="agent_state.json",
        modbus_host="",
        modbus_port=502,
        modbus_unit_id=1,
        modbus_register_address=0,
        modbus_register_type="holding",
    )
    base.update(overrides)
    return AgentConfig(**base)


class TestLoadConfig:
    def test_charge_les_valeurs_par_defaut(self, monkeypatch):
        monkeypatch.setenv("EQUIPMENT_ID", "eq-1")
        monkeypatch.setenv("API_KEY", "cle-secrete")
        for variable in (
            "BACKEND_URL", "INTERVAL_SECONDS", "REQUEST_TIMEOUT_SECONDS",
            "SEND_MAX_RETRIES", "SEND_RETRY_BACKOFF_SECONDS",
        ):
            monkeypatch.delenv(variable, raising=False)

        config = system_agent.load_config()

        assert config.equipment_id == "eq-1"
        assert config.api_key == "cle-secrete"
        assert config.backend_url == "http://localhost:8080"
        assert config.interval_seconds == 60
        assert config.send_max_retries == 3
        assert config.send_retry_backoff_seconds == 5

    def test_variables_obligatoires_manquantes_leve_system_exit(self, monkeypatch):
        monkeypatch.delenv("EQUIPMENT_ID", raising=False)
        monkeypatch.delenv("API_KEY", raising=False)

        with pytest.raises(SystemExit) as excinfo:
            system_agent.load_config()

        assert "EQUIPMENT_ID" in str(excinfo.value)
        assert "API_KEY" in str(excinfo.value)


class TestCollectCoreMetrics:
    def test_calcule_les_debits_a_partir_de_l_echantillon_precedent(self, monkeypatch):
        monkeypatch.setattr(system_agent.psutil, "disk_io_counters", lambda: FakeIoCounters(1_000_000, 2_000_000))
        monkeypatch.setattr(system_agent.psutil, "net_io_counters", lambda: FakeIoCounters(500_000, 100_000))
        monkeypatch.setattr(system_agent.psutil, "virtual_memory", lambda: FakeVirtualMemory())
        monkeypatch.setattr(system_agent.psutil, "disk_usage", lambda path: FakeDiskUsage())
        monkeypatch.setattr(system_agent.psutil, "swap_memory", lambda: FakeSwap())
        monkeypatch.setattr(system_agent.psutil, "pids", lambda: list(range(120)))
        monkeypatch.setattr(system_agent.psutil, "cpu_percent", lambda interval=None: 42.5)
        monkeypatch.setattr(system_agent.psutil, "boot_time", lambda: 0.0)
        monkeypatch.setattr(system_agent, "count_listening_ports", lambda: 7)
        monkeypatch.setattr(system_agent.checks, "read_load_ratio", lambda: 0.5)
        monkeypatch.setattr(system_agent.checks, "read_resource_limits", lambda: (1024, 256))

        previous = IoSample(
            timestamp=0.0, disk_read_bytes=0, disk_write_bytes=0, net_bytes_recv=0, net_bytes_sent=0,
        )
        monkeypatch.setattr(system_agent.time, "monotonic", lambda: 10.0)

        metrics, io_sample = system_agent.collect_core_metrics(previous)

        assert metrics["cpu_percent"] == 42.5
        assert metrics["memory_percent"] == 55.0
        assert metrics["disk_percent"] == 70.0
        assert metrics["swap_percent"] == 12.0
        assert metrics["process_count"] == 120
        assert metrics["listening_ports_count"] == 7
        assert metrics["load_1min"] == 0.5
        assert metrics["open_files_limit"] == 1024
        assert metrics["process_limit"] == 256
        # 1 000 000 octets sur 10s = 100 000 o/s = ~97.66 Ko/s
        assert metrics["disk_read_kbps"] == 97.66
        assert io_sample.disk_read_bytes == 1_000_000

    def test_sans_echantillon_precedent_aucun_debit_calcule(self, monkeypatch):
        monkeypatch.setattr(system_agent.psutil, "disk_io_counters", lambda: FakeIoCounters(0, 0))
        monkeypatch.setattr(system_agent.psutil, "net_io_counters", lambda: FakeIoCounters(0, 0))
        monkeypatch.setattr(system_agent.psutil, "virtual_memory", lambda: FakeVirtualMemory())
        monkeypatch.setattr(system_agent.psutil, "disk_usage", lambda path: FakeDiskUsage())
        monkeypatch.setattr(system_agent.psutil, "swap_memory", lambda: FakeSwap())
        monkeypatch.setattr(system_agent.psutil, "pids", lambda: [])
        monkeypatch.setattr(system_agent.psutil, "cpu_percent", lambda interval=None: 1.0)
        monkeypatch.setattr(system_agent.psutil, "boot_time", lambda: 0.0)
        monkeypatch.setattr(system_agent, "count_listening_ports", lambda: None)
        monkeypatch.setattr(system_agent.checks, "read_load_ratio", lambda: None)
        monkeypatch.setattr(system_agent.checks, "read_resource_limits", lambda: (None, None))

        metrics, _ = system_agent.collect_core_metrics(None)

        assert "disk_read_kbps" not in metrics
        assert "listening_ports_count" not in metrics
        assert "load_1min" not in metrics


class TestCollectOptionalMetrics:
    def test_ne_garde_que_les_sondes_actives(self, monkeypatch):
        monkeypatch.setattr(system_agent.checks, "count_tcp_services_down", lambda *a: None)
        monkeypatch.setattr(system_agent.checks, "check_dns_latency", lambda *a: 12.3)
        monkeypatch.setattr(system_agent.checks, "check_log_file", lambda *a: (None, None))
        monkeypatch.setattr(system_agent.checks, "check_watched_file", lambda *a: (None, None))
        monkeypatch.setattr(system_agent.checks, "read_sensors", lambda: (None, None))
        monkeypatch.setattr(system_agent.checks, "read_modbus_register", lambda *a: None)

        metrics = system_agent.collect_optional_metrics(fake_config(), {})

        assert metrics == {"dns_latency_ms": 12.3}


class TestSendMetrics:
    def test_envoie_les_metriques_avec_la_cle_api_de_l_equipement(self, monkeypatch):
        appels = []
        monkeypatch.setattr(system_agent.requests, "post", lambda *a, **k: appels.append((a, k)) or FakeResponse(200))

        system_agent.send_metrics(
            fake_config(),
            {"cpu_percent": 1, "memory_percent": 2, "disk_percent": 3, "swap_percent": 4, "process_count": 5},
        )

        assert len(appels) == 1
        args, kwargs = appels[0]
        assert args[0] == "http://backend.local/api/v1/metrics/system"
        assert kwargs["headers"] == {"X-API-Key": "secret"}
        assert kwargs["json"]["equipment_id"] == "00000000-0000-0000-0000-000000000000"

    def test_reessaie_apres_un_echec_puis_reussit(self, monkeypatch):
        reponses = iter([requests.RequestException("timeout"), FakeResponse(200)])
        attentes = []

        def fake_post(*a, **k):
            reponse = next(reponses)
            if isinstance(reponse, Exception):
                raise reponse
            return reponse

        monkeypatch.setattr(system_agent.requests, "post", fake_post)
        monkeypatch.setattr(system_agent.time, "sleep", lambda s: attentes.append(s))

        system_agent.send_metrics(
            fake_config(),
            {"cpu_percent": 1, "memory_percent": 2, "disk_percent": 3, "swap_percent": 4, "process_count": 5},
        )

        assert attentes == [5]  # backoff x tentative 1

    def test_abandonne_apres_epuisement_des_tentatives(self, monkeypatch):
        def toujours_en_echec(*a, **k):
            raise requests.RequestException("connexion refusee")

        attentes = []
        monkeypatch.setattr(system_agent.requests, "post", toujours_en_echec)
        monkeypatch.setattr(system_agent.time, "sleep", lambda s: attentes.append(s))

        with pytest.raises(requests.RequestException):
            system_agent.send_metrics(
                fake_config(send_max_retries=3),
                {"cpu_percent": 1, "memory_percent": 2, "disk_percent": 3, "swap_percent": 4, "process_count": 5},
            )

        assert attentes == [5, 10]  # backoff x tentative (1 puis 2), rien apres la derniere

    def test_une_reponse_http_en_echec_est_aussi_retentee(self, monkeypatch):
        appels = []
        monkeypatch.setattr(
            system_agent.requests, "post",
            lambda *a, **k: appels.append(1) or FakeResponse(500),
        )
        monkeypatch.setattr(system_agent.time, "sleep", lambda s: None)

        with pytest.raises(requests.HTTPError):
            system_agent.send_metrics(
                fake_config(send_max_retries=2),
                {"cpu_percent": 1, "memory_percent": 2, "disk_percent": 3, "swap_percent": 4, "process_count": 5},
            )

        assert len(appels) == 2  # HTTPError est une RequestException : elle aussi retentee


class FakeResponse:
    def __init__(self, status_code: int):
        self.status_code = status_code

    def raise_for_status(self):
        if self.status_code >= 400:
            raise requests.HTTPError(f"HTTP {self.status_code}")


class FakeIoCounters:
    def __init__(self, primary: int, secondary: int):
        self.read_bytes = primary
        self.write_bytes = secondary
        self.bytes_recv = primary
        self.bytes_sent = secondary


class FakeVirtualMemory:
    percent = 55.0
    total = 16 * 1024 * 1024 * 1024
    used = 8 * 1024 * 1024 * 1024


class FakeDiskUsage:
    percent = 70.0
    total = 500 * 1024 * 1024 * 1024
    used = 350 * 1024 * 1024 * 1024


class FakeSwap:
    percent = 12.0
