import type { CreateTransactionInput, Transaction } from '../types/transaction'

export async function fetchTransactions(): Promise<Transaction[]> {
  const response = await fetch('/transactions')
  if (!response.ok) {
    throw new Error(`GET /transactions failed: ${response.status}`)
  }
  return response.json() as Promise<Transaction[]>
}

export async function createTransaction(input: CreateTransactionInput): Promise<void> {
  const response = await fetch('/transactions', {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify(input),
  })
  if (!response.ok) {
    throw new Error(`POST /transactions failed: ${response.status}`)
  }
}
