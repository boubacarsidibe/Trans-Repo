import socket

import checks


class TestParseTcpTargets:
    def test_analyse_host_port(self):
        assert checks.parse_tcp_targets("10.0.0.5:22") == [("10.0.0.5", 22)]

    def test_port_seul_suppose_localhost(self):
        assert checks.parse_tcp_targets("8080") == [("localhost", 8080)]

    def test_plusieurs_cibles_separees_par_des_virgules(self):
        assert checks.parse_tcp_targets("5432,localhost:8080,10.0.0.5:22") == [
            ("localhost", 5432),
            ("localhost", 8080),
            ("10.0.0.5", 22),
        ]

    def test_chaine_vide_renvoie_aucune_cible(self):
        assert checks.parse_tcp_targets("") == []

    def test_entree_invalide_ignoree(self):
        assert checks.parse_tcp_targets("localhost:abc, 10.0.0.5:22") == [("10.0.0.5", 22)]


class TestCountTcpServicesDown:
    def test_aucune_cible_renvoie_none(self):
        assert checks.count_tcp_services_down([], timeout=1) is None

    def test_compte_les_services_injoignables(self, monkeypatch):
        def fake_create_connection(address, timeout):
            host, _ = address
            if "down" in host:
                raise OSError("connection refused")
            return DummySocket()

        monkeypatch.setattr(socket, "create_connection", fake_create_connection)

        down = checks.count_tcp_services_down(
            [("up.example", 80), ("down.example", 80), ("also-down.example", 22)], timeout=1
        )
        assert down == 2


class DummySocket:
    def __enter__(self):
        return self

    def __exit__(self, *exc_info):
        return False


class TestCheckDnsLatency:
    def test_hostname_vide_renvoie_none(self):
        assert checks.check_dns_latency("", timeout=1) is None

    def test_resolution_reussie_renvoie_une_latence_positive(self, monkeypatch):
        monkeypatch.setattr(socket, "getaddrinfo", lambda *a, **k: [("fake",)])
        latence = checks.check_dns_latency("ept.sn", timeout=1)
        assert latence is not None
        assert latence >= 0

    def test_echec_de_resolution_renvoie_none(self, monkeypatch):
        def fake_getaddrinfo(*a, **k):
            raise OSError("resolution failed")

        monkeypatch.setattr(socket, "getaddrinfo", fake_getaddrinfo)
        assert checks.check_dns_latency("inconnu.invalid", timeout=1) is None


class TestEtatFichier:
    def test_round_trip_sauvegarde_et_lecture(self, tmp_path):
        chemin = tmp_path / "state.json"
        checks.save_state(str(chemin), {"log_offset": 42})
        assert checks.load_state(str(chemin)) == {"log_offset": 42}

    def test_fichier_absent_renvoie_dictionnaire_vide(self, tmp_path):
        assert checks.load_state(str(tmp_path / "absent.json")) == {}


class TestCheckWatchedFile:
    def test_chemin_vide_renvoie_none_none(self):
        assert checks.check_watched_file("") == (None, None)

    def test_fichier_absent_renvoie_zero_et_none(self, tmp_path):
        assert checks.check_watched_file(str(tmp_path / "absent.txt")) == (0, None)

    def test_fichier_present_renvoie_un_et_sa_taille(self, tmp_path):
        fichier = tmp_path / "present.txt"
        fichier.write_text("bonjour")
        existe, taille = checks.check_watched_file(str(fichier))
        assert existe == 1
        assert taille == len("bonjour")


class TestCheckLogFile:
    def test_chemin_vide_renvoie_none_none(self):
        assert checks.check_log_file("", None, {}) == (None, None)

    def test_premier_cycle_n_a_pas_d_historique(self, tmp_path):
        fichier = tmp_path / "app.log"
        fichier.write_text("ligne 1\nligne 2\n")
        etat: dict = {}

        total, correspondances = checks.check_log_file(str(fichier), None, etat)

        assert (total, correspondances) == (0, 0)
        assert etat["log_offset"] == fichier.stat().st_size

    def test_compte_les_nouvelles_lignes_depuis_le_dernier_cycle(self, tmp_path):
        fichier = tmp_path / "app.log"
        fichier.write_text("ligne 1\n")
        etat: dict = {}
        checks.check_log_file(str(fichier), None, etat)  # amorce l'etat

        with open(fichier, "a", encoding="utf-8") as f:
            f.write("ERROR boom\nligne normale\nERROR encore\n")

        total, correspondances = checks.check_log_file(str(fichier), r"ERROR", etat)

        assert total == 3
        assert correspondances == 2

    def test_troncature_du_fichier_repart_du_debut(self, tmp_path):
        fichier = tmp_path / "app.log"
        fichier.write_text("une ligne assez longue pour avancer l'offset\n")
        etat: dict = {}
        checks.check_log_file(str(fichier), None, etat)  # amorce l'etat sur un gros offset

        fichier.write_text("courte\n")  # remplace par un fichier plus petit (rotation)

        total, _ = checks.check_log_file(str(fichier), None, etat)

        assert total == 1

    def test_fichier_introuvable_renvoie_none_none(self, tmp_path):
        assert checks.check_log_file(str(tmp_path / "absent.log"), None, {}) == (None, None)


class TestReadSensors:
    def test_absence_de_capteurs_renvoie_none_none(self, monkeypatch):
        monkeypatch.delattr("psutil.sensors_temperatures", raising=False)
        monkeypatch.delattr("psutil.sensors_fans", raising=False)
        assert checks.read_sensors() == (None, None)

    def test_retient_la_temperature_maximale(self, monkeypatch):
        entree = type("Entree", (), {"current": None})

        def fausse_lecture(current):
            e = entree()
            e.current = current
            return e

        monkeypatch.setattr(
            "psutil.sensors_temperatures",
            lambda: {"coretemp": [fausse_lecture(45.0), fausse_lecture(62.5)]},
            raising=False,
        )
        monkeypatch.delattr("psutil.sensors_fans", raising=False)

        temperature, ventilateur = checks.read_sensors()

        assert temperature == 62.5
        assert ventilateur is None


class TestReadModbusRegister:
    def test_host_vide_renvoie_none_sans_tenter_de_connexion(self):
        assert checks.read_modbus_register("", 502, 1, 0, "holding") is None
