const proprietes = {
	width: 16,
	height: 16,
	viewBox: "0 0 24 24",
	fill: "none",
	stroke: "currentColor",
	strokeWidth: 1.8,
	strokeLinecap: "round" as const,
	strokeLinejoin: "round" as const,
	"aria-hidden": true,
	focusable: false,
};

export function IconePersonne() {
	return (
		<svg {...proprietes}>
			<circle cx="12" cy="8" r="3.4" />
			<path d="M5 20c0-3.6 3.1-6 7-6s7 2.4 7 6" />
		</svg>
	);
}

export function IconeCadenas() {
	return (
		<svg {...proprietes}>
			<rect x="5" y="11" width="14" height="9" rx="2" />
			<path d="M8 11V8a4 4 0 0 1 8 0v3" />
		</svg>
	);
}

export function IconeFleche() {
	return (
		<svg {...proprietes} width={15} height={15} strokeWidth={2.2}>
			<path d="M5 12h14" />
			<path d="M13 6l6 6-6 6" />
		</svg>
	);
}

/** L'insigne du poste : un nœud central relié à quatre satellites. */
export function IconeReseau() {
	return (
		<svg width={26} height={26} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.6} aria-hidden focusable={false}>
			<circle cx="12" cy="12" r="2.6" />
			<circle cx="4" cy="5" r="1.8" />
			<circle cx="20" cy="5" r="1.8" />
			<circle cx="4" cy="19" r="1.8" />
			<circle cx="20" cy="19" r="1.8" />
			<path d="M9.9 10.1 5.4 6.3M14.1 10.1l4.5-3.8M9.9 13.9l-4.5 3.8M14.1 13.9l4.5 3.8" />
		</svg>
	);
}
