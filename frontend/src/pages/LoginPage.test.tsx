import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { LoginPage } from "./LoginPage";

const mockLogin = vi.fn();
const mockNavigate = vi.fn();

vi.mock("../auth/AuthContext", () => ({
	useAuth: () => ({ login: mockLogin }),
}));

vi.mock("react-router-dom", async (importOriginal) => {
	const reel = await importOriginal<typeof import("react-router-dom")>();
	return { ...reel, useNavigate: () => mockNavigate };
});

async function remplirEtSoumettre(email: string, motDePasse: string) {
	await userEvent.type(screen.getByLabelText("Adresse e-mail"), email);
	await userEvent.type(screen.getByLabelText("Mot de passe"), motDePasse);
	await userEvent.click(screen.getByRole("button", { name: /continuer/i }));
}

describe("LoginPage", () => {
	beforeEach(() => {
		mockLogin.mockReset();
		mockNavigate.mockReset();
	});

	it("connecte l'utilisateur et redirige vers l'accueil quand les identifiants sont valides", async () => {
		mockLogin.mockResolvedValueOnce(undefined);
		render(<LoginPage />);

		await remplirEtSoumettre("amina.diop@ept.sn", "motdepasse");

		expect(mockLogin).toHaveBeenCalledWith("amina.diop@ept.sn", "motdepasse");
		await waitFor(() => expect(mockNavigate).toHaveBeenCalledWith("/"));
	});

	it("affiche un message dédié quand le serveur refuse les identifiants", async () => {
		mockLogin.mockRejectedValueOnce({ response: { status: 401 } });
		render(<LoginPage />);

		await remplirEtSoumettre("amina.diop@ept.sn", "mauvais-mot-de-passe");

		expect(await screen.findByText("Adresse e-mail ou mot de passe incorrect.")).toBeInTheDocument();
		expect(mockNavigate).not.toHaveBeenCalled();
	});

	it("affiche un message dédié quand le serveur ne répond pas du tout", async () => {
		mockLogin.mockRejectedValueOnce(new Error("Network Error"));
		render(<LoginPage />);

		await remplirEtSoumettre("amina.diop@ept.sn", "motdepasse");

		expect(await screen.findByText(/n'a pas répondu/)).toBeInTheDocument();
		expect(mockNavigate).not.toHaveBeenCalled();
	});
});
