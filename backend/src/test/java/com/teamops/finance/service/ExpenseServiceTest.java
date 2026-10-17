package com.teamops.finance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.teamops.approval.repository.ApprovalRepository;
import com.teamops.common.audit.AuditService;
import com.teamops.common.exception.ApiException;
import com.teamops.common.settings.AppSettingsService;
import com.teamops.common.storage.FileService;
import com.teamops.common.web.ClientInfo;
import com.teamops.department.repository.DepartmentRepository;
import com.teamops.finance.dto.FinanceDtos.ExpenseDto;
import com.teamops.finance.dto.FinanceDtos.SaveExpense;
import com.teamops.finance.entity.ExpenseCategory;
import com.teamops.finance.entity.FinanceExpense;
import com.teamops.finance.repository.FinanceExpenseRepository;
import com.teamops.recyclebin.service.RecycleBinService;
import com.teamops.support.SliceAuth;
import com.teamops.vendor.repository.VendorRepository;

/**
 * Expenses keep the AUD rate in force on their date: taken on create and when the date or currency changes, kept
 * when only the amount changes. GST is part of the amount; the default currency comes from the Finance setting.
 */
class ExpenseServiceTest {

	private static final LocalDate SEPTEMBER = LocalDate.of(2026, 9, 15);

	private static final LocalDate OCTOBER = LocalDate.of(2026, 10, 15);

	private final FinanceExpenseRepository repository = mock(FinanceExpenseRepository.class);

	private final ExchangeRateService rates = mock(ExchangeRateService.class);

	private final AppSettingsService settings = mock(AppSettingsService.class);

	private final ApprovalRepository approvals = mock(ApprovalRepository.class);

	private final ExpenseService service = new ExpenseService(repository, mock(VendorRepository.class),
			mock(DepartmentRepository.class), approvals, rates, mock(FileService.class), settings,
			mock(AuditService.class), mock(RecycleBinService.class));

	@BeforeEach
	void setUp() {
		when(settings.financeDefaultCurrency()).thenReturn("INR");
		when(rates.ratesOn(anyCollection(), eq(SEPTEMBER)))
			.thenReturn(Map.of("INR", ExchangeRateServiceTest.rate(1L, "INR", "2026-09-01", "0.01800000")));
		when(rates.ratesOn(anyCollection(), eq(OCTOBER)))
			.thenReturn(Map.of("INR", ExchangeRateServiceTest.rate(2L, "INR", "2026-10-01", "0.01820000")));
		when(repository.saveAndFlush(any())).thenAnswer(call -> {
			FinanceExpense saved = call.getArgument(0);
			ReflectionTestUtils.setField(saved, "id", 70L);
			return saved;
		});
	}

	@Test
	void anExpenseTakesTheRateInForceOnItsDateInTheDefaultCurrency() {
		ExpenseDto saved = service.create(request(SEPTEMBER, "42000.00", "6406.78", null, null), SliceAuth.SUPER_ADMIN,
				ClientInfo.unknown());

		assertThat(saved.currency()).isEqualTo("INR");
		assertThat(saved.audRate()).isEqualByComparingTo("0.018");
		assertThat(saved.audRateDate()).isEqualTo(LocalDate.of(2026, 9, 1));
		assertThat(saved.amountAud()).isEqualByComparingTo("756.00");
		assertThat(saved.gstAud()).isEqualByComparingTo("115.32");
		assertThat(saved.amountExGst()).isEqualByComparingTo("35593.22");
	}

	@Test
	void aChangedAmountKeepsTheRateButANewDateTakesItAgain() {
		FinanceExpense expense = existing();
		when(repository.findDetailedById(70L)).thenReturn(Optional.of(expense));

		ExpenseDto amountOnly = service.update(70L, request(SEPTEMBER, "84000.00", null, null, 0), SliceAuth.SUPER_ADMIN,
				ClientInfo.unknown());
		assertThat(amountOnly.audRate()).isEqualByComparingTo("0.018");
		assertThat(amountOnly.amountAud()).isEqualByComparingTo("1512.00");

		// Even if September's rate changed since, the stored rate stays; only a new date (or currency) takes it again.
		ExpenseDto newDate = service.update(70L, request(OCTOBER, "84000.00", null, null, expense.getVersion()),
				SliceAuth.SUPER_ADMIN, ClientInfo.unknown());
		assertThat(newDate.audRate()).isEqualByComparingTo("0.0182");
		assertThat(newDate.audRateDate()).isEqualTo(LocalDate.of(2026, 10, 1));
		assertThat(newDate.amountAud()).isEqualByComparingTo("1528.80");
	}

	@Test
	void audExpensesNeedNoRate() {
		ExpenseDto saved = service.create(request(SEPTEMBER, "99.95", null, "AUD", null), SliceAuth.SUPER_ADMIN,
				ClientInfo.unknown());

		assertThat(saved.audRate()).isEqualByComparingTo("1");
		assertThat(saved.audRateDate()).isNull();
		assertThat(saved.amountAud()).isEqualByComparingTo("99.95");
		verify(rates, never()).ratesOn(anyCollection(), eq(SEPTEMBER));
	}

	@Test
	void aCurrencyWithoutARateOnTheDateOrTooMuchGstIsRefused() {
		when(rates.ratesOn(anyCollection(), eq(SEPTEMBER))).thenReturn(Map.of());
		assertThatThrownBy(() -> service.create(request(SEPTEMBER, "100.00", null, "SGD", null), SliceAuth.SUPER_ADMIN,
				ClientInfo.unknown()))
			.isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getCode()).isEqualTo("NO_EXCHANGE_RATE"));
		assertThatThrownBy(() -> service.create(request(OCTOBER, "100.00", "100.01", null, null), SliceAuth.SUPER_ADMIN,
				ClientInfo.unknown()))
			.isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getCode()).isEqualTo("INVALID_GST"));
	}

	@Test
	void anUnknownApprovalIsRefused() {
		when(approvals.findById(5L)).thenReturn(Optional.empty());
		SaveExpense request = new SaveExpense(OCTOBER, ExpenseCategory.HARDWARE, null, "Laptop", new BigDecimal("100.00"),
				null, null, null, 5L, null, null);
		assertThatThrownBy(() -> service.create(request, SliceAuth.SUPER_ADMIN, ClientInfo.unknown()))
			.isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getCode()).isEqualTo("UNKNOWN_APPROVAL"));
	}

	private FinanceExpense existing() {
		FinanceExpense expense = new FinanceExpense();
		ReflectionTestUtils.setField(expense, "id", 70L);
		expense.setVersion(0);
		expense.setExpenseDate(SEPTEMBER);
		expense.setCategory(ExpenseCategory.SOFTWARE);
		expense.setDescription("Figma seats");
		expense.setAmount(new BigDecimal("42000.00"));
		expense.setCurrency("INR");
		expense.setAudRate(new BigDecimal("0.01800000"));
		expense.setAudRateDate(LocalDate.of(2026, 9, 1));
		expense.setAmountAud(new BigDecimal("756.00"));
		return expense;
	}

	private static SaveExpense request(LocalDate date, String amount, String gst, String currency, Integer version) {
		return new SaveExpense(date, ExpenseCategory.SOFTWARE, null, "Figma seats", new BigDecimal(amount),
				gst == null ? null : new BigDecimal(gst), currency, null, null, null, version);
	}

}
