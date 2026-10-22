import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { Transaction } from '../types/transaction'
import { TransactionList } from './TransactionList'

const sample: Transaction = {
  id: '1',
  accountId: 'acc-1',
  amount: '-100.00',
  currency: 'COP',
  merchant: 'Shop',
  occurredAt: '2026-09-05T09:00:00Z',
  receivedAt: '2026-09-05T09:00:01Z',
}

describe('TransactionList', () => {
  it('shows a loading message', () => {
    render(<TransactionList transactions={[]} loading={true} error={null} />)
    expect(screen.getByText(/cargando/i)).toBeInTheDocument()
  })

  it('shows the error message', () => {
    render(<TransactionList transactions={[]} loading={false} error="500" />)
    expect(screen.getByRole('alert')).toHaveTextContent('500')
  })

  it('shows an empty state', () => {
    render(<TransactionList transactions={[]} loading={false} error={null} />)
    expect(screen.getByText(/sin transacciones/i)).toBeInTheDocument()
  })

  it('renders each transaction', () => {
    render(<TransactionList transactions={[sample]} loading={false} error={null} />)
    expect(screen.getByText(/Shop/)).toBeInTheDocument()
    expect(screen.getByText(/-100.00 COP/)).toBeInTheDocument()
  })
})
