/** A message that may be a bank's debit (the server decides; this only keeps other text out) */
export function looksLikeBankMessage(text: string): boolean {
  return /\bAED\b/i.test(text) && /\ba\/c\b/i.test(text);
}

/**
 * The clipboard's text, read in a tap (the iPhone then shows its "لصق" button), or '' when it
 * cannot be read. Unless {@code any}, only text that looks like a bank message is returned, so
 * something else copied never lands in the box.
 */
export async function readBankMessage(any = false): Promise<string> {
  try {
    const text = (await navigator.clipboard?.readText?.()) ?? '';
    return any || looksLikeBankMessage(text) ? text.trim() : '';
  } catch {
    return '';
  }
}
