import { useCallback, useEffect, useState } from 'react'
import { createTransaction as postTransaction, fetchTransactions } from '../api/transactions'
import type { CreateTransactionInput, Transaction } from '../types/transaction'

const POLL_INTERVAL_MS = 3000

/**
 * transaction-api writes to Kafka and reads the store, but never writes it
 * itself (CQRS-lite, spec §4.3) — transaction-processor persists
 * asynchronously. So a GET right after a POST can legitimately miss the row
 * that was just created. Polling (instead of a single refetch-after-create)
 * is what actually guarantees the new row shows up without a manual reload
 * (spec V8), because it keeps retrying regardless of how long the processor
 * takes.
 */
export function useTransactions() {
  const [transactions, setTransactions] = useState<Transaction[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [creating, setCreating] = useState(false)
  // Bumped after a successful create to get one fetch outside the regular
  // interval, for snappier feedback than waiting up to POLL_INTERVAL_MS.
  const [refreshToken, setRefreshToken] = useState(0)

  useEffect(() => {
    // `ignore` guards against a stale response overwriting newer state --
    // e.g. the component unmounted, or a later poll's response arrives
    // before an earlier one. See https://react.dev/learn/synchronizing-with-effects#fetching-data
    let ignore = false

    async function load() {
      try {
        const data = await fetchTransactions()
        if (!ignore) {
          setTransactions(data)
          setError(null)
        }
      } catch (err) {
        if (!ignore) {
          setError(err instanceof Error ? err.message : 'Failed to load transactions')
        }
      } finally {
        if (!ignore) {
          setLoading(false)
        }
      }
    }

    load()
    const intervalId = setInterval(load, POLL_INTERVAL_MS)

    return () => {
      ignore = true
      clearInterval(intervalId)
    }
  }, [refreshToken])

  const createTransaction = useCallback(async (input: CreateTransactionInput) => {
    setCreating(true)
    try {
      await postTransaction(input)
      setRefreshToken((token) => token + 1)
    } finally {
      setCreating(false)
    }
  }, [])

  return { transactions, loading, error, creating, createTransaction }
}
