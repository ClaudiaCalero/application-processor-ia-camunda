import {useCallback, useEffect, useState} from "react";
import type { FormEvent } from 'react'
import './App.css'

type RequestStatus = 'RECEIVED' | 'IN_REVIEW' | 'COMPLETED'

interface Request {
  id: number
  text: string
  category: string | null
  score: number | null
  summary: string | null
  amount: number | null
  status: RequestStatus
  reviewedByPerson: boolean
  createdAt: string
}

const API_URL =
    import.meta.env.VITE_API_URL ?? 'http://localhost:8081'

function formatDate(value: string): string {
  return new Date(value).toLocaleDateString()
}
function formatAmount(amount: number | null): string {
  if (amount == null) return '-'

  return new Intl.NumberFormat('en-GB', {
    style: 'currency',
    currency:'EUR',
  }).format(amount)
}

function getStatusLabel(status: RequestStatus): string {
  switch (status) {
    case 'RECEIVED':
      return 'Received'
    case 'IN_REVIEW':
      return 'Under review'
    case 'COMPLETED':
      return 'Completed'
  }
}

function App() {
  const [text, setText] = useState('')
  const [requests, setRequests] = useState<Request[]>([])
  const [loading, setLoading] = useState(true)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [success, setSuccess] = useState<string | null>(null)

  const loadRequests = useCallback(async () => {
    try {
      const response = await fetch(`${API_URL}/api/requests`)

      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`)
      }

      const data: Request[] = await response.json()
      setRequests(data)
      setError(null)
  } catch {
    setError(
        'Could not load requests. Make sure the Spring Boot backend is running'
    )
  } finally {
    setLoading(false)
    }
  },[])


  useEffect(() => {
    let active = true

    async function refreshRequests() {
      if (!active) return
      await loadRequests()
    }

    async function startPolling() {
      await refreshRequests()

      if (!active) return

      intervalId = window.setInterval(() => {
        void refreshRequests()
      }, 5000)
    }

    let intervalId: number | undefined

    void startPolling()

    return () => {
      active = false

      if (intervalId !== undefined) {
        window.clearInterval(intervalId)
      }
    }
  }, [loadRequests])


  async function handleSubmit(event: FormEvent<HTMLFormElement>){
    event.preventDefault()
    setError(null)
    setSuccess(null)

    const trimmedText = text.trim()

    if (!trimmedText) {
      setError('Please enter the request details.')
      return
    }
    setSubmitting(true)

    try {
      const response = await fetch(`${API_URL}/api/requests`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({text: trimmedText}),
      })

      if(!response.ok) {
        if (response.status === 400) {
          throw new Error(
              'The request is invalid. Check that it contains no more that 5,000 characters.'
          )
        }
        throw new Error(
            `Could not submit the request (HTTP ${response.status}).`
        )
      }

      setText('')
      setSuccess('Request submitted successfully.')
      await loadRequests()
    } catch (err) {
      setError(
          err instanceof Error
              ? err.message
              : 'An unexpected error occurred.'
      )
    } finally {
      setSubmitting(false)
    }
  }

  const completedCount = requests.filter(
      (request) => request.status === 'COMPLETED'
  ).length

  const reviewCount = requests.filter(
      (request) => request.status === 'IN_REVIEW'
  ).length

  const receivedCount = requests.filter(
      (request) => request.status === 'RECEIVED'
  ).length

  return(
      <main className="app">
        <header className="header">
          <div className="brand">
            <div className="brand-mark" aria-hidden="true">
              T
            </div>

            <div>
              <p className="section-label">APPLICATION PROCESSING</p>
              <h1>Request Manager</h1>
              <p className="subtitle">
                AI-powered classification and workflow management.
              </p>
            </div>
          </div>

          <div className="connection-status">
            <span className="status-dot"/>
            Local environment
          </div>
        </header>

        <section className="metrics" aria-label="Request overview">
          <article className="metric">
            <span className="metric-label">Total requests</span>
            <strong>{loading ? '-' : requests.length}</strong>
            <span className="metric-caption">All registered requests</span>
          </article>

          <article className="metric">
            <span className="metric-label">Awating review</span>
            <strong>{loading ? '-' : reviewCount}</strong>
            <span className="metric-caption">Require human attention</span>
          </article>

          <article className="metric">
            <span className="metric-label">Completed</span>
            <strong>{loading ? '-' : completedCount}</strong>
            <span className="metric-caption">Finished processing</span>
          </article>

          <article className="metric">
          <span className="metric-label">Received</span>
          <strong>{loading ? '—' : receivedCount}</strong>
          <span className="metric-caption">Not yet completed</span>
        </article>
        </section>

        <section className="panel submission-panel">
          <div className="section-heading">
            <div>
              <p className="section-label">NEW REQUEST</p>
              <h2>Submit an application</h2>
              <p>
                Describe the request and let the workflow process it.
              </p>
            </div>
          </div>

          <form onSubmit={handleSubmit}>
            <label htmlFor="request-text">Request details</label>

            <textarea
            id="request-text"
            value={text}
            onChange={(event) => setText(event.target.value)}
            placeholder="For example: I would like to request reimbursement for an invoice of €150..."
            rows={5}
            maxLength={5000}
            required
            disabled={submitting}
            />

            <div className="form-footer">
              <span className="character-count">
                {text.length.toLocaleString()} / 5,000 characters
              </span>

              <button
                  className="primary-button"
                  type="submit"
              disabled={submitting || !text.trim()}
              >
                {submitting ? 'Submitting...' : 'Submit request'}
              </button>
            </div>
          </form>

          {error && (

              <p className="feedback error" role="alert">
                {error}
              </p>
          )}

          {success && (
              <p className="feedback success" role="status">
                {success}
              </p>
          )}
        </section>

        <section className="panel requests-panel">
          <div className="section-heading list-heading">
            <div>
              <p className="section-label">WORKFLOW OVERVIEW</p>
              <h2>Registered requests</h2>
              <p>
                Classification results and processing status.
                The list refreshes every five seconds.
              </p>
            </div>

            <button
            className="secondary-button"
            type="button"
            onClick={() => void loadRequests()}
            disabled={loading}
            >
              {loading ? 'Loading...' : 'Refresh'}
            </button>
          </div>

          {loading && requests.length === 0 && (
              <p className="feedback">Loading requests...</p>
          )}

          {!loading && !error && requests.length === 0 && (
              <div className="empty-state">
                <h3>No requests yet</h3>
                <p>Submit your first request using the form above.</p>
              </div>
          )}

          {requests.length > 0 && (
              <div className="table-wrapper">
                <table>
                  <thead>
                  <tr>
                    <th>ID</th>
                    <th>Request</th>
                    <th>Category</th>
                    <th>Score</th>
                    <th>Amount</th>
                    <th>Status</th>
                    <th>Human review</th>
                    <th>Created</th>
                  </tr>
                  </thead>

                  <tbody>
                  {requests.map((request) => (
                      <tr key={request.id}>
                        <td className="id-cell">#{request.id}</td>

                        <td className="request-cell">
                          <strong>
                            {request.summary || 'Awaiting classification'}
                          </strong>
                          <span>{request.text}</span>
                        </td>

                        <td>{request.category ?? '-'}</td>

                        <td>
                          {request.score == null
                          ? '-'
                          : `${Math.round(request.score * 100)}%`}
                        </td>

                        <td>{formatAmount(request.amount)}</td>

                        <td>
                          <span className={`status status-${request.status.toLocaleLowerCase()}`}
                          >
                            {getStatusLabel(request.status)}
                          </span>
                        </td>

                        <td>
                          {request.reviewedByPerson ? 'Yes' : 'No'}
                        </td>

                        <td className="date-cell">
                          {formatDate(request.createdAt)}
                        </td>
                      </tr>
                  ))}
                  </tbody>
                </table>
              </div>
          )}

          {!loading && error && requests.length > 0 && (
              <p className="feedback warning" role="status">
                The lastest refresh failed. Displaying the last loaded results.
              </p>
          )}
        </section>

        <footer className="app-footer">
          <span>Request Manager</span>
          <span>React · TypeScript · Spring Boot · Camunda 8 · PostgreAQL · Ollama</span>
        </footer>
      </main>
  )
}


export default App
