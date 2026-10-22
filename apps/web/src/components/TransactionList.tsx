import type { Transaction } from '../types/transaction'

interface TransactionListProps {
  transactions: Transaction[]
  loading: boolean
  error: string | null
}

const dateFormatter = new Intl.DateTimeFormat(undefined, {
  dateStyle: 'medium',
  timeStyle: 'short',
})

export function TransactionList({ transactions, loading, error }: TransactionListProps) {
  if (loading) {
    return <p>Cargando transacciones…</p>
  }

  if (error) {
    return <p role="alert">No se pudieron cargar las transacciones: {error}</p>
  }

  if (transactions.length === 0) {
    return <p>Sin transacciones todavía.</p>
  }

  return (
    <ul>
      {transactions.map((transaction) => (
        <li key={transaction.id}>
          <strong>{transaction.merchant}</strong>{' '}
          {transaction.amount} {transaction.currency} —{' '}
          {dateFormatter.format(new Date(transaction.occurredAt))}
        </li>
      ))}
    </ul>
  )
}
