/** La marque : un champ de quatre lampes, dont une allumée. */
export function Marque({ taille = 16 }: { taille?: number }) {
	return (
		<svg width={taille} height={taille} viewBox="0 0 32 32" aria-hidden="true" focusable="false">
			<circle cx="10" cy="10" r="4.2" fill="currentColor" />
			<circle cx="22" cy="10" r="4.2" fill="currentColor" />
			<circle cx="10" cy="22" r="4.2" fill="currentColor" />
			<circle cx="22" cy="22" r="4.2" fill="#e0a63b" />
		</svg>
	);
}
