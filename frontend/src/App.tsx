import { BrowserRouter, Route, Routes } from "react-router-dom";
import { AuthProvider } from "./auth/AuthContext";
import { ProtectedRoute } from "./auth/ProtectedRoute";
import { AppLayout } from "./layout/AppLayout";
import { SupervisionProvider } from "./supervision/SupervisionContext";
import { AlertesPage } from "./pages/AlertesPage";
import { CartographiePage } from "./pages/CartographiePage";
import { EquipementFormPage } from "./pages/EquipementFormPage";
import { EquipementsPage } from "./pages/EquipementsPage";
import { IntrouvablePage } from "./pages/IntrouvablePage";
import { JournalPage } from "./pages/JournalPage";
import { LoginPage } from "./pages/LoginPage";
import { NetVisionDashboardPage } from "./pages/NetVisionDashboardPage";
import { RapportsPage } from "./pages/RapportsPage";
import { SeuilsPage } from "./pages/SeuilsPage";
import { SynoptiquePage } from "./pages/SynoptiquePage";
import { UtilisateursPage } from "./pages/UtilisateursPage";

function App() {
	return (
		<AuthProvider>
			<BrowserRouter>
				<Routes>
					<Route path="/login" element={<LoginPage />} />
					<Route
						path="/netvision-preview"
						element={
							<ProtectedRoute>
								<NetVisionDashboardPage />
							</ProtectedRoute>
						}
					/>
					<Route
						element={
							<ProtectedRoute>
								<SupervisionProvider>
									<AppLayout />
								</SupervisionProvider>
							</ProtectedRoute>
						}
					>
						<Route path="/" element={<SynoptiquePage />} />
						<Route path="/equipements" element={<EquipementsPage />} />
						<Route path="/equipements/nouveau" element={<EquipementFormPage />} />
						<Route path="/equipements/:id/modifier" element={<EquipementFormPage />} />
						<Route path="/cartographie" element={<CartographiePage />} />
						<Route path="/alertes" element={<AlertesPage />} />
						<Route path="/seuils" element={<SeuilsPage />} />
						<Route path="/rapports" element={<RapportsPage />} />
						<Route path="/journal" element={<JournalPage />} />
						<Route path="/utilisateurs" element={<UtilisateursPage />} />
						<Route path="*" element={<IntrouvablePage />} />
					</Route>
				</Routes>
			</BrowserRouter>
		</AuthProvider>
	);
}

export default App;
