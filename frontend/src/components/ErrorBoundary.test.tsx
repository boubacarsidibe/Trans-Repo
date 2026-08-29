import { fireEvent, render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ErrorBoundary } from "./ErrorBoundary";

function Bombe(): never {
	throw new Error("Panne simulée");
}

describe("ErrorBoundary", () => {
	beforeEach(() => {
		// React logue aussi l'erreur du rendu qui a échoué : on ne teste pas la
		// console, juste le fait qu'elle ne fasse pas échouer le test.
		vi.spyOn(console, "error").mockImplementation(() => {});
	});

	it("affiche le contenu normalement en l'absence d'erreur", () => {
		render(
			<ErrorBoundary>
				<p>Contenu normal</p>
			</ErrorBoundary>,
		);

		expect(screen.getByText("Contenu normal")).toBeInTheDocument();
	});

	it("affiche un écran de repli quand un enfant lève une exception au rendu", () => {
		render(
			<ErrorBoundary>
				<Bombe />
			</ErrorBoundary>,
		);

		expect(screen.getByText("Ce poste a rencontré une erreur inattendue")).toBeInTheDocument();
		expect(screen.getByText("Panne simulée")).toBeInTheDocument();
	});

	it("recharge la page au clic sur le bouton de repli", () => {
		const reload = vi.fn();
		vi.stubGlobal("location", { ...window.location, reload });

		render(
			<ErrorBoundary>
				<Bombe />
			</ErrorBoundary>,
		);

		fireEvent.click(screen.getByRole("button", { name: "Recharger la page" }));

		expect(reload).toHaveBeenCalled();

		vi.unstubAllGlobals();
	});
});
