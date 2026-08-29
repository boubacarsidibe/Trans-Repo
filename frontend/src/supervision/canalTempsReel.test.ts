import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("../api/client", () => ({
	BASE_URL: "http://localhost:8080",
	getToken: vi.fn(),
}));

import { getToken } from "../api/client";
import { ouvrirCanaux } from "./canalTempsReel";

class FakeWebSocket {
	static instances: FakeWebSocket[] = [];
	url: string;
	onopen: (() => void) | null = null;
	onmessage: ((message: { data: string }) => void) | null = null;
	onclose: (() => void) | null = null;
	onerror: (() => void) | null = null;
	closed = false;

	constructor(url: string) {
		this.url = url;
		FakeWebSocket.instances.push(this);
	}

	close() {
		this.closed = true;
		this.onclose?.();
	}
}

describe("ouvrirCanaux", () => {
	beforeEach(() => {
		FakeWebSocket.instances = [];
		vi.stubGlobal("WebSocket", FakeWebSocket);
		vi.mocked(getToken).mockReturnValue("un-jeton");
		vi.useFakeTimers();
	});

	afterEach(() => {
		vi.useRealTimers();
		vi.unstubAllGlobals();
	});

	it("n'ouvre aucun canal et signale l'état inactif sans jeton", () => {
		vi.mocked(getToken).mockReturnValue(null);
		const surEtat = vi.fn();

		const arreter = ouvrirCanaux(vi.fn(), surEtat);

		expect(FakeWebSocket.instances).toHaveLength(0);
		expect(surEtat).toHaveBeenCalledWith(false);
		arreter();
	});

	it("ouvre les trois canaux et ne signale l'état actif qu'une fois tous connectés", () => {
		const surEtat = vi.fn();
		const arreter = ouvrirCanaux(vi.fn(), surEtat);

		expect(FakeWebSocket.instances).toHaveLength(3);
		expect(surEtat).not.toHaveBeenCalledWith(true);

		FakeWebSocket.instances.slice(0, 2).forEach((socket) => socket.onopen?.());
		expect(surEtat).not.toHaveBeenCalledWith(true);

		FakeWebSocket.instances[2].onopen?.();
		expect(surEtat).toHaveBeenLastCalledWith(true);

		arreter();
	});

	it("transmet un message reçu à surEvenement", () => {
		const surEvenement = vi.fn();
		const arreter = ouvrirCanaux(surEvenement, vi.fn());

		const evenement = { type: "metric_update", horodatage: "2026-08-29T00:00:00Z", payload: {} };
		FakeWebSocket.instances[0].onmessage?.({ data: JSON.stringify(evenement) });

		expect(surEvenement).toHaveBeenCalledWith(evenement);
		arreter();
	});

	it("ignore un message illisible sans casser le flux", () => {
		const surEvenement = vi.fn();
		const arreter = ouvrirCanaux(surEvenement, vi.fn());

		expect(() => FakeWebSocket.instances[0].onmessage?.({ data: "pas du json" })).not.toThrow();
		expect(surEvenement).not.toHaveBeenCalled();
		arreter();
	});

	it("se reconnecte après une coupure, avec un délai croissant", () => {
		const surEtat = vi.fn();
		const arreter = ouvrirCanaux(vi.fn(), surEtat);

		const premier = FakeWebSocket.instances[0];
		premier.onopen?.();
		premier.onclose?.();

		expect(surEtat).toHaveBeenLastCalledWith(false);
		expect(FakeWebSocket.instances).toHaveLength(3);

		vi.advanceTimersByTime(1_000);
		expect(FakeWebSocket.instances).toHaveLength(4);

		arreter();
	});

	it("arrête tous les canaux ouverts et n'en rouvre aucun après l'arrêt", () => {
		const surEtat = vi.fn();
		const arreter = ouvrirCanaux(vi.fn(), surEtat);

		FakeWebSocket.instances.forEach((socket) => socket.onopen?.());
		arreter();

		expect(surEtat).toHaveBeenLastCalledWith(false);
		FakeWebSocket.instances.forEach((socket) => expect(socket.closed).toBe(true));

		vi.advanceTimersByTime(60_000);
		expect(FakeWebSocket.instances).toHaveLength(3);
	});
});
