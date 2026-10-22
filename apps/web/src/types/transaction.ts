/** Mirrors TransactionView (apps/transaction-api, spec §4.2) — GET /transactions. */
export interface Transaction {
  id: string
  accountId: string
  /** Always a string, never a JS number — see CreateTransactionInput. */
  amount: string
  currency: string
  merchant: string
  occurredAt: string
  receivedAt: string
}

/** Mirrors CreateTransactionRequest (apps/transaction-api) — POST /transactions body. */
export interface CreateTransactionInput {
  accountId: string
  /**
   * String, not number: the backend contract (spec §4.2) carries `amount` as
   * an exact decimal string specifically so JS never parses it into a
   * float64 and silently loses precision. Keep it a string end to end.
   */
  amount: string
  currency: string
  merchant: string
  occurredAt: string
}
