import { act, renderHook } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { AuthResponse } from "../types/api";
import { getToken } from "../api/client";

vi.mock("../api/endpoints", () => ({
	login: vi.fn(),
}));

import { login as loginRequest } from "../api/endpoints";
import { AuthProvider, useAuth } from "./AuthContext";

describe("AuthContext", () => {
	beforeEach(() => {
		localStorage.clear();
		vi.clearAllMocks();
	});

	it("n'est pas authentifié sans session stockée", () => {
		const { result } = renderHook(() => useAuth(), { wrapper: AuthProvider });

		expect(result.current.isAuthenticated).toBe(false);
		expect(result.current.user).toBeNull();
	});

	it("restaure la session depuis le stockage local au montage", () => {
		localStorage.setItem("monitoring.token", "un-jeton");
		localStorage.setItem(
			"monitoring.user",
			JSON.stringify({ id: 1, username: "bob", email: "bob@ept.sn", role: "ADMINISTRATEUR" }),
		);

		const { result } = renderHook(() => useAuth(), { wrapper: AuthProvider });

		expect(result.current.isAuthenticated).toBe(true);
		expect(result.current.user?.email).toBe("bob@ept.sn");
	});

	it("login enregistre la session et authentifie", async () => {
		const auth: AuthResponse = {
			id: 2,
			username: "alice",
			email: "alice@ept.sn",
			role: "TECHNICIEN",
			token: "jeton",
			refreshToken: "refresh",
			tokenType: "Bearer",
		};
		vi.mocked(loginRequest).mockResolvedValue(auth);

		const { result } = renderHook(() => useAuth(), { wrapper: AuthProvider });
		await act(async () => {
			await result.current.login("alice@ept.sn", "secret");
		});

		expect(result.current.isAuthenticated).toBe(true);
		expect(result.current.user?.role).toBe("TECHNICIEN");
		expect(getToken()).toBe("jeton");
	});

	it("logout efface la session", () => {
		localStorage.setItem("monitoring.token", "un-jeton");
		localStorage.setItem(
			"monitoring.user",
			JSON.stringify({ id: 1, username: "bob", email: "bob@ept.sn", role: "ADMINISTRATEUR" }),
		);

		const { result } = renderHook(() => useAuth(), { wrapper: AuthProvider });
		act(() => result.current.logout());

		expect(result.current.isAuthenticated).toBe(false);
		expect(getToken()).toBeNull();
	});
});
