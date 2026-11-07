/**
 * How many bank messages wait for review, in Arabic: "طلب واحد", "طلبان", "3 طلبات", "11 طلبًا"
 * (BANK-SMS.md B-04). Kept apart from the review page, so the home screen does not load it.
 */
export function pendingLabel(n: number): string {
  if (n === 1) return 'طلب واحد';
  if (n === 2) return 'طلبان';
  if (n >= 3 && n <= 10) return n + ' طلبات';
  return n + ' طلبًا';
}

/**
 * A merchant's name as the server cleans it (MerchantClassifier.clean): capitals, and every sign
 * that is not a letter or a digit is a space. Two cards with the same cleaned name are the same
 * merchant.
 */
export function cleanMerchant(name: string): string {
  return name.toUpperCase().replace(/[^\p{L}\p{N}]+/gu, ' ').trim();
}
