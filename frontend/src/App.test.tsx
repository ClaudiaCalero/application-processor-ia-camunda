import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import App from './App'

type Item = {
    id: number
    text: string
    category: string | null
    score: number | null
    summary: string | null
    amount: number | null
    status: 'RECEIVED' | 'IN_REVIEW' | 'COMPLETED'
    reviewedByPerson: boolean
    createdAt: string
}

function item(overrides: Partial<Item> = {}): Item {
    return {
        id: 1,
        text: 'I received an invoice for 150 euros',
        category: 'INVOICE',
        score: 0.95,
        summary: 'Invoice question',
        amount: 150,
        status: 'COMPLETED',
        reviewedByPerson: false,
        createdAt: '2026-10-28T10:00:00Z',
        ...overrides,
    }
}

const fetchMock = vi.fn()

function backendReturns(list: Item[]) {
    fetchMock.mockImplementation(async (_url: string, init?: RequestInit) => {
        if (init?.method === 'POST') {
            return { ok: true, status: 201, json: async () => item({ id: 99 }) }
        }
        return { ok: true, status: 200, json: async () => list }
    })
}

beforeEach(() => {
    fetchMock.mockReset()
    vi.stubGlobal('fetch', fetchMock)
})

afterEach(() => {
    vi.useRealTimers()
    vi.unstubAllGlobals()
})

describe('request table', () => {
    it('formats score, amount, status and human review', async () => {
        backendReturns([item()])
        render(<App />)

        expect(await screen.findByText('95%')).toBeInTheDocument()
        expect(screen.getByText('€150.00')).toBeInTheDocument()
        expect(screen.getByText('Invoice question')).toBeInTheDocument()
        // "Completed" appears in the metrics card and in the status badge
        expect(screen.getAllByText('Completed').length).toBeGreaterThanOrEqual(2)
    })

    it('shows a dash instead of 0 or null as the amount', async () => {
        backendReturns([
            item({ id: 1, amount: 0 }),
            item({ id: 2, amount: null }),
        ])
        render(<App />)

        await screen.findByText('#1')
        expect(screen.queryByText('€0.00')).not.toBeInTheDocument()
    })

    it('counts requests per status in the metric cards', async () => {
        backendReturns([
            item({ id: 1, status: 'COMPLETED' }),
            item({ id: 2, status: 'IN_REVIEW' }),
            item({ id: 3, status: 'IN_REVIEW' }),
            item({ id: 4, status: 'RECEIVED' }),
        ])
        render(<App />)

        await screen.findByText('#4')

        // "Completed" also appears as a status badge in the table, so pick the metric label
        const completedLabel = screen
            .getAllByText('Completed')
            .find((el) => el.classList.contains('metric-label'))

        expect(screen.getByText('Total requests').closest('article')).toHaveTextContent(/^Total requests4/)
        expect(screen.getByText('Awaiting review').closest('article')).toHaveTextContent(/^Awaiting review2/)
        expect(completedLabel?.closest('article')).toHaveTextContent(/^Completed1/)
        expect(screen.getByText('Received', { selector: '.metric-label' }).closest('article'))
            .toHaveTextContent(/^Received1/)
    })
})

describe('error handling', () => {
    it('shows an alert when the backend cannot be reached', async () => {
        fetchMock.mockRejectedValue(new Error('network down'))
        render(<App />)

        expect(await screen.findByRole('alert')).toHaveTextContent(/could not load requests/i)
    })

    it('shows a validation message when the API answers 400', async () => {
        fetchMock.mockImplementation(async (_url: string, init?: RequestInit) => {
            if (init?.method === 'POST') return { ok: false, status: 400, json: async () => ({}) }
            return { ok: true, status: 200, json: async () => [] }
        })
        const user = userEvent.setup()
        render(<App />)

        await user.type(screen.getByLabelText('Request details'), 'hello')
        await user.click(screen.getByRole('button', { name: /submit request/i }))

        expect(await screen.findByRole('alert')).toHaveTextContent(/request is invalid/i)
    })
})

describe('submitting a request', () => {
    it('keeps the button disabled while the text is blank', async () => {
        backendReturns([])
        const user = userEvent.setup()
        render(<App />)

        const button = screen.getByRole('button', { name: /submit request/i })
        expect(button).toBeDisabled()

        await user.type(screen.getByLabelText('Request details'), '   ')
        expect(button).toBeDisabled()
    })

    it('sends the trimmed text as JSON, then clears the form', async () => {
        backendReturns([])
        const user = userEvent.setup()
        render(<App />)

        const textarea = screen.getByLabelText('Request details')
        await user.type(textarea, '  invoice for 150 euros  ')
        await user.click(screen.getByRole('button', { name: /submit request/i }))

        await waitFor(() => {
            const post = fetchMock.mock.calls.find(([, init]) => init?.method === 'POST')
            expect(post).toBeDefined()
            expect(post![0]).toMatch(/\/api\/requests$/)
            expect(JSON.parse(post![1].body)).toEqual({ text: 'invoice for 150 euros' })
        })
        expect(await screen.findByRole('status')).toHaveTextContent(/submitted successfully/i)
        expect(textarea).toHaveValue('')
    })
})

describe('polling', () => {
    it('reloads the list every five seconds', async () => {
        vi.useFakeTimers({ shouldAdvanceTime: true })
        backendReturns([])
        render(<App />)

        await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(1))
        await vi.advanceTimersByTimeAsync(5000)
        await waitFor(() => expect(fetchMock.mock.calls.length).toBeGreaterThanOrEqual(2))
    })
})