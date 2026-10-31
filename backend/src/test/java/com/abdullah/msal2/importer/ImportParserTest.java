package com.abdullah.msal2.importer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.abdullah.msal2.importer.ImportFile.Amount;
import com.abdullah.msal2.importer.ImportFile.Data;
import com.abdullah.msal2.importer.ImportFile.Exp;
import com.abdullah.msal2.importer.ImportFile.Kind;
import com.abdullah.msal2.importer.ImportParser.Counts;
import com.abdullah.msal2.importer.ImportParser.Result;
import com.abdullah.msal2.system.error.ApiException;
import com.abdullah.msal2.system.error.ErrorCode;

import tools.jackson.databind.json.JsonMapper;

/**
 * R-IO-02 and R-IO-04, ported from M-SAL's test/migration.test.js (sanitizeData,
 * sanitizeState, sanitizeExp) and test/expenses.test.js (parseImport, countState), plus the
 * fixes of DATABASE.md 6.2.
 * <p>
 * Not ported: the localStorage loader (main, .bak, v1 keys) and unknown fields riding along,
 * which here are kept in {@code imports.raw_data} instead.
 */
class ImportParserTest {

	private static final ZoneId DUBAI = ZoneId.of("Asia/Dubai");
	private static final Instant NOW = Instant.parse("2026-11-11T08:00:00Z");

	private final ImportParser parser = new ImportParser(JsonMapper.builder().build());

	private Result parse(String json) {
		return parser.parse(json.getBytes(StandardCharsets.UTF_8), DUBAI, NOW);
	}

	private static String q(String json) {
		return json.replace('\'', '"');
	}

	/** expenses.test.js synth(): two people, one with a cycle and a live and a deleted entry. */
	private static final String SYNTH = q("""
			{ 'schemaVersion': 3, 'currentId': 'p1', 'profiles': [
			  { 'id': 'p1', 'name': 'عبدالله', 'data': { 'monthLabel': 'x', 'salaryLabel': 'الراتب', 'salary': '1000',
			    'blocks': [{ 'id': 'b1', 'title': 'ثابتة', 'rows': [
			      { 'id': 'r1', 'name': 'إيجار', 'amount': '500', 'done': false },
			      { 'id': 'r2', 'name': '', 'amount': '~20', 'done': true }] }],
			    'exp': { 'accounts': [{ 'id': 'acc_main', 'name': 'مصاريفي', 'archived': false,
			               'cycles': [{ 'id': 'c1', 'startDate': '2026-09-02', 'allowance': 100 }] }],
			             'lastAccountId': 'acc_main', 'categories': [],
			             'entries': [
			               { 'id': 'e1', 'type': 'expense', 'amount': 5, 'name': '', 'categoryId': 'cat_food', 'date': '2026-09-03', 'accountId': 'acc_main', 'createdAt': 1788000000000 },
			               { 'id': 'e2', 'type': 'expense', 'amount': 6, 'name': '', 'categoryId': 'cat_food', 'date': '2026-09-04', 'accountId': 'acc_main', 'deletedAt': 9 }] } } },
			  { 'id': 'p2', 'name': 'سارة', 'data': { 'blocks': [] } } ] }
			""");

	@Test
	void countStateCountsProfilesBlocksRowsAccountsCyclesLiveAndDeletedEntries() {
		Result r = parse(SYNTH);
		assertThat(r.file().kind()).isEqualTo(Kind.STATE);
		assertThat(r.file().newer()).isFalse();
		// p2 has no exp: M-SAL counts the default account it would create
		assertThat(r.counts()).isEqualTo(new Counts(2, 1, 2, 2, 1, 1, 1));
	}

	@Test
	void parseImportRecognisesOldMultiProfileAndSinglePersonFiles() {
		String old = q("""
				{ 'profiles': [
				  { 'id': 'p1', 'name': 'عبدالله', 'data': { 'salary': '1000', 'blocks': [{ 'id': 'b1', 'title': 'ثابتة', 'rows': [
				      { 'id': 'r1', 'name': 'إيجار', 'amount': '500', 'done': false },
				      { 'id': 'r2', 'name': '', 'amount': '~20', 'done': true }] }] } },
				  { 'id': 'p2', 'name': 'سارة', 'data': { 'blocks': [] } } ] }
				""");
		Result b = parse(old);
		assertThat(b.file().kind()).isEqualTo(Kind.STATE);
		assertThat(b.counts().entries()).isZero();
		assertThat(b.counts().rows()).isEqualTo(2);
		assertThat(b.file().profiles().getFirst().data().exp()).as("no Expenses part: left alone").isNull();

		Result c = parse(q("""
				{ 'salary': '1000', 'blocks': [{ 'id': 'b1', 'title': 'ثابتة', 'rows': [
				    { 'id': 'r1', 'name': 'إيجار', 'amount': '500', 'done': false },
				    { 'id': 'r2', 'name': '', 'amount': '~20', 'done': true }] }] }
				"""));
		assertThat(c.file().kind()).isEqualTo(Kind.SINGLE);
		assertThat(c.counts().blocks()).isEqualTo(1);
		assertThat(c.counts().rows()).isEqualTo(2);
		assertThat(c.file().profiles().getFirst().data().salary()).isEqualTo(new Amount(new BigDecimal("1000.00"), false));
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "not json", "{\"profiles\":" })
	void invalidJsonIsRefused(String bad) {
		assertThatThrownBy(() -> parse(bad)).isInstanceOf(ApiException.class)
				.extracting(e -> ((ApiException) e).errorCode()).isEqualTo(ErrorCode.SRV_007_E001);
	}

	@ParameterizedTest
	@ValueSource(strings = { "[]", "null", "42", "{\"foo\":1}", "{\"profiles\":[]}", "{\"profiles\":[1,2]}" })
	void unknownShapesAreRefused(String bad) {
		assertThatThrownBy(() -> parse(bad)).isInstanceOf(ApiException.class)
				.extracting(e -> ((ApiException) e).errorCode()).isEqualTo(ErrorCode.SRV_007_E002);
	}

	@Test
	void aNewerSchemaVersionIsReported() {
		assertThat(parse(SYNTH.replace("\"schemaVersion\": 3", "\"schemaVersion\": 99")).file().newer()).isTrue();
	}

	@Test
	void knownFieldValidationRepairsBadTypesAndReadsNumbersAsAmounts() {
		Data out = parse(q("""
				{ 'monthLabel': 5, 'salaryLabel': null, 'salary': 15000,
				  'blocks': [null, 7, { 'id': 3, 'title': 9, 'rows': [null, { 'id': 1, 'name': 2, 'amount': 45.5, 'done': 1 }, 'x'] }] }
				""")).file().profiles().getFirst().data();
		assertThat(out.salaryLabel()).isEqualTo("الراتب");
		assertThat(out.monthLabel()).as("not a string: this month").isEqualTo("نوفمبر 2026");
		assertThat(out.salary().value()).isEqualByComparingTo("15000");
		assertThat(out.blocks()).hasSize(1);
		assertThat(out.blocks().getFirst().title()).isEmpty();
		assertThat(out.blocks().getFirst().id()).startsWith(ImportFile.GENERATED);
		assertThat(out.blocks().getFirst().rows()).singleElement().satisfies(r -> {
			assertThat(r.name()).isEmpty();
			assertThat(r.amount().value()).isEqualByComparingTo("45.5");
			assertThat(r.done()).isTrue();
		});
	}

	@Test
	void junkProfilesAreSkippedAndABadCurrentIdFallsBackToTheFirstProfile() {
		ImportFile out = parse(q("""
				{ 'currentId': 'nope', 'profiles': [null, 3, 'x',
				  { 'id': 'pb', 'name': '   ', 'data': { 'blocks': [] } }, { 'id': 'pa', 'name': 'أ', 'data': {} }] }
				""")).file();
		assertThat(out.profiles()).extracting(ImportFile.Profile::id).containsExactly("pb", "pa");
		assertThat(out.profiles().getFirst().name()).isEqualTo("بدون اسم");
		assertThat(out.currentId()).isEqualTo("pb");
	}

	@Test
	void ledgerAmountsFollowParseAmount() {
		assertThat(LedgerAmounts.parse("~15,000")).isEqualTo(new Amount(new BigDecimal("15000.00"), true));
		assertThat(LedgerAmounts.parse("٤٦٫٢٥")).isEqualTo(new Amount(new BigDecimal("46.25"), false));
		assertThat(LedgerAmounts.parse("-20.5")).isEqualTo(new Amount(new BigDecimal("-20.50"), false));
		assertThat(LedgerAmounts.parse("12345.678").value()).isEqualByComparingTo("12345.68");
		assertThat(LedgerAmounts.parse("abc")).as("not a number: 0").isEqualTo(new Amount(new BigDecimal("0.00"), false));
		assertThat(LedgerAmounts.parse("~")).isEqualTo(new Amount(new BigDecimal("0.00"), true));
		assertThat(LedgerAmounts.parse("12abc").value()).isEqualByComparingTo("12");
		assertThat(LedgerAmounts.parse("  ")).isEqualTo(LedgerAmounts.EMPTY);
		assertThat(LedgerAmounts.parse(null)).isEqualTo(LedgerAmounts.EMPTY);
		assertThat(LedgerAmounts.parse("1e20")).as("too large for the database").isNull();
		assertThat(LedgerAmounts.raw(new BigDecimal("15000.00"), true)).isEqualTo("~15000");
		assertThat(LedgerAmounts.raw(new BigDecimal("-20.50"), false)).isEqualTo("-20.5");
		assertThat(LedgerAmounts.raw(new BigDecimal("0.00"), false)).isEqualTo("0");
		assertThat(LedgerAmounts.raw(null, false)).isEmpty();
	}

	/** migration.test.js richExp() */
	@Test
	void anExistingExpIsKeptExactly() {
		Exp exp = parse(q("""
				{ 'schemaVersion': 3, 'profiles': [{ 'id': 'p1', 'name': 'أ', 'data': { 'blocks': [], 'exp': {
				  'accounts': [
				    { 'id': 'acc_main', 'name': 'مصاريفي', 'archived': false, 'cycles': [
				        { 'id': 'c1', 'startDate': '2026-09-02', 'allowance': 3000, 'expectedNextSalaryDate': '2026-10-02' },
				        { 'id': 'c2', 'startDate': '2026-10-02', 'allowance': 3200 } ] },
				    { 'id': 'acc_2', 'name': 'حساب السفر', 'archived': true, 'cycles': [] } ],
				  'lastAccountId': 'acc_2',
				  'categories': [
				    { 'id': 'cat_food', 'name': 'أكل ومطاعم', 'emoji': '🍔', 'hidden': true },
				    { 'id': 'cat_custom1', 'name': 'قطتي', 'emoji': '🐈', 'hidden': false } ],
				  'entries': [
				    { 'id': 'e1', 'type': 'expense', 'amount': 46.25, 'name': 'غداء', 'categoryId': 'cat_food', 'date': '2026-10-03', 'accountId': 'acc_main' },
				    { 'id': 'e2', 'type': 'income', 'amount': 100, 'name': 'مكافأة', 'categoryId': 'cat_custom1', 'date': '2026-10-04', 'accountId': 'acc_main', 'deletedAt': 1790000000000 } ]
				} } }] }
				""")).file().profiles().getFirst().data().exp();
		assertThat(exp.accounts()).extracting(ImportFile.Account::id).containsExactly("acc_main", "acc_2");
		assertThat(exp.accounts().get(1).archived()).isTrue();
		assertThat(exp.accounts().getFirst().cycles()).satisfiesExactly(
				c -> {
					assertThat(c.startDate()).isEqualTo(LocalDate.parse("2026-09-02"));
					assertThat(c.allowance()).isEqualByComparingTo("3000");
					assertThat(c.expectedNextSalaryDate()).isEqualTo(LocalDate.parse("2026-10-02"));
				},
				c -> assertThat(c.expectedNextSalaryDate()).isNull());
		assertThat(exp.lastAccountId()).isEqualTo("acc_2");
		assertThat(exp.categories()).extracting(ImportFile.Category::id).containsExactly("cat_food", "cat_custom1");
		assertThat(exp.categories().getFirst().hidden()).isTrue();
		assertThat(exp.entries()).satisfiesExactly(
				e -> {
					assertThat(e.amount()).isEqualByComparingTo("46.25");
					assertThat(e.income()).isFalse();
					assertThat(e.deletedAt()).isNull();
				},
				e -> {
					assertThat(e.income()).isTrue();
					assertThat(e.deletedAt()).isEqualTo(Instant.ofEpochMilli(1790000000000L));
				});
	}

	@Test
	void aPartialOrDamagedExpIsCompletedNeverEmptied() {
		Exp exp = parse(q("""
				{ 'profiles': [{ 'id': 'p1', 'data': { 'exp': {
				  'accounts': [7, { 'name': '' }, { 'id': 'a2', 'name': 'ب', 'archived': 'yes', 'cycles': 'x' }],
				  'lastAccountId': 'gone', 'categories': [null, { 'id': 'c', 'emoji': '' }], 'entries': 'x' } } }] }
				""")).file().profiles().getFirst().data().exp();
		assertThat(exp.accounts()).hasSize(2);
		assertThat(exp.accounts().getFirst().id()).startsWith(ImportFile.GENERATED);
		assertThat(exp.accounts().getFirst().name()).isEqualTo(ImportParser.DEFAULT_ACCOUNT_NAME);
		assertThat(exp.accounts().get(1).archived()).as("JavaScript truthiness").isTrue();
		assertThat(exp.lastAccountId()).isEqualTo(exp.accounts().getFirst().id());
		assertThat(exp.categories()).singleElement().satisfies(c -> {
			assertThat(c.name()).isEmpty();
			assertThat(c.emoji()).isEqualTo("📦");
		});
		assertThat(exp.entries()).isEmpty();

		Exp empty = parse("{\"profiles\":[{\"data\":{\"exp\":{}}}]}").file().profiles().getFirst().data().exp();
		assertThat(empty.accounts()).singleElement().extracting(ImportFile.Account::id).isEqualTo("acc_main");
		assertThat(empty.lastAccountId()).isEqualTo("acc_main");
		assertThat(empty.categories()).hasSize(20).isEqualTo(ImportParser.DEFAULT_CATEGORIES);
	}

	@Test
	void entriesTheDatabaseCannotHoldAreFixedOrSkippedWithWarnings() {
		Result r = parse(q("""
				{ 'profiles': [{ 'id': 'p1', 'data': { 'exp': {
				  'accounts': [{ 'id': 'a1', 'name': 'أ', 'cycles': [
				      { 'id': 'c1', 'startDate': '2026-09-02', 'allowance': -5 },
				      { 'id': 'c2', 'startDate': '2026-09-02' },
				      { 'id': 'c3', 'startDate': '2026-02-30' },
				      { 'id': 'c4', 'startDate': '2026-10-02', 'allowance': '200', 'expectedNextSalaryDate': '2026-10-01' }] }],
				  'categories': [{ 'id': 'k1', 'name': 'ك' }],
				  'entries': [
				    { 'id': 'e1', 'amount': 5, 'categoryId': 'gone', 'accountId': 'a1', 'date': '2026-09-03' },
				    { 'id': 'e2', 'amount': 5, 'categoryId': 'k1', 'accountId': 'gone', 'date': '2026-09-03' },
				    { 'id': 'e3', 'amount': 5, 'categoryId': 'k1', 'accountId': 'a1', 'date': '3 Sep', 'createdAt': 1788386400000 },
				    { 'id': 'e4', 'amount': 5, 'categoryId': 'k1', 'accountId': 'a1' },
				    { 'id': 'e5', 'amount': 0, 'categoryId': 'k1', 'accountId': 'a1', 'date': '2026-09-03' },
				    { 'id': 'e6', 'amount': 'abc', 'categoryId': 'k1', 'accountId': 'a1', 'date': '2026-09-03' },
				    { 'id': 'e1', 'amount': 7.005, 'type': 'income', 'categoryId': 'k1', 'accountId': 'a1', 'date': '2026-09-03', 'deletedAt': true }] } } }] }
				"""));
		Exp exp = r.file().profiles().getFirst().data().exp();
		assertThat(exp.accounts().getFirst().cycles()).satisfiesExactly(
				c -> assertThat(c.allowance()).as("negative: 0").isEqualByComparingTo("0"),
				c -> {
					assertThat(c.id()).isEqualTo("c4");
					assertThat(c.allowance()).isEqualByComparingTo("200");
					assertThat(c.expectedNextSalaryDate()).as("not after the start").isNull();
				});
		assertThat(exp.categories()).extracting(ImportFile.Category::id).containsExactly("k1", "cat_other");
		assertThat(exp.entries()).satisfiesExactly(
				e -> assertThat(e.categoryId()).isEqualTo("cat_other"),
				e -> assertThat(e.accountId()).isEqualTo("a1"),
				e -> assertThat(e.date()).as("createdAt in Dubai").isEqualTo(LocalDate.parse("2026-09-03")),
				e -> assertThat(e.date()).as("no createdAt: today in Dubai").isEqualTo(LocalDate.parse("2026-11-11")),
				e -> {
					assertThat(e.id()).as("a repeated id gets its own").startsWith(ImportFile.GENERATED);
					assertThat(e.amount()).isEqualByComparingTo("7.01");
					assertThat(e.income()).isTrue();
					assertThat(e.deletedAt()).isEqualTo(NOW);
				});
		assertThat(r.counts().cycles()).isEqualTo(4);
		assertThat(r.counts().entries()).isEqualTo(6);
		assertThat(r.counts().deleted()).isEqualTo(1);
		assertThat(r.file().warnings().list()).extracting(Warnings.Warning::code, Warnings.Warning::count)
				.containsExactlyInAnyOrder(
						org.assertj.core.groups.Tuple.tuple("CYCLE_INVALID", 2),
						org.assertj.core.groups.Tuple.tuple("CATEGORY_MISSING", 1),
						org.assertj.core.groups.Tuple.tuple("ACCOUNT_MISSING", 1),
						org.assertj.core.groups.Tuple.tuple("DATE_INVALID", 2),
						org.assertj.core.groups.Tuple.tuple("AMOUNT_INVALID", 2));
		assertThat(r.file().warnings().list()).extracting(Warnings.Warning::message)
				.contains("مصاريف بفئة غير موجودة، ستُربط بفئة «أخرى» (1)");
	}

	@Test
	void theTwentyDefaultCategoriesMatchTheTemplates() {
		List<ImportFile.Category> c = ImportParser.DEFAULT_CATEGORIES;
		assertThat(c).hasSize(20).extracting(ImportFile.Category::id).doesNotHaveDuplicates();
		assertThat(c.getFirst()).isEqualTo(new ImportFile.Category("cat_food", "أكل ومطاعم", "🍔", false, "cat_food"));
		assertThat(c.getLast()).isEqualTo(new ImportFile.Category("cat_other", "أخرى", "📦", false, "cat_other"));
		assertThat(c).noneMatch(ImportFile.Category::hidden);
	}
}
