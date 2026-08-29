import { useAuth } from "../auth/AuthContext";
import { RequireRole } from "../auth/RequireRole";
import { ROLE, estAdministrateur } from "../supervision/libelles";
import "../styles/netvision.css";

const NAV_LINKS = [
	{ icon: "/netvision/icon-nav-dashboard.svg", label: "Dashboard", actif: true },
	{ icon: "/netvision/icon-nav-monitoring.svg", label: "Monitoring", actif: false },
	{ icon: "/netvision/icon-nav-inventory.svg", label: "Inventory", actif: false },
	{ icon: "/netvision/icon-nav-alerts.svg", label: "Alerts", actif: false },
	{ icon: "/netvision/icon-nav-admin.svg", label: "Administration", actif: false },
];

const SPARKLINE_HEIGHTS = [19.19, 28.8, 26.39, 38.39, 36, 43.19, 40.8];

const CRITICAL_EVENTS = [
	{
		heure: "12:45:02",
		tag: "SRE-01",
		titre: "BGP Flap: DC-EAST-04",
		detail: "Core router dropped peering",
		icon: "/netvision/icon-alert-critical.svg",
		niveau: "critical" as const,
	},
	{
		heure: "12:30:15",
		tag: null,
		titre: "Latency Spike detected",
		detail: "US-WEST Availability Zone",
		icon: "/netvision/icon-alert-warning.svg",
		niveau: "normal" as const,
	},
	{
		heure: "11:58:42",
		tag: null,
		titre: "Auto-scaling event completed",
		detail: "Cluster: K8S-PROD-02",
		icon: "/netvision/icon-alert-info.svg",
		niveau: "muted" as const,
	},
];

const TOP_TALKERS = [
	{
		source: "edge-proxy-01",
		ip: "10.0.4.12",
		destination: "External Transit",
		throughput: "852",
		unite: "Mbps",
		statut: "Active" as const,
	},
	{
		source: "db-cluster-node-3",
		ip: "10.0.8.44",
		destination: "App Layer",
		throughput: "421",
		unite: "Mbps",
		statut: "Active" as const,
	},
	{
		source: "backup-svc-04",
		ip: "172.16.0.5",
		destination: "Cold Storage",
		throughput: "2.1",
		unite: "Gbps",
		statut: "Warning" as const,
	},
	{
		source: "internal-load-balancer",
		ip: "10.0.1.1",
		destination: "Global VIP",
		throughput: "154",
		unite: "Mbps",
		statut: "Active" as const,
	},
];

/**
 * Ecran de style/demo (issue #11), pas un outil de supervision reel — garde
 * de role au meme titre que les autres pages reservees aux administrateurs.
 */
export function NetVisionDashboardPage() {
	const { user } = useAuth();

	return (
		<RequireRole
			autorise={estAdministrateur(user?.role)}
			requis={user ? ROLE[user.role as keyof typeof ROLE] : "inconnu"}
		>
			<Contenu />
		</RequireRole>
	);
}

function Contenu() {
	return (
		<div className="netvision">
			<aside className="netvision-sidebar">
				<div>
					<div className="netvision-brand">
						<h1>NetVision</h1>
						<p>Enterprise Observability</p>
					</div>
					<nav className="netvision-nav">
						{NAV_LINKS.map((lien) => (
							<a
								key={lien.label}
								href="#"
								className={lien.actif ? "netvision-nav-link actif" : "netvision-nav-link"}
							>
								<img src={lien.icon} alt="" />
								<span>{lien.label}</span>
							</a>
						))}
					</nav>
				</div>
				<div className="netvision-nav-footer">
					<a href="#" className="netvision-nav-link">
						<img src="/netvision/icon-nav-support.svg" alt="" />
						<span>Support</span>
					</a>
					<a href="#" className="netvision-nav-link">
						<img src="/netvision/icon-nav-logs.svg" alt="" />
						<span>Logs</span>
					</a>
				</div>
			</aside>

			<div className="netvision-main">
				<header className="netvision-topbar">
					<div style={{ display: "flex", flex: 1, alignItems: "center", maxWidth: 800 }}>
						<div className="netvision-search">
							<img src="/netvision/icon-search.svg" alt="" />
							<input type="text" placeholder="Search telemetry, nodes, or alerts..." />
						</div>
						<nav className="netvision-tabs">
							<a href="#" className="netvision-tab actif">
								Metrics
							</a>
							<a href="#" className="netvision-tab">
								Topology
							</a>
							<a href="#" className="netvision-tab">
								Discovery
							</a>
						</nav>
					</div>
					<div className="netvision-topbar-actions">
						<button type="button" className="netvision-icon-button" aria-label="Notifications">
							<img src="/netvision/icon-bell.svg" alt="" />
						</button>
						<button type="button" className="netvision-icon-button" aria-label="Settings">
							<img src="/netvision/icon-settings.svg" alt="" />
						</button>
						<div className="netvision-avatar">
							<img src="/netvision/avatar.png" alt="Avatar utilisateur" />
						</div>
					</div>
				</header>

				<div className="netvision-canvas">
					<div className="netvision-grid" style={{ gridTemplateRows: "186px" }}>
						<div className="netvision-health-card">
							<div className="netvision-health-glow" />
							<div className="netvision-health-label">SYSTEM INTEGRITY</div>
							<div className="netvision-health-value">98.4%</div>
							<div className="netvision-sparkline-row">
								<div className="netvision-sparkline">
									{SPARKLINE_HEIGHTS.map((hauteur, index) => (
										<span
											key={index}
											className={index >= 5 ? "forte" : ""}
											style={{ height: `${hauteur}px` }}
										/>
									))}
								</div>
								<div className="netvision-sparkline-delta">
									<img src="/netvision/icon-trend-up.svg" alt="" />
									<span>+0.2%</span>
								</div>
							</div>
						</div>

						<div className="netvision-quick-stats">
							<div className="netvision-stat-card netvision-stat-card--online">
								<div className="netvision-stat-label">Online</div>
								<div className="netvision-stat-value">
									<span className="netvision-stat-number">1,242</span>
									<span className="netvision-stat-unit">Nodes</span>
								</div>
							</div>
							<div className="netvision-stat-card netvision-stat-card--offline">
								<div className="netvision-stat-label">Offline</div>
								<div className="netvision-stat-value">
									<span className="netvision-stat-number">12</span>
									<span className="netvision-stat-unit">Nodes</span>
								</div>
							</div>
							<div className="netvision-stat-card netvision-stat-card--critical">
								<div className="netvision-stat-label">Critical Alerts</div>
								<div className="netvision-stat-value">
									<span className="netvision-stat-number">5</span>
									<span className="netvision-stat-unit">Events</span>
								</div>
							</div>
							<div className="netvision-stat-card netvision-stat-card--warning">
								<div className="netvision-stat-label">Warnings</div>
								<div className="netvision-stat-value">
									<span className="netvision-stat-number">18</span>
									<span className="netvision-stat-unit">Events</span>
								</div>
							</div>
						</div>
					</div>

					<div className="netvision-grid" style={{ gridTemplateRows: "463px" }}>
						<div className="netvision-panel netvision-performance">
							<div className="netvision-panel-header">
								<div className="netvision-panel-title">
									<img src="/netvision/icon-chart.svg" alt="" />
									<h3>Global Performance Matrix</h3>
								</div>
								<div className="netvision-performance-legend">
									<div className="netvision-legend-item">
										<span className="netvision-legend-dot" style={{ background: "#a8c8ff" }} />
										<span>Network</span>
									</div>
									<div className="netvision-legend-item">
										<span className="netvision-legend-dot" style={{ background: "#a3c9ff" }} />
										<span>CPU</span>
									</div>
									<div className="netvision-range-select">
										<img src="/netvision/icon-calendar.svg" alt="" />
										<span>Last 6 Hours</span>
									</div>
								</div>
							</div>
							<div className="netvision-chart-area">
								<img src="/netvision/chart-visualization.svg" alt="Real-time performance chart" />
							</div>
						</div>

						<div className="netvision-panel netvision-events">
							<div className="netvision-panel-header">
								<h3 style={{ margin: 0, fontSize: 20, fontWeight: 600, lineHeight: "28px" }}>
									Critical Events
								</h3>
							</div>
							<div className="netvision-events-list">
								{CRITICAL_EVENTS.map((evt) => (
									<div
										key={evt.heure}
										className={
											evt.niveau === "critical"
												? "netvision-event netvision-event--critical"
												: evt.niveau === "muted"
													? "netvision-event netvision-event--muted"
													: "netvision-event"
										}
									>
										<div className="netvision-event-rail">
											<img src={evt.icon} alt="" />
											<div className="netvision-event-rail-line" />
										</div>
										<div className="netvision-event-body">
											<div className="netvision-event-time-row">
												<span className="netvision-event-time">{evt.heure}</span>
												{evt.tag && <span className="netvision-event-badge">{evt.tag}</span>}
											</div>
											<div className="netvision-event-title">{evt.titre}</div>
											<div className="netvision-event-meta">{evt.detail}</div>
										</div>
									</div>
								))}
							</div>
							<div className="netvision-events-footer">
								<button type="button" className="netvision-button-outline">
									View Full Log History
								</button>
							</div>
						</div>
					</div>

					<div className="netvision-grid" style={{ gridTemplateRows: "358px" }}>
						<div className="netvision-panel netvision-topology">
							<div className="netvision-topology-header">
								<h3>Network Topology</h3>
								<img src="/netvision/icon-expand.svg" alt="" />
							</div>
							<div className="netvision-topology-map">
								<div className="netvision-topology-glow" />
								<div className="netvision-topology-nodes">
									<div className="netvision-topology-diagram">
										<img src="/netvision/topology-lines.svg" alt="" />
										<div className="netvision-topology-node netvision-topology-node--top">
											<img src="/netvision/icon-cloud.svg" alt="" />
										</div>
										<div className="netvision-topology-node netvision-topology-node--bl">
											<img src="/netvision/icon-router.svg" alt="" />
										</div>
										<div className="netvision-topology-node netvision-topology-node--br">
											<img src="/netvision/icon-server.svg" alt="" />
										</div>
									</div>
								</div>
								<div className="netvision-topology-badge">3 Data Centers</div>
							</div>
						</div>

						<div className="netvision-panel netvision-talkers">
							<div className="netvision-panel-header netvision-panel-header--tinted">
								<h3 style={{ margin: 0, fontSize: 20, fontWeight: 600, lineHeight: "28px" }}>
									Top Talkers
								</h3>
								<span
									style={{
										fontSize: 10,
										fontWeight: 700,
										letterSpacing: 0.5,
										color: "var(--nv-text-dim)",
									}}
								>
									REAL-TIME TRAFFIC
								</span>
							</div>
							<table className="netvision-table">
								<thead>
									<tr>
										<th>Source Node</th>
										<th>Destination</th>
										<th>Throughput</th>
										<th>Status</th>
									</tr>
								</thead>
								<tbody>
									{TOP_TALKERS.map((ligne) => (
										<tr key={ligne.source}>
											<td>
												<span className="netvision-node-name">
													{ligne.source}
													<span className="netvision-node-ip">{ligne.ip}</span>
												</span>
											</td>
											<td>{ligne.destination}</td>
											<td>
												<span className="netvision-throughput">
													<span>{ligne.throughput}</span>
													<span>{ligne.unite}</span>
												</span>
											</td>
											<td>
												<span className="netvision-status">
													<span
														className={
															ligne.statut === "Active"
																? "netvision-status-dot netvision-status-dot--active"
																: "netvision-status-dot netvision-status-dot--warning"
														}
													/>
													{ligne.statut}
												</span>
											</td>
										</tr>
									))}
								</tbody>
							</table>
						</div>
					</div>
				</div>
			</div>

			<div className="netvision-noise" aria-hidden />
		</div>
	);
}
