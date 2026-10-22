import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { TransactionForm } from './TransactionForm'

async function fillValidForm(user: ReturnType<typeof userEvent.setup>) {
  await user.type(screen.getByLabelText('Cuenta'), 'acc-1')
  await user.type(screen.getByLabelText('Monto'), '-100.00')
  await user.selectOptions(screen.getByLabelText('Moneda'), 'USD')
  await user.type(screen.getByLabelText('Comercio'), 'Shop')
  const dateInput = screen.getByLabelText('Fecha')
  await user.clear(dateInput)
  await user.type(dateInput, '2026-09-05T09:00')
}

describe('TransactionForm', () => {
  it('defaults the date to now', () => {
    render(<TransactionForm onCreate={vi.fn()} creating={false} />)
    const dateInput = screen.getByLabelText('Fecha') as HTMLInputElement
    expect(dateInput.value).not.toBe('')
  })

  it('submits with the typed values, amount kept as a string', async () => {
    const onCreate = vi.fn().mockResolvedValue(undefined)
    const user = userEvent.setup()

    render(<TransactionForm onCreate={onCreate} creating={false} />)
    await fillValidForm(user)
    await user.click(screen.getByRole('button', { name: /crear transacción/i }))

    expect(onCreate).toHaveBeenCalledWith({
      accountId: 'acc-1',
      amount: '-100.00',
      currency: 'USD',
      merchant: 'Shop',
      occurredAt: new Date('2026-09-05T09:00').toISOString(),
    })
  })

  it('does not submit when the amount does not look like a decimal', async () => {
    const onCreate = vi.fn()
    const user = userEvent.setup()

    render(<TransactionForm onCreate={onCreate} creating={false} />)
    await user.type(screen.getByLabelText('Cuenta'), 'acc-1')
    await user.type(screen.getByLabelText('Monto'), 'not-a-number')
    await user.type(screen.getByLabelText('Comercio'), 'Shop')
    await user.click(screen.getByRole('button', { name: /crear transacción/i }))

    expect(onCreate).not.toHaveBeenCalled()
  })

  it('shows the error when the create call fails', async () => {
    const onCreate = vi.fn().mockRejectedValue(new Error('boom'))
    const user = userEvent.setup()

    render(<TransactionForm onCreate={onCreate} creating={false} />)
    await fillValidForm(user)
    await user.click(screen.getByRole('button', { name: /crear transacción/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent('boom')
  })

  it('disables the submit button while creating', () => {
    render(<TransactionForm onCreate={vi.fn()} creating={true} />)
    expect(screen.getByRole('button', { name: /creando/i })).toBeDisabled()
  })
})
