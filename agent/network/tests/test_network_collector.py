import json

import pytest
import requests

import network_collector
from network_collector import CollectorConfig, NetworkEquipment


def fake_config(**overrides) -> CollectorConfig:
    base = dict(
        backend_url="http://backend.local",
        equipments_config_path="equipments.json",
        interval_seconds=60,
        ping_timeout_seconds=2,
        snmp_timeout_seconds=3,
        request_timeout_seconds=10,
    )
    base.update(overrides)
    return CollectorConfig(**base)


def fake_equipment(**overrides) -> NetworkEquipment:
    base = dict(
        nom="sw-core-01",
        equipment_id="eq-1",
        api_key="secret",
        ip_address="10.0.0.1",
        snmp_community="public",
        snmp_port=161,
        interface_index=1,
    )
    base.update(overrides)
    return NetworkEquipment(**base)


class TestLoadEquipments:
    def test_applique_les_valeurs_par_defaut(self, tmp_path):
        chemin = tmp_path / "equipments.json"
        chemin.write_text(json.dumps([
            {"nom": "sw-core-01", "equipment_id": "eq-1", "api_key": "cle-1", "ip_address": "10.0.0.1"},
        ]))

        equipements = network_collector.load_equipments(str(chemin))

        assert len(equipements) == 1
        equipement = equipements[0]
        assert equipement.snmp_community == "public"
        assert equipement.snmp_port == 161
        assert equipement.interface_index == 1

    def test_les_valeurs_explicites_ne_sont_pas_ecrasees(self, tmp_path):
        chemin = tmp_path / "equipments.json"
        chemin.write_text(json.dumps([{
            "nom": "sw-core-01", "equipment_id": "eq-1", "api_key": "cle-1", "ip_address": "10.0.0.1",
            "snmp_community": "prive", "snmp_port": 1161, "interface_index": 3,
        }]))

        equipement = network_collector.load_equipments(str(chemin))[0]

        assert equipement.snmp_community == "prive"
        assert equipement.snmp_port == 1161
        assert equipement.interface_index == 3


class TestComputeRates:
    def test_calcule_la_bande_passante_et_le_taux_d_erreur(self):
        previous = {"in_octets": 1000, "out_octets": 500, "in_errors": 0, "out_errors": 0,
                    "in_packets": 100, "out_packets": 50}
        current = {"in_octets": 2000, "out_octets": 1500, "in_errors": 2, "out_errors": 0,
                   "in_packets": 150, "out_packets": 100}

        bandwidth_mbps, error_rate_percent = network_collector.compute_rates(previous, current, elapsed_seconds=10)

        # (1000 + 1000) octets / 10s * 8 bits / 1e6 = 0.0016 Mb/s
        assert bandwidth_mbps == pytest.approx(0.0016)
        # 2 erreurs / 100 paquets = 2%
        assert error_rate_percent == pytest.approx(2.0)

    def test_temps_ecoule_nul_ou_negatif_renvoie_none(self):
        assert network_collector.compute_rates({}, {}, elapsed_seconds=0) == (None, None)
        assert network_collector.compute_rates({}, {}, elapsed_seconds=-1) == (None, None)

    def test_aucun_paquet_ecoule_ne_produit_pas_de_division_par_zero(self):
        compteurs = {"in_octets": 0, "out_octets": 0, "in_errors": 0, "out_errors": 0,
                     "in_packets": 0, "out_packets": 0}

        _, error_rate_percent = network_collector.compute_rates(compteurs, compteurs, elapsed_seconds=10)

        assert error_rate_percent == 0.0

    def test_un_compteur_reinitialise_par_le_materiel_ne_produit_pas_de_delta_negatif(self):
        previous = {"in_octets": 5000, "out_octets": 0, "in_errors": 0, "out_errors": 0,
                    "in_packets": 500, "out_packets": 0}
        current = {"in_octets": 100, "out_octets": 0, "in_errors": 0, "out_errors": 0,
                   "in_packets": 10, "out_packets": 0}  # compteur reparti de zero

        bandwidth_mbps, _ = network_collector.compute_rates(previous, current, elapsed_seconds=10)

        assert bandwidth_mbps == 0.0


class TestPingEquipment:
    def test_equipement_joignable_renvoie_la_latence(self, monkeypatch):
        monkeypatch.setattr(network_collector, "icmp_ping", lambda *a, **k: FakePingResult(is_alive=True, avg_rtt=4.2))

        latence, joignable = network_collector.ping_equipment(fake_equipment(), timeout=2)

        assert latence == 4.2
        assert joignable is True

    def test_equipement_injoignable_renvoie_none(self, monkeypatch):
        monkeypatch.setattr(network_collector, "icmp_ping", lambda *a, **k: FakePingResult(is_alive=False, avg_rtt=0))

        latence, joignable = network_collector.ping_equipment(fake_equipment(), timeout=2)

        assert latence is None
        assert joignable is False

    def test_erreur_icmp_renvoie_none_sans_lever(self, monkeypatch):
        def leve(*a, **k):
            raise OSError("operation non permise")

        monkeypatch.setattr(network_collector, "icmp_ping", leve)

        latence, joignable = network_collector.ping_equipment(fake_equipment(), timeout=2)

        assert latence is None
        assert joignable is False


class TestSnmpGetValues:
    def test_renvoie_un_dictionnaire_indexe_par_cle_d_oid(self, monkeypatch):
        # Ordre de OID_TEMPLATES : les 6 compteurs de trafic, puis if_oper_status, puis sys_up_time.
        valeurs = [100, 200, 0, 0, 10, 20, 1, 123456]

        async def fausse_requete_snmp(*a, **k):
            return valeurs

        monkeypatch.setattr(network_collector, "_snmp_get_async", fausse_requete_snmp)

        resultat = network_collector.snmp_get_values(fake_equipment(), timeout=3)

        assert resultat == {
            "in_octets": 100, "out_octets": 200, "in_errors": 0,
            "out_errors": 0, "in_packets": 10, "out_packets": 20,
            "if_oper_status": 1, "sys_up_time": 123456,
        }

    def test_reponse_snmp_invalide_renvoie_none(self, monkeypatch):
        async def fausse_requete_snmp(*a, **k):
            return None

        monkeypatch.setattr(network_collector, "_snmp_get_async", fausse_requete_snmp)

        assert network_collector.snmp_get_values(fake_equipment(), timeout=3) is None

    def test_erreur_reseau_renvoie_none_sans_lever(self, monkeypatch):
        async def fausse_requete_snmp(*a, **k):
            raise OSError("timeout SNMP")

        monkeypatch.setattr(network_collector, "_snmp_get_async", fausse_requete_snmp)

        assert network_collector.snmp_get_values(fake_equipment(), timeout=3) is None


class TestSendNetworkMetrics:
    def test_envoie_le_payload_avec_la_cle_api_de_l_equipement(self, monkeypatch):
        appels = []
        monkeypatch.setattr(network_collector.requests, "post", lambda *a, **k: appels.append((a, k)) or FakeResponse(200))

        network_collector.send_network_metrics(fake_config(), fake_equipment(api_key="cle-du-switch"), {"latency_ms": 1.2})

        assert len(appels) == 1
        args, kwargs = appels[0]
        assert args[0] == "http://backend.local/api/v1/metrics/network"
        assert kwargs["headers"] == {"X-API-Key": "cle-du-switch"}
        assert kwargs["json"] == {"latency_ms": 1.2}

    def test_erreur_reseau_est_absorbee_sans_lever(self, monkeypatch):
        def leve(*a, **k):
            raise requests.RequestException("connexion refusee")

        monkeypatch.setattr(network_collector.requests, "post", leve)

        # Ne doit pas lever : un equipement en echec ne doit pas arreter le cycle des autres.
        network_collector.send_network_metrics(fake_config(), fake_equipment(), {"latency_ms": 1.2})


class TestPollEquipment:
    def test_equipement_injoignable_envoie_un_payload_minimal_sans_snmp(self, monkeypatch):
        monkeypatch.setattr(network_collector, "ping_equipment", lambda *a, **k: (None, False))
        appelle_snmp = []
        monkeypatch.setattr(network_collector, "snmp_get_values", lambda *a, **k: appelle_snmp.append(1))
        payloads = []
        monkeypatch.setattr(network_collector, "send_network_metrics", lambda cfg, eq, payload: payloads.append(payload))

        network_collector.poll_equipment(fake_config(), fake_equipment(equipment_id="eq-1"), {})

        assert payloads == [{"equipment_id": "eq-1"}]
        assert appelle_snmp == []

    def test_premier_cycle_ne_calcule_aucun_debit_mais_donne_uptime_et_etat_interface(self, monkeypatch):
        monkeypatch.setattr(network_collector, "ping_equipment", lambda *a, **k: (3.5, True))
        monkeypatch.setattr(
            network_collector, "snmp_get_values",
            lambda *a, **k: {"in_octets": 100, "out_octets": 100, "in_errors": 0, "out_errors": 0,
                              "in_packets": 10, "out_packets": 10, "if_oper_status": 1, "sys_up_time": 500_000},
        )
        payloads = []
        monkeypatch.setattr(network_collector, "send_network_metrics", lambda cfg, eq, payload: payloads.append(payload))
        previous_readings: dict = {}

        network_collector.poll_equipment(fake_config(), fake_equipment(equipment_id="eq-1"), previous_readings)

        assert payloads[0]["latency_ms"] == 3.5
        assert "bandwidth_mbps" not in payloads[0]
        assert payloads[0]["uptime_seconds"] == 5000.0  # sys_up_time / 100
        assert payloads[0]["interface_up"] == 1
        assert "eq-1" in previous_readings  # amorce pour le cycle suivant

    def test_interface_down_est_reportee_comme_indisponible(self, monkeypatch):
        monkeypatch.setattr(network_collector, "ping_equipment", lambda *a, **k: (3.5, True))
        monkeypatch.setattr(
            network_collector, "snmp_get_values",
            lambda *a, **k: {"in_octets": 0, "out_octets": 0, "in_errors": 0, "out_errors": 0,
                              "in_packets": 0, "out_packets": 0, "if_oper_status": 2, "sys_up_time": 100},
        )
        payloads = []
        monkeypatch.setattr(network_collector, "send_network_metrics", lambda cfg, eq, payload: payloads.append(payload))

        network_collector.poll_equipment(fake_config(), fake_equipment(equipment_id="eq-1"), {})

        assert payloads[0]["interface_up"] == 0

    def test_second_cycle_calcule_bande_passante_et_taux_d_erreur(self, monkeypatch):
        monkeypatch.setattr(network_collector, "ping_equipment", lambda *a, **k: (2.0, True))
        compteurs_appel = iter([
            {"in_octets": 0, "out_octets": 0, "in_errors": 0, "out_errors": 0, "in_packets": 0, "out_packets": 0,
             "if_oper_status": 1, "sys_up_time": 0},
            {"in_octets": 1_000_000, "out_octets": 0, "in_errors": 0, "out_errors": 0, "in_packets": 100,
             "out_packets": 0, "if_oper_status": 1, "sys_up_time": 1000},
        ])
        monkeypatch.setattr(network_collector, "snmp_get_values", lambda *a, **k: next(compteurs_appel))
        temps = iter([0.0, 10.0])
        monkeypatch.setattr(network_collector.time, "monotonic", lambda: next(temps))
        payloads = []
        monkeypatch.setattr(network_collector, "send_network_metrics", lambda cfg, eq, payload: payloads.append(payload))
        previous_readings: dict = {}
        equipement = fake_equipment(equipment_id="eq-1")

        network_collector.poll_equipment(fake_config(), equipement, previous_readings)  # amorce
        network_collector.poll_equipment(fake_config(), equipement, previous_readings)  # calcule le debit

        assert "bandwidth_mbps" in payloads[1]
        assert payloads[1]["bandwidth_mbps"] > 0
        assert payloads[1]["error_rate_percent"] == 0.0
        assert payloads[1]["uptime_seconds"] == 10.0


class FakeResponse:
    def __init__(self, status_code: int):
        self.status_code = status_code

    def raise_for_status(self):
        if self.status_code >= 400:
            raise requests.HTTPError(f"HTTP {self.status_code}")


class FakePingResult:
    def __init__(self, is_alive: bool, avg_rtt: float):
        self.is_alive = is_alive
        self.avg_rtt = avg_rtt
