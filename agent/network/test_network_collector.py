"""Tests pour network_collector.py : ICMP, SNMP, calcul de debit/taux d'erreur."""

import types

import pytest

import network_collector
from network_collector import (
    CollectorConfig,
    NetworkEquipment,
    compute_rates,
    ping_equipment,
    poll_equipment,
    snmp_get_counters,
)


def _equipment(**overrides) -> NetworkEquipment:
    base = dict(
        nom="Routeur coeur",
        equipment_id="eq-1",
        api_key="cle-secrete",
        ip_address="10.0.0.1",
        snmp_community="public",
        snmp_port=161,
        interface_index=1,
    )
    base.update(overrides)
    return NetworkEquipment(**base)


def _config(**overrides) -> CollectorConfig:
    base = dict(
        backend_url="http://backend.local",
        equipments_config_path="equipments.json",
        interval_seconds=60,
        ping_timeout_seconds=2.0,
        snmp_timeout_seconds=3.0,
        request_timeout_seconds=10,
    )
    base.update(overrides)
    return CollectorConfig(**base)


def _counters(in_octets, out_octets, in_errors=0, out_errors=0, in_packets=1000, out_packets=1000):
    return {
        "in_octets": in_octets,
        "out_octets": out_octets,
        "in_errors": in_errors,
        "out_errors": out_errors,
        "in_packets": in_packets,
        "out_packets": out_packets,
    }


class TestComputeRates:
    def test_calcule_le_debit_et_le_taux_derreur_entre_deux_cycles(self):
        precedent = _counters(in_octets=1_000_000, out_octets=500_000, in_errors=10, out_errors=5,
                               in_packets=10_000, out_packets=8_000)
        courant = _counters(in_octets=1_000_000 + 625_000, out_octets=500_000 + 625_000,
                             in_errors=10 + 3, out_errors=5 + 2, in_packets=10_000 + 500, out_packets=8_000 + 500)

        bandwidth_mbps, error_rate_percent = compute_rates(precedent, courant, elapsed_seconds=10.0)

        # 1 250 000 octets * 8 bits / 10s / 1_000_000 = 1 Mbps.
        assert bandwidth_mbps == pytest.approx(1.0)
        # 5 erreurs pour 1000 paquets = 0.5%.
        assert error_rate_percent == pytest.approx(0.5)

    def test_taux_derreur_nul_sans_paquet_echange(self):
        precedent = _counters(in_octets=0, out_octets=0, in_packets=100, out_packets=100)
        courant = _counters(in_octets=0, out_octets=0, in_packets=100, out_packets=100)

        _, error_rate_percent = compute_rates(precedent, courant, elapsed_seconds=5.0)

        assert error_rate_percent == 0.0

    def test_delta_plafonne_a_zero_si_les_compteurs_repartent_de_zero(self):
        """Un redemarrage d'equipement remet les compteurs SNMP a zero : le delta ne
        doit jamais devenir negatif."""
        precedent = _counters(in_octets=1_000_000, out_octets=1_000_000, in_packets=50_000, out_packets=50_000)
        courant = _counters(in_octets=100, out_octets=100, in_packets=10, out_packets=10)

        bandwidth_mbps, error_rate_percent = compute_rates(precedent, courant, elapsed_seconds=10.0)

        assert bandwidth_mbps == 0.0
        assert error_rate_percent == 0.0

    def test_aucun_debit_sans_temps_ecoule(self):
        precedent = _counters(in_octets=0, out_octets=0)
        courant = _counters(in_octets=1000, out_octets=1000)

        assert compute_rates(precedent, courant, elapsed_seconds=0) == (None, None)


class TestPingEquipment:
    def test_equipement_joignable(self, mocker):
        mocker.patch(
            "network_collector.icmp_ping",
            return_value=types.SimpleNamespace(is_alive=True, avg_rtt=12.5),
        )

        latence, joignable = ping_equipment(_equipment(), timeout=2.0)

        assert latence == 12.5
        assert joignable is True

    def test_equipement_injoignable_sans_reponse(self, mocker):
        mocker.patch(
            "network_collector.icmp_ping",
            return_value=types.SimpleNamespace(is_alive=False, avg_rtt=0.0),
        )

        latence, joignable = ping_equipment(_equipment(), timeout=2.0)

        assert latence is None
        assert joignable is False

    def test_equipement_injoignable_sur_exception_icmp(self, mocker):
        mocker.patch("network_collector.icmp_ping", side_effect=OSError("reseau injoignable"))

        latence, joignable = ping_equipment(_equipment(), timeout=2.0)

        assert latence is None
        assert joignable is False


class TestSnmpGetCounters:
    def test_lit_les_six_compteurs_dans_lordre(self, mocker):
        # `_snmp_get_async` est une coroutine : la patcher (plutot que
        # `asyncio.run`) laisse le vrai `asyncio.run` l'executer normalement,
        # sans avertissement de coroutine jamais attendue.
        mocker.patch("network_collector._snmp_get_async", return_value=[100, 200, 1, 2, 300, 400])

        compteurs = snmp_get_counters(_equipment(), timeout=3.0)

        assert compteurs == {
            "in_octets": 100, "out_octets": 200, "in_errors": 1,
            "out_errors": 2, "in_packets": 300, "out_packets": 400,
        }

    def test_reponse_invalide_retourne_none(self, mocker):
        mocker.patch("network_collector._snmp_get_async", return_value=None)

        assert snmp_get_counters(_equipment(), timeout=3.0) is None

    def test_exception_snmp_ne_remonte_pas(self, mocker):
        mocker.patch("network_collector._snmp_get_async", side_effect=TimeoutError("pas de reponse SNMP"))

        assert snmp_get_counters(_equipment(), timeout=3.0) is None


class TestPollEquipment:
    def test_equipement_injoignable_envoie_un_paquet_minimal_sans_snmp(self, mocker):
        mocker.patch("network_collector.ping_equipment", return_value=(None, False))
        snmp = mocker.patch("network_collector.snmp_get_counters")
        envoi = mocker.patch("network_collector.send_network_metrics")

        previous_readings = {}
        poll_equipment(_config(), _equipment(), previous_readings)

        snmp.assert_not_called()
        envoi.assert_called_once()
        _, _, payload = envoi.call_args.args
        assert payload == {"equipment_id": "eq-1"}
        assert previous_readings == {}

    def test_premier_cycle_ne_calcule_pas_encore_de_debit(self, mocker):
        mocker.patch("network_collector.ping_equipment", return_value=(5.0, True))
        mocker.patch("network_collector.snmp_get_counters", return_value=_counters(1000, 2000))
        envoi = mocker.patch("network_collector.send_network_metrics")

        previous_readings = {}
        poll_equipment(_config(), _equipment(), previous_readings)

        _, _, payload = envoi.call_args.args
        assert payload["latency_ms"] == 5.0
        assert "bandwidth_mbps" not in payload
        assert "eq-1" in previous_readings

    def test_second_cycle_calcule_le_debit_et_le_taux_derreur(self, mocker):
        mocker.patch("network_collector.ping_equipment", return_value=(5.0, True))
        envoi = mocker.patch("network_collector.send_network_metrics")
        equipement = _equipment()
        config = _config()

        mocker.patch("network_collector.snmp_get_counters",
                      return_value=_counters(1_000_000, 500_000, in_packets=10_000, out_packets=8_000))
        mocker.patch("network_collector.time.monotonic", return_value=100.0)
        previous_readings = {}
        poll_equipment(config, equipement, previous_readings)

        mocker.patch(
            "network_collector.snmp_get_counters",
            return_value=_counters(1_000_000 + 625_000, 500_000 + 625_000,
                                    in_errors=5, out_errors=5, in_packets=10_500, out_packets=8_500),
        )
        mocker.patch("network_collector.time.monotonic", return_value=110.0)
        poll_equipment(config, equipement, previous_readings)

        _, _, payload = envoi.call_args.args
        assert payload["bandwidth_mbps"] == pytest.approx(1.0, rel=1e-3)
        assert payload["error_rate_percent"] == pytest.approx(1.0, rel=1e-3)

    def test_equipement_injoignable_efface_la_lecture_precedente(self, mocker):
        mocker.patch("network_collector.send_network_metrics")
        mocker.patch("network_collector.ping_equipment", return_value=(None, False))

        previous_readings = {"eq-1": {"timestamp": 42.0}}
        poll_equipment(_config(), _equipment(), previous_readings)

        # Un equipement injoignable ne touche pas au dernier releve connu : le
        # prochain cycle joignable calculera son delta contre cette valeur.
        assert previous_readings == {"eq-1": {"timestamp": 42.0}}


def test_run_continue_apres_une_erreur_sur_un_equipement(mocker):
    """Un equipement en erreur ne doit pas empecher le sondage des autres."""
    config = _config(interval_seconds=0)
    equipement_en_panne = _equipment(nom="Switch en panne", equipment_id="eq-panne")
    equipement_ok = _equipment(nom="Switch ok", equipment_id="eq-ok")

    faux_arret = types.SimpleNamespace(stop_requested=False)
    mocker.patch("network_collector.GracefulShutdown", return_value=faux_arret)

    appels = []

    def faux_sondage(config, equipment, previous_readings):
        appels.append(equipment.equipment_id)
        if equipment.equipment_id == "eq-panne":
            raise ConnectionError("equipement injoignable")
        faux_arret.stop_requested = True

    mocker.patch("network_collector.poll_equipment", side_effect=faux_sondage)

    network_collector.run(config, [equipement_en_panne, equipement_ok])

    assert appels == ["eq-panne", "eq-ok"]
