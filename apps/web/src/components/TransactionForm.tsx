import { useState } from 'react'
import type { CreateTransactionInput } from '../types/transaction'

interface TransactionFormProps {
  onCreate: (input: CreateTransactionInput) => Promise<void>
  creating: boolean
}

const AMOUNT_PATTERN = '^-?\\d+(\\.\\d{1,2})?$'

// Small fixed list for Phase 0 -- no design system / data-driven catalog yet
// (that's Fase 1). COP first since it's what the project's own examples use.
const CURRENCIES = ['COP', 'USD', 'MXN', 'EUR']

/**
 * Formats a Date as the local "YYYY-MM-DDTHH:mm" string <input
 * type="datetime-local"> expects. Deliberately NOT `date.toISOString()`:
 * that's always UTC, but datetime-local has no timezone and the browser
 * reads/writes it as local wall-clock time -- using the UTC string here
 * would silently shift the value (and the `max` bound) by your UTC offset.
 */
function toDatetimeLocalValue(date: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`
}

function createDefaultValues() {
  return {
    accountId: '',
    // Text, not a number input: the backend carries `amount` as an exact
    // decimal string on purpose (spec §4.2) so JS never rounds it through a
    // float64. A number input would defeat that the moment someone typed into it.
    amount: '',
    currency: CURRENCIES[0],
    merchant: '',
    // occurredAt is "when it happened at the bank" (spec §4.2), not "now" --
    // the backend allows backdating on purpose (@PastOrPresent), since a
    // transaction can be reported days after it occurred. Defaulting to now
    // covers the common case (logging something that just happened) while
    // staying editable for the rest.
    occurredAt: toDatetimeLocalValue(new Date()),
  }
}

export function TransactionForm({ onCreate, creating }: TransactionFormProps) {
  const [values, setValues] = useState(createDefaultValues)
  const [submitError, setSubmitError] = useState<string | null>(null)

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setSubmitError(null)
    try {
      await onCreate({
        accountId: values.accountId,
        amount: values.amount,
        currency: values.currency,
        merchant: values.merchant,
        // <input type="datetime-local"> has no timezone; new Date(...)
        // interprets it in the browser's local time, and toISOString()
        // converts that to the UTC instant the backend expects.
        occurredAt: new Date(values.occurredAt).toISOString(),
      })
      setValues(createDefaultValues())
    } catch (err) {
      setSubmitError(err instanceof Error ? err.message : 'No se pudo crear la transacción')
    }
  }

  function updateField(field: keyof typeof values) {
    return (event: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => {
      setValues((current) => ({ ...current, [field]: event.target.value }))
    }
  }

  return (
    <form onSubmit={handleSubmit}>
      <label>
        Cuenta
        <input
          required
          value={values.accountId}
          onChange={updateField('accountId')}
          name="accountId"
        />
      </label>
      <label>
        Monto
        <input
          required
          inputMode="decimal"
          pattern={AMOUNT_PATTERN}
          title="Negativo para egresos, p. ej. -42000 o -42000.50"
          value={values.amount}
          onChange={updateField('amount')}
          name="amount"
        />
      </label>
      <label>
        Moneda
        <select value={values.currency} onChange={updateField('currency')} name="currency">
          {CURRENCIES.map((code) => (
            <option key={code} value={code}>
              {code}
            </option>
          ))}
        </select>
      </label>
      <label>
        Comercio
        <input
          required
          value={values.merchant}
          onChange={updateField('merchant')}
          name="merchant"
        />
      </label>
      <label>
        Fecha
        <input
          required
          type="datetime-local"
          max={toDatetimeLocalValue(new Date())}
          value={values.occurredAt}
          onChange={updateField('occurredAt')}
          name="occurredAt"
        />
      </label>
      <button type="submit" disabled={creating}>
        {creating ? 'Creando…' : 'Crear transacción'}
      </button>
      {submitError && <p role="alert">{submitError}</p>}
    </form>
  )
}
