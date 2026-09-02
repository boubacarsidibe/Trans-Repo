import json
import threading
import urllib.error
import urllib.request

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


class TestValidateRedundancyConfig:
    def test_role_unique_par_defaut_ne_leve_pas(self):
        network_collector.validate_redundancy_config(fake_config())

    def test_role_primaire_ne_leve_pas(self):
        network_collector.validate_redundancy_config(fake_config(collector_role="primaire"))

    def test_role_inconnu_leve(self):
        with pytest.raises(SystemExit):
            network_collector.validate_redundancy_config(fake_config(collector_role="tertiaire"))

    def test_secondaire_sans_url_de_la_primaire_leve(self):
        with pytest.raises(SystemExit):
            network_collector.validate_redundancy_config(
                fake_config(collector_role="secondaire", peer_heartbeat_url=""))

    def test_secondaire_avec_url_de_la_primaire_ne_leve_pas(self):
        network_collector.validate_redundancy_config(
            fake_config(collector_role="secondaire", peer_heartbeat_url="http://primaire:8091/heartbeat"))


class TestHeartbeatServer:
    def test_repond_actif_sur_heartbeat(self):
        server = network_collector.start_heartbeat_server(0, "collecteur-test")
        try:
            port = server.server_address[1]
            with urllib.request.urlopen(f"http://127.0.0.1:{port}/heartbeat", timeout=2) as reponse:
                corps = json.loads(reponse.read())

            assert reponse.status == 200
            assert corps == {"collector_id": "collecteur-test", "actif": True}
        finally:
            server.shutdown()

    def test_renvoie_404_sur_une_autre_route(self):
        server = network_collector.start_heartbeat_server(0, "collecteur-test")
        try:
            port = server.server_address[1]
            try:
                urllib.request.urlopen(f"http://127.0.0.1:{port}/autre", timeout=2)
                assert False, "une reponse 404 aurait du lever une HTTPError"
            except urllib.error.HTTPError as exc:
                assert exc.code == 404
        finally:
            server.shutdown()


class TestPollPeerHeartbeat:
    def test_reponse_ok_renvoie_true(self, monkeypatch):
        monkeypatch.setattr(network_collector.requests, "get", lambda *a, **k: FakeResponse(200))

        assert network_collector.poll_peer_heartbeat("http://primaire:8091/heartbeat", timeout=2) is True

    def test_erreur_reseau_renvoie_false_sans_lever(self, monkeypatch):
        def leve(*a, **k):
            raise requests.RequestException("connexion refusee")

        monkeypatch.setattr(network_collector.requests, "get", leve)

        assert network_collector.poll_peer_heartbeat("http://primaire:8091/heartbeat", timeout=2) is False

    def test_code_erreur_http_renvoie_false(self, monkeypatch):
        monkeypatch.setattr(network_collector.requests, "get", lambda *a, **k: FakeResponse(500))

        assert network_collector.poll_peer_heartbeat("http://primaire:8091/heartbeat", timeout=2) is False


class TestEvaluateStandbyCycle:
    def test_heartbeat_present_reinitialise_le_compteur(self, monkeypatch):
        monkeypatch.setattr(network_collector, "poll_peer_heartbeat", lambda *a, **k: True)

        bascule, compteur = network_collector.evaluate_standby_cycle(
            fake_config(collector_role="secondaire", failover_cycles_toleres=3), consecutive_failures=2)

        assert bascule is False
        assert compteur == 0

    def test_bascule_uniquement_au_seuil_configure(self, monkeypatch):
        monkeypatch.setattr(network_collector, "poll_peer_heartbeat", lambda *a, **k: False)
        config = fake_config(collector_role="secondaire", failover_cycles_toleres=3)

        bascule_1, compteur_1 = network_collector.evaluate_standby_cycle(config, consecutive_failures=0)
        bascule_2, compteur_2 = network_collector.evaluate_standby_cycle(config, consecutive_failures=compteur_1)
        bascule_3, compteur_3 = network_collector.evaluate_standby_cycle(config, consecutive_failures=compteur_2)

        assert (bascule_1, compteur_1) == (False, 1)
        assert (bascule_2, compteur_2) == (False, 2)
        assert (bascule_3, compteur_3) == (True, 3)


class TestSendCollectorHeartbeat:
    def test_envoie_le_heartbeat_quand_id_et_cle_sont_configures(self, monkeypatch):
        appels = []
        monkeypatch.setattr(network_collector.requests, "post", lambda *a, **k: appels.append((a, k)) or FakeResponse(201))
        config = fake_config(collector_id="collecteur-1", collector_api_key="cle-collecteur")

        network_collector.send_collector_heartbeat(config, actif=True)

        assert len(appels) == 1
        args, kwargs = appels[0]
        assert args[0] == "http://backend.local/api/v1/collectors/heartbeat"
        assert kwargs["headers"] == {"X-Collector-Key": "cle-collecteur"}
        assert kwargs["json"] == {"collector_id": "collecteur-1", "actif": True}

    def test_aucun_appel_sans_identifiant_ni_cle(self, monkeypatch):
        appels = []
        monkeypatch.setattr(network_collector.requests, "post", lambda *a, **k: appels.append(1))

        network_collector.send_collector_heartbeat(fake_config(), actif=True)

        assert appels == []

    def test_erreur_reseau_est_absorbee_sans_lever(self, monkeypatch):
        def leve(*a, **k):
            raise requests.RequestException("connexion refusee")

        monkeypatch.setattr(network_collector.requests, "post", leve)
        config = fake_config(collector_id="collecteur-1", collector_api_key="cle-collecteur")

        network_collector.send_collector_heartbeat(config, actif=True)


class TestPollEquipmentLock:
    def test_fonctionne_a_l_identique_avec_un_verrou_reel(self, monkeypatch):
        # Le verrou est optionnel (utilise seulement quand le recepteur de traps
        # tourne en parallele) : avec un vrai threading.Lock, le resultat doit rester
        # identique a l'appel sans verrou, et le verrou doit ressortir libere.
        monkeypatch.setattr(network_collector, "ping_equipment", lambda *a, **k: (3.5, True))
        monkeypatch.setattr(
            network_collector, "snmp_get_values",
            lambda *a, **k: {"in_octets": 100, "out_octets": 100, "in_errors": 0, "out_errors": 0,
                              "in_packets": 10, "out_packets": 10, "if_oper_status": 1, "sys_up_time": 500_000},
        )
        payloads = []
        monkeypatch.setattr(network_collector, "send_network_metrics", lambda cfg, eq, payload: payloads.append(payload))
        lock = threading.Lock()

        network_collector.poll_equipment(fake_config(), fake_equipment(equipment_id="eq-1"), {}, lock)

        assert payloads[0]["uptime_seconds"] == 5000.0
        assert payloads[0]["interface_up"] == 1
        assert not lock.locked()


class TestFindEquipmentByIp:
    def test_trouve_l_equipement_dont_l_ip_correspond(self):
        equipements = [fake_equipment(equipment_id="eq-1", ip_address="10.0.0.1"),
                       fake_equipment(equipment_id="eq-2", ip_address="10.0.0.2")]

        trouve = network_collector.find_equipment_by_ip(equipements, "10.0.0.2")

        assert trouve.equipment_id == "eq-2"

    def test_renvoie_none_si_aucune_ip_ne_correspond(self):
        equipements = [fake_equipment(ip_address="10.0.0.1")]

        assert network_collector.find_equipment_by_ip(equipements, "10.0.0.99") is None


class TestDescribeTrap:
    def test_reconnait_un_trap_standard(self):
        var_binds = [("1.3.6.1.6.3.1.1.4.1.0", "1.3.6.1.6.3.1.1.5.3")]

        assert network_collector.describe_trap(var_binds) == "linkDown"

    def test_renvoie_l_oid_brut_pour_un_trap_non_repertorie(self):
        var_binds = [("1.3.6.1.6.3.1.1.4.1.0", "1.3.6.1.4.1.9999.1")]

        assert network_collector.describe_trap(var_binds) == "1.3.6.1.4.1.9999.1"

    def test_renvoie_inconnu_si_snmp_trap_oid_absent(self):
        var_binds = [("1.3.6.1.2.1.1.3.0", "12345")]

        assert network_collector.describe_trap(var_binds) == "inconnu"


class TestHandleTrap:
    def test_trap_d_une_ip_non_declaree_est_ignore(self, monkeypatch):
        appels = []
        monkeypatch.setattr(network_collector, "poll_equipment", lambda *a, **k: appels.append(a))
        equipements = [fake_equipment(ip_address="10.0.0.1")]

        network_collector.handle_trap(fake_config(), equipements, {}, "10.0.0.99", [])

        assert appels == []

    def test_trap_d_un_equipement_declare_declenche_un_sondage_immediat(self, monkeypatch):
        appels = []
        monkeypatch.setattr(network_collector, "poll_equipment", lambda *a, **k: appels.append(a))
        equipement = fake_equipment(equipment_id="eq-1", ip_address="10.0.0.1")
        config = fake_config()
        previous_readings = {}
        lock = threading.Lock()

        network_collector.handle_trap(
            config, [equipement], previous_readings, "10.0.0.1",
            [("1.3.6.1.6.3.1.1.4.1.0", "1.3.6.1.6.3.1.1.5.3")], lock)

        assert len(appels) == 1
        assert appels[0] == (config, equipement, previous_readings, lock)

    def test_une_erreur_lors_du_sondage_declenche_par_le_trap_est_absorbee(self, monkeypatch):
        def leve(*a, **k):
            raise RuntimeError("panne SNMP")

        monkeypatch.setattr(network_collector, "poll_equipment", leve)
        equipement = fake_equipment(ip_address="10.0.0.1")

        # Ne doit pas lever : un trap malforme ou un equipement en echec ne doit pas
        # arreter le thread du recepteur de traps.
        network_collector.handle_trap(fake_config(), [equipement], {}, "10.0.0.1", [])


class _ArreterApresNCycles:
    """Remplace GracefulShutdown dans les tests de run() : s'arrete au bout de N
    passages dans la boucle plutot que d'attendre un signal OS."""

    def __init__(self, n: int):
        self._restants = n

    @property
    def stop_requested(self) -> bool:
        if self._restants <= 0:
            return True
        self._restants -= 1
        return False


class FakeHeartbeatServer:
    def __init__(self):
        self.shutdown_appele = False

    def shutdown(self):
        self.shutdown_appele = True


class TestRunTrapReceiverSuitLActivite:
    """Redondance (issue #157) + traps SNMP (cf. tete de module) : le recepteur
    de traps ne doit tourner que sur l'instance active, jamais sur une
    secondaire en veille."""

    def test_instance_primaire_demarre_le_recepteur_de_traps_des_le_depart(self, monkeypatch):
        monkeypatch.setattr(network_collector, "GracefulShutdown", lambda: _ArreterApresNCycles(1))
        monkeypatch.setattr(network_collector, "start_heartbeat_server",
                             lambda *a, **k: FakeHeartbeatServer())
        appels_trap = []
        monkeypatch.setattr(network_collector, "start_trap_receiver_thread",
                             lambda *a, **k: appels_trap.append(1))
        monkeypatch.setattr(network_collector, "poll_equipment", lambda *a, **k: None)
        monkeypatch.setattr(network_collector, "send_collector_heartbeat", lambda *a, **k: None)

        config = fake_config(collector_role="primaire", interval_seconds=0)
        network_collector.run(config, [fake_equipment()], {})

        assert appels_trap == [1]

    def test_instance_secondaire_en_veille_ne_demarre_pas_le_recepteur_de_traps(self, monkeypatch):
        # 2 cycles, jamais de bascule (heartbeat de la primaire toujours present) :
        # le recepteur de traps ne doit jamais demarrer.
        monkeypatch.setattr(network_collector, "GracefulShutdown", lambda: _ArreterApresNCycles(2))
        monkeypatch.setattr(network_collector, "poll_peer_heartbeat", lambda *a, **k: True)
        appels_trap = []
        monkeypatch.setattr(network_collector, "start_trap_receiver_thread",
                             lambda *a, **k: appels_trap.append(1))
        monkeypatch.setattr(network_collector, "start_heartbeat_server",
                             lambda *a, **k: FakeHeartbeatServer())

        config = fake_config(collector_role="secondaire", peer_heartbeat_url="http://primaire:8091/heartbeat",
                              failover_cycles_toleres=3, interval_seconds=0)
        network_collector.run(config, [fake_equipment()], {})

        assert appels_trap == []

    def test_instance_secondaire_demarre_le_recepteur_de_traps_seulement_apres_bascule(self, monkeypatch):
        # 2 cycles, bascule au 2e (seuil atteint) : le recepteur de traps ne doit
        # demarrer qu'a ce moment-la, pas avant.
        monkeypatch.setattr(network_collector, "GracefulShutdown", lambda: _ArreterApresNCycles(2))
        monkeypatch.setattr(network_collector, "poll_peer_heartbeat", lambda *a, **k: False)
        appels_trap = []
        monkeypatch.setattr(network_collector, "start_trap_receiver_thread",
                             lambda *a, **k: appels_trap.append(1))
        monkeypatch.setattr(network_collector, "start_heartbeat_server",
                             lambda *a, **k: FakeHeartbeatServer())
        monkeypatch.setattr(network_collector, "poll_equipment", lambda *a, **k: None)
        monkeypatch.setattr(network_collector, "send_collector_heartbeat", lambda *a, **k: None)

        config = fake_config(collector_role="secondaire", peer_heartbeat_url="http://primaire:8091/heartbeat",
                              failover_cycles_toleres=2, interval_seconds=0)
        network_collector.run(config, [fake_equipment()], {})

        # Une seule bascule, donc un seul demarrage du recepteur de traps.
        assert appels_trap == [1]

    def test_recepteur_de_traps_desactive_n_est_jamais_demarre(self, monkeypatch):
        monkeypatch.setattr(network_collector, "GracefulShutdown", lambda: _ArreterApresNCycles(1))
        monkeypatch.setattr(network_collector, "start_heartbeat_server",
                             lambda *a, **k: FakeHeartbeatServer())
        appels_trap = []
        monkeypatch.setattr(network_collector, "start_trap_receiver_thread",
                             lambda *a, **k: appels_trap.append(1))
        monkeypatch.setattr(network_collector, "poll_equipment", lambda *a, **k: None)
        monkeypatch.setattr(network_collector, "send_collector_heartbeat", lambda *a, **k: None)

        config = fake_config(collector_role="primaire", interval_seconds=0, trap_enabled=False)
        network_collector.run(config, [fake_equipment()], {})

        assert appels_trap == []


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
