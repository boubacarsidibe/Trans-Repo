import { NavLink, Outlet, useNavigate } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";

export function AppLayout() {
	const { user, logout } = useAuth();
	const navigate = useNavigate();

	function handleLogout() {
		logout();
		navigate("/login");
	}

	return (
		<div className="app-shell">
			<header className="app-header">
				<span className="app-title">Monitoring EPT</span>
				<nav>
					<NavLink to="/equipements">Équipements</NavLink>
					<NavLink to="/alertes">Alertes</NavLink>
				</nav>
				<div className="app-user">
					<span>
						{user?.username} ({user?.role})
					</span>
					<button onClick={handleLogout}>Déconnexion</button>
				</div>
			</header>
			<main className="app-content">
				<Outlet />
			</main>
		</div>
	);
}
