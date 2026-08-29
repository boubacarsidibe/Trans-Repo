import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it } from "vitest";
import { AuthProvider } from "./AuthContext";
import { RequireRole } from "./RequireRole";

describe("RequireRole", () => {
	beforeEach(() => {
		localStorage.clear();
	});

	it("affiche un accès refusé si le rôle ne correspond pas", () => {
		localStorage.setItem("monitoring.token", "jeton");
		localStorage.setItem(
			"monitoring.user",
			JSON.stringify({ id: 1, username: "bob", email: "bob@ept.sn", role: "OBSERVATEUR" }),
		);

		render(
			<AuthProvider>
				<RequireRole autorise={false} requis="ADMINISTRATEUR">
					<p>Contenu réservé</p>
				</RequireRole>
			</AuthProvider>,
		);

		expect(screen.getByText("Cet écran est réservé aux administrateurs")).toBeInTheDocument();
		expect(screen.getByText("bob@ept.sn")).toBeInTheDocument();
		expect(screen.queryByText("Contenu réservé")).not.toBeInTheDocument();
	});

	it("affiche le contenu si le rôle correspond", () => {
		render(
			<AuthProvider>
				<RequireRole autorise={true} requis="ADMINISTRATEUR">
					<p>Contenu réservé</p>
				</RequireRole>
			</AuthProvider>,
		);

		expect(screen.getByText("Contenu réservé")).toBeInTheDocument();
	});
});
