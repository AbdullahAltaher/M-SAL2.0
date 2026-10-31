import { roundMoney } from './amounts';

/** The currency, always beside the number and never inside it (R-GEN-05). */
export const AED = 'د.إ';

const NF_INT = new Intl.NumberFormat('en-US', { maximumFractionDigits: 0 });
const NF_DEC = new Intl.NumberFormat('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });

/**
 * M-SAL's formatMoney (R-GEN-05): whole numbers without decimals ("46", "1,200"), otherwise
 * always two decimals ("46.50"). Latin digits always. Accepts the API's strings ("46.25").
 */
export function formatMoney(v: number | string | null | undefined): string {
  const r = roundMoney(Number(v) || 0);
  return Number.isInteger(r) ? NF_INT.format(r) : NF_DEC.format(r);
}

/** "46.25 د.إ" */
export function moneyText(v: number | string | null | undefined): string {
  return formatMoney(v) + ' ' + AED;
}
