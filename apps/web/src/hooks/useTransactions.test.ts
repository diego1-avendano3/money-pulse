import { act, renderHook } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { Transaction } from '../types/transaction'
import { useTransactions } from './useTransactions'

const sampleTransaction: Transaction = {
  id: '1',
  accountId: 'acc-1',
  amount: '-100.00',
  currency: 'COP',
  merchant: 'Shop',
  occurredAt: '2026-09-05T09:00:00Z',
  receivedAt: '2026-09-05T09:00:01Z',
}

describe('useTransactions', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
    vi.unstubAllGlobals()
  })

  it('loads the list on mount and polls every 3s', async () => {
    const fetchMock = vi.fn().mockResolvedValue({ ok: true, json: async () => [sampleTransaction] })
    vi.stubGlobal('fetch', fetchMock)

    const { result } = renderHook(() => useTransactions())
    expect(result.current.loading).toBe(true)

    await act(() => vi.advanceTimersByTimeAsync(0))
    expect(result.current.loading).toBe(false)
    expect(result.current.transactions).toEqual([sampleTransaction])
    expect(fetchMock).toHaveBeenCalledTimes(1)

    await act(() => vi.advanceTimersByTimeAsync(3000))
    expect(fetchMock).toHaveBeenCalledTimes(2)
  })

  it('surfaces an error message when the list fetch fails', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false, status: 500 }))

    const { result } = renderHook(() => useTransactions())
    await act(() => vi.advanceTimersByTimeAsync(0))

    expect(result.current.error).toMatch(/500/)
  })

  it('createTransaction POSTs and refetches so the new row shows up', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce({ ok: true, json: async () => [] }) // initial load
      .mockResolvedValueOnce({ ok: true }) // the POST
      .mockResolvedValueOnce({ ok: true, json: async () => [sampleTransaction] }) // refetch
    vi.stubGlobal('fetch', fetchMock)

    const { result } = renderHook(() => useTransactions())
    await act(() => vi.advanceTimersByTimeAsync(0))

    await act(() =>
      result.current.createTransaction({
        accountId: 'acc-1',
        amount: '-100.00',
        currency: 'COP',
        merchant: 'Shop',
        occurredAt: '2026-09-05T09:00:00Z',
      }),
    )

    expect(fetchMock).toHaveBeenNthCalledWith(
      2,
      '/transactions',
      expect.objectContaining({ method: 'POST' }),
    )
    expect(result.current.transactions).toEqual([sampleTransaction])
  })
})
