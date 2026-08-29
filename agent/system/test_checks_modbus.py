"""Validation de checks.read_modbus_register contre un simulateur Modbus TCP local."""

import threading
import time

import pytest

pytest.importorskip("pymodbus")

from pymodbus.datastore import ModbusSequentialDataBlock, ModbusServerContext, ModbusSlaveContext
from pymodbus.server import StartTcpServer

from checks import read_modbus_register


def _demarrer_simulateur(port: int, holding: list[int], input_regs: list[int]) -> None:
    store = ModbusSlaveContext(
        hr=ModbusSequentialDataBlock(0, holding),
        ir=ModbusSequentialDataBlock(0, input_regs),
        di=ModbusSequentialDataBlock(0, [0] * 8),
        co=ModbusSequentialDataBlock(0, [0] * 8),
        # zero_mode=True : l'adresse demandee par le client correspond directement
        # a l'index du registre simule (comme sur un automate reel), sans le
        # decalage de +1 que pymodbus applique par defaut sur ses ModbusSlaveContext.
        zero_mode=True,
    )
    context = ModbusServerContext(slaves=store, single=True)
    thread = threading.Thread(
        target=StartTcpServer, kwargs={"context": context, "address": ("127.0.0.1", port)}, daemon=True)
    thread.start()
    time.sleep(0.3)


@pytest.fixture(scope="module")
def simulateur_modbus():
    port = 15502
    # holding[0]=1234, holding[5]=65535 (plafond uint16) ; input[0]=4321
    _demarrer_simulateur(port, holding=[1234, 0, 0, 0, 0, 65535], input_regs=[4321])
    return "127.0.0.1", port


def test_lecture_registre_holding(simulateur_modbus):
    host, port = simulateur_modbus
    assert read_modbus_register(host, port, 1, 0, "holding") == 1234.0


def test_lecture_registre_input(simulateur_modbus):
    host, port = simulateur_modbus
    assert read_modbus_register(host, port, 1, 0, "input") == 4321.0


def test_lecture_registre_valeur_maximale_uint16(simulateur_modbus):
    host, port = simulateur_modbus
    assert read_modbus_register(host, port, 1, 5, "holding") == 65535.0


def test_adresse_hors_plage_renvoie_none(simulateur_modbus):
    host, port = simulateur_modbus
    assert read_modbus_register(host, port, 1, 999, "holding") is None


def test_port_injoignable_renvoie_none_sans_lever():
    # Rien n'ecoute sur ce port : connexion refusee immediatement, pas de crash.
    assert read_modbus_register("127.0.0.1", 15503, 1, 0, "holding") is None


def test_host_vide_ne_tente_aucune_connexion():
    assert read_modbus_register("", 502, 1, 0, "holding") is None
