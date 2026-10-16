package com.teamops.bench.service;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.teamops.bench.entity.ClientEngagement;
import com.teamops.bench.entity.Consultant;
import com.teamops.bench.repository.ClientRepository;
import com.teamops.celebration.repository.EmployeeRepository;
import com.teamops.common.exception.ApiException;
import com.teamops.recyclebin.service.RecycleBinHandler;

/**
 * Bench records in the recycle bin. A consultant is named after its employee and filed under the employee's
 * department; it comes back only while its Employee record is live. An engagement comes back only while its client
 * is live. Skills and certifications stay with the consultant and go with its permanent delete (foreign key cascade).
 */
@Configuration
class BenchRecycleHandlers {

	@Bean
	RecycleBinHandler<Consultant> consultantRecycleHandler(EmployeeRepository employees) {
		return new RecycleBinHandler<>() {

			@Override
			public Class<Consultant> type() {
				return Consultant.class;
			}

			@Override
			public String label(Consultant consultant) {
				return consultant.getEmployee() == null ? null
						: consultant.getEmployee().getEmployeeCode() + " " + consultant.getEmployee().getFullName();
			}

			@Override
			public Long departmentOf(Consultant consultant) {
				return consultant.getEmployee() == null || consultant.getEmployee().getDepartment() == null ? null
						: consultant.getEmployee().getDepartment().getId();
			}

			@Override
			public void checkRestore(Consultant consultant) {
				if (!employees.existsById(consultant.getEmployeeId())) {
					throw ApiException.conflict("RESTORE_CONFLICT",
							"This consultant's employee record is in the recycle bin. Restore the employee first.");
				}
			}

		};
	}

	@Bean
	RecycleBinHandler<ClientEngagement> engagementRecycleHandler(ClientRepository clients) {
		return new RecycleBinHandler<>() {

			@Override
			public Class<ClientEngagement> type() {
				return ClientEngagement.class;
			}

			@Override
			public String label(ClientEngagement engagement) {
				return engagement.getClient().getName() + " – " + engagement.getName();
			}

			@Override
			public void checkRestore(ClientEngagement engagement) {
				if (!clients.existsById(engagement.getClient().getId())) {
					throw ApiException.conflict("RESTORE_CONFLICT",
							"This engagement's client is in the recycle bin. Restore the client first.");
				}
			}

		};
	}

}
