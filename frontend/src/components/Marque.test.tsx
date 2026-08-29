import { render } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { Marque } from './Marque'

describe('Marque', () => {
	it('rend un svg avec la taille demandée', () => {
		const { container } = render(<Marque taille={24} />)
		const svg = container.querySelector('svg')
		expect(svg).toBeInTheDocument()
		expect(svg).toHaveAttribute('width', '24')
		expect(svg).toHaveAttribute('height', '24')
	})
})
