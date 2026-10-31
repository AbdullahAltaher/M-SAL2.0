package com.abdullah.msal2.stats;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.abdullah.msal2.expenses.EntryData;
import com.abdullah.msal2.system.money.Money;

/**
 * Spending per category (R-STA-02), from M-SAL's {@code categoryTotals}: biggest first, and
 * whole-number percentages that add up to exactly 100 (largest remainder). Live expenses only.
 */
public final class CategoryTotals {

	private CategoryTotals() {
	}

	public record CategoryTotal(UUID categoryId, BigDecimal total, int pct) {
	}

	private record Row(UUID categoryId, long cents) {
	}

	public static List<CategoryTotal> of(List<EntryData> entries) {
		Map<UUID, Long> cents = new LinkedHashMap<>();
		for (EntryData entry : entries) {
			if (entry.isLive() && entry.isExpense()) {
				cents.merge(entry.categoryId(), Money.cents(entry.amount()), Long::sum);
			}
		}
		List<Row> rows = cents.entrySet().stream()
				.map(e -> new Row(e.getKey(), e.getValue()))
				.sorted(Comparator.comparingLong(Row::cents).reversed().thenComparing(r -> r.categoryId().toString()))
				.toList();

		long sum = rows.stream().mapToLong(Row::cents).sum();
		int[] pct = new int[rows.size()];
		if (sum > 0) {
			// Round each share down, then give the missing points to the largest remainders
			// (ties to the earlier row). Remainders share one denominator, so they compare exactly.
			int used = 0;
			long[] remainder = new long[rows.size()];
			for (int i = 0; i < rows.size(); i++) {
				long scaled = rows.get(i).cents() * 100;
				pct[i] = (int) (scaled / sum);
				remainder[i] = scaled % sum;
				used += pct[i];
			}
			List<Integer> order = new ArrayList<>();
			for (int i = 0; i < rows.size(); i++) {
				order.add(i);
			}
			order.sort(Comparator.comparingLong((Integer i) -> remainder[i]).reversed().thenComparing(i -> i));
			for (int k = 0; k < 100 - used; k++) {
				pct[order.get(k)]++;
			}
		}

		List<CategoryTotal> result = new ArrayList<>();
		for (int i = 0; i < rows.size(); i++) {
			result.add(new CategoryTotal(rows.get(i).categoryId(), BigDecimal.valueOf(rows.get(i).cents(), 2), pct[i]));
		}
		return result;
	}
}
