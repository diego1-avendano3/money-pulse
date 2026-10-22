import './App.css'
import { TransactionForm } from './components/TransactionForm'
import { TransactionList } from './components/TransactionList'
import { useTransactions } from './hooks/useTransactions'

function App() {
  const { transactions, loading, error, creating, createTransaction } = useTransactions()

  return (
    <main className="app">
      <h1>Money Pulse</h1>
      <TransactionForm onCreate={createTransaction} creating={creating} />
      <TransactionList transactions={transactions} loading={loading} error={error} />
    </main>
  )
}

export default App
