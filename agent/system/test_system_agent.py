"""Tests pour system_agent.py : calcul des metriques, envoi et arret propre."""

import signal
import time as time_module
import types

import pytest
import requests

import system_agent
from system_agent import AgentConfig, GracefulShutdown, collect_core_metrics, collect_optional_metrics, send_metrics


def _config(**overrides) -> AgentConfig:
    base = dict(
        backend_url="http://backend.local",
        equipment_id="eq-1",
        api_key="cle-secrete",
        interval_seconds=60,
        request_timeout_seconds=10,
        tcp_targets=[],
        tcp_check_timeout_seconds=2.0,
        dns_check_hostname="",
        dns_check_timeout_seconds=2.0,
        log_file_path="",
        log_pattern=None,
        watched_file_path="",
        state_file_path="agent_state.json",
        modbus_host="",
        modbus_port=502,
        modbus_unit_id=1,
        modbus_register_address=0,
        modbus_register_type="holding",
        probe_charge_machine=False,
        probe_limites_ressources=False,
        probe_capteurs=False,
    )
    base.update(overrides)
    return AgentConfig(**base)


def _fake_disk_io(read_bytes, write_bytes):
    return types.SimpleNamespace(read_bytes=read_bytes, write_bytes=write_bytes)


def _fake_net_io(bytes_recv, bytes_sent):
    return types.SimpleNamespace(bytes_recv=bytes_recv, bytes_sent=bytes_sent)


@pytest.fixture(autouse=True)
def _psutil_de_base(mocker):
    """Sondes psutil communes, neutralisees a des valeurs fixes pour tous les tests."""
    mocker.patch("system_agent.psutil.cpu_percent", return_value=10.0)
    mocker.patch("system_agent.psutil.virtual_memory", return_value=types.SimpleNamespace(
        percent=42.5, total=8_000_000_000, used=3_000_000_000))
    mocker.patch("system_agent.psutil.disk_usage", return_value=types.SimpleNamespace(
        percent=61.2, total=500_000_000_000, used=300_000_000_000))
    mocker.patch("system_agent.psutil.swap_memory", return_value=types.SimpleNamespace(percent=5.0))
    mocker.patch("system_agent.psutil.pids", return_value=list(range(120)))
    mocker.patch("system_agent.psutil.boot_time", return_value=time_module.time() - 3600)
    mocker.patch("system_agent.count_listening_ports", return_value=None)
    mocker.patch("system_agent.checks.read_load_ratio", return_value=None)
    mocker.patch("system_agent.checks.read_resource_limits", return_value=(None, None))


class TestCollectCoreMetrics:
    def test_valeurs_de_base(self, mocker):
        mocker.patch("system_agent.psutil.disk_io_counters", return_value=_fake_disk_io(1000, 2000))
        mocker.patch("system_agent.psutil.net_io_counters", return_value=_fake_net_io(3000, 4000))

        metrics, io = collect_core_metrics(previous_io=None, config=_config())

        assert metrics["cpu_percent"] == 10.0
        assert metrics["memory_percent"] == 42.5
        assert metrics["disk_percent"] == 61.2
        assert metrics["swap_percent"] == 5.0
        assert metrics["process_count"] == 120
        assert io.disk_read_bytes == 1000
        assert io.net_bytes_sent == 4000

    def test_aucun_debit_sans_cycle_precedent(self, mocker):
        mocker.patch("system_agent.psutil.disk_io_counters", return_value=_fake_disk_io(1000, 2000))
        mocker.patch("system_agent.psutil.net_io_counters", return_value=_fake_net_io(3000, 4000))

        metrics, _ = collect_core_metrics(previous_io=None, config=_config())

        for cle in ("disk_read_kbps", "disk_write_kbps", "network_in_kbps", "network_out_kbps"):
            assert cle not in metrics

    def test_debits_calcules_par_delta_entre_deux_cycles(self, mocker):
        mocker.patch("system_agent.psutil.disk_io_counters", return_value=_fake_disk_io(1_000_000, 2_000_000))
        mocker.patch("system_agent.psutil.net_io_counters", return_value=_fake_net_io(500_000, 250_000))
        mocker.patch("system_agent.time.monotonic", return_value=100.0)
        _, premier_io = collect_core_metrics(previous_io=None, config=_config())

        mocker.patch(
            "system_agent.psutil.disk_io_counters",
            return_value=_fake_disk_io(1_000_000 + 10_240, 2_000_000 + 20_480),
        )
        mocker.patch(
            "system_agent.psutil.net_io_counters",
            return_value=_fake_net_io(500_000 + 5_120, 250_000 + 2_560),
        )
        mocker.patch("system_agent.time.monotonic", return_value=105.0)

        metrics, _ = collect_core_metrics(previous_io=premier_io, config=_config())

        # 5 secondes ecoulees entre les deux cycles.
        assert metrics["disk_read_kbps"] == pytest.approx(2.0)
        assert metrics["disk_write_kbps"] == pytest.approx(4.0)
        assert metrics["network_in_kbps"] == pytest.approx(1.0)
        assert metrics["network_out_kbps"] == pytest.approx(0.5)

    def test_debit_neutralise_a_zero_si_le_compteur_repart_de_zero(self, mocker):
        """Un redemarrage remet les compteurs cumulatifs de psutil a zero : le delta
        ne doit jamais devenir negatif, seulement plafonne a zero."""
        mocker.patch("system_agent.psutil.disk_io_counters", return_value=_fake_disk_io(1_000_000, 2_000_000))
        mocker.patch("system_agent.psutil.net_io_counters", return_value=_fake_net_io(500_000, 250_000))
        mocker.patch("system_agent.time.monotonic", return_value=100.0)
        _, premier_io = collect_core_metrics(previous_io=None, config=_config())

        mocker.patch("system_agent.psutil.disk_io_counters", return_value=_fake_disk_io(100, 200))
        mocker.patch("system_agent.psutil.net_io_counters", return_value=_fake_net_io(50, 25))
        mocker.patch("system_agent.time.monotonic", return_value=105.0)

        metrics, _ = collect_core_metrics(previous_io=premier_io, config=_config())

        assert metrics["disk_read_kbps"] == 0
        assert metrics["network_in_kbps"] == 0

    def test_charge_machine_ignoree_par_defaut(self, mocker):
        lire = mocker.patch("system_agent.checks.read_load_ratio", return_value=0.42)
        mocker.patch("system_agent.psutil.disk_io_counters", return_value=_fake_disk_io(0, 0))
        mocker.patch("system_agent.psutil.net_io_counters", return_value=_fake_net_io(0, 0))

        metrics, _ = collect_core_metrics(previous_io=None, config=_config(probe_charge_machine=False))

        lire.assert_not_called()
        assert "load_1min" not in metrics

    def test_charge_machine_lue_si_activee(self, mocker):
        mocker.patch("system_agent.checks.read_load_ratio", return_value=0.42)
        mocker.patch("system_agent.psutil.disk_io_counters", return_value=_fake_disk_io(0, 0))
        mocker.patch("system_agent.psutil.net_io_counters", return_value=_fake_net_io(0, 0))

        metrics, _ = collect_core_metrics(previous_io=None, config=_config(probe_charge_machine=True))

        assert metrics["load_1min"] == 0.42

    def test_limites_ressources_ignorees_par_defaut(self, mocker):
        lire = mocker.patch("system_agent.checks.read_resource_limits", return_value=(1024, 512))
        mocker.patch("system_agent.psutil.disk_io_counters", return_value=_fake_disk_io(0, 0))
        mocker.patch("system_agent.psutil.net_io_counters", return_value=_fake_net_io(0, 0))

        metrics, _ = collect_core_metrics(previous_io=None, config=_config(probe_limites_ressources=False))

        lire.assert_not_called()
        assert "open_files_limit" not in metrics
        assert "process_limit" not in metrics

    def test_limites_ressources_lues_si_activees(self, mocker):
        mocker.patch("system_agent.checks.read_resource_limits", return_value=(1024, 512))
        mocker.patch("system_agent.psutil.disk_io_counters", return_value=_fake_disk_io(0, 0))
        mocker.patch("system_agent.psutil.net_io_counters", return_value=_fake_net_io(0, 0))

        metrics, _ = collect_core_metrics(previous_io=None, config=_config(probe_limites_ressources=True))

        assert metrics["open_files_limit"] == 1024
        assert metrics["process_limit"] == 512


class TestCollectOptionalMetrics:
    def test_capteurs_ignores_par_defaut(self, mocker):
        lire = mocker.patch("system_agent.checks.read_sensors", return_value=(55.0, 3200))

        metrics = collect_optional_metrics(_config(probe_capteurs=False), state={})

        lire.assert_not_called()
        assert "temperature_max_celsius" not in metrics
        assert "fan_speed_rpm" not in metrics

    def test_capteurs_lus_si_actives(self, mocker):
        mocker.patch("system_agent.checks.read_sensors", return_value=(55.0, 3200))

        metrics = collect_optional_metrics(_config(probe_capteurs=True), state={})

        assert metrics["temperature_max_celsius"] == 55.0
        assert metrics["fan_speed_rpm"] == 3200


def test_send_metrics_poste_les_bonnes_donnees(mocker):
    config = _config(backend_url="http://backend.local", equipment_id="eq-1", api_key="cle-secrete",
                      request_timeout_seconds=7)
    poste = mocker.patch("system_agent.requests.post")

    send_metrics(config, {
        "cpu_percent": 1.0, "memory_percent": 2.0, "disk_percent": 3.0,
        "swap_percent": 4.0, "process_count": 5,
    })

    poste.assert_called_once()
    _, kwargs = poste.call_args
    assert kwargs["headers"] == {"X-API-Key": "cle-secrete"}
    assert kwargs["timeout"] == 7
    assert kwargs["json"]["equipment_id"] == "eq-1"
    poste.return_value.raise_for_status.assert_called_once()


def test_send_metrics_propage_une_erreur_http(mocker):
    poste = mocker.patch("system_agent.requests.post")
    poste.return_value.raise_for_status.side_effect = requests.HTTPError("403 Forbidden")

    with pytest.raises(requests.RequestException):
        send_metrics(_config(), {
            "cpu_percent": 1.0, "memory_percent": 1.0, "disk_percent": 1.0,
            "swap_percent": 1.0, "process_count": 1,
        })


class TestGracefulShutdown:
    def test_enregistre_sigint_et_sigterm(self, mocker):
        enregistrer = mocker.patch("system_agent.signal.signal")

        shutdown = GracefulShutdown()

        assert shutdown.stop_requested is False
        enregistrer.assert_any_call(signal.SIGINT, shutdown._handle_signal)
        enregistrer.assert_any_call(signal.SIGTERM, shutdown._handle_signal)

    def test_arret_demande_apres_un_signal(self, mocker):
        mocker.patch("system_agent.signal.signal")
        shutdown = GracefulShutdown()

        shutdown._handle_signal(signal.SIGINT, None)

        assert shutdown.stop_requested is True


def test_run_continue_apres_une_erreur_denvoi_sur_un_cycle(mocker):
    """Le cycle suivant doit quand meme se derouler apres l'echec d'un envoi."""
    config = _config(interval_seconds=0)

    faux_arret = types.SimpleNamespace(stop_requested=False)
    mocker.patch("system_agent.GracefulShutdown", return_value=faux_arret)
    mocker.patch("system_agent.checks.load_state", return_value={})
    mocker.patch("system_agent.checks.save_state")
    mocker.patch("system_agent.collect_optional_metrics", return_value={})

    compteur = {"n": 0}

    def fausse_collecte(previous_io, config):
        compteur["n"] += 1
        if compteur["n"] >= 2:
            faux_arret.stop_requested = True
        return {
            "cpu_percent": 1.0, "memory_percent": 1.0, "disk_percent": 1.0,
            "swap_percent": 1.0, "process_count": 1,
        }, None

    mocker.patch("system_agent.collect_core_metrics", side_effect=fausse_collecte)
    envoi = mocker.patch(
        "system_agent.send_metrics",
        side_effect=[requests.RequestException("echec reseau simule"), None],
    )

    system_agent.run(config)

    assert envoi.call_count == 2
    assert compteur["n"] == 2


def test_run_sarrete_immediatement_si_larret_est_deja_demande(mocker):
    faux_arret = types.SimpleNamespace(stop_requested=True)
    mocker.patch("system_agent.GracefulShutdown", return_value=faux_arret)
    mocker.patch("system_agent.checks.load_state", return_value={})
    collecte = mocker.patch("system_agent.collect_core_metrics")

    system_agent.run(_config())

    collecte.assert_not_called()
