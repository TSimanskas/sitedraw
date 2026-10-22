export type PasswordRule = 'length' | 'upper' | 'number' | 'symbol'

export function passwordRuleStatus(password: string): Record<PasswordRule, boolean> {
  return {
    length: password.length >= 8,
    upper: /\p{Lu}/u.test(password),
    number: /\p{N}/u.test(password),
    symbol: /[^\p{L}\p{N}]/u.test(password),
  }
}

export function isStrongPassword(password: string) {
  const status = passwordRuleStatus(password)
  return status.length && status.upper && status.number && status.symbol
}
