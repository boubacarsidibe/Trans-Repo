import { render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it } from "vitest";
import { AuthProvider } from "./AuthContext";
import { ProtectedRoute } from "./ProtectedRoute";

function renderRoute(session?: { token: string; user: unknown }) {
	if (session) {
		localStorage.setItem("monitoring.token", session.token);
		localStorage.setItem("monitoring.user", JSON.stringify(session.user));
	}

	return render(
		<AuthProvider>
			<MemoryRouter initialEntries={["/prive"]}>
				<Routes>
					<Route path="/login" element={<p>Page de connexion</p>} />
					<Route
						path="/prive"
						element={
							<ProtectedRoute>
								<p>Contenu protégé</p>
							</ProtectedRoute>
						}
					/>
				</Routes>
			</MemoryRouter>
		</AuthProvider>,
	);
}

describe("ProtectedRoute", () => {
	beforeEach(() => {
		localStorage.clear();
	});

	it("redirige vers /login si non authentifié", () => {
		renderRoute();

		expect(screen.getByText("Page de connexion")).toBeInTheDocument();
		expect(screen.queryByText("Contenu protégé")).not.toBeInTheDocument();
	});

	it("affiche le contenu si authentifié", () => {
		renderRoute({ token: "jeton", user: { id: 1, username: "bob", email: "bob@ept.sn", role: "ADMINISTRATEUR" } });

		expect(screen.getByText("Contenu protégé")).toBeInTheDocument();
		expect(screen.queryByText("Page de connexion")).not.toBeInTheDocument();
	});
});
