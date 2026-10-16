package com.teamops.bench.service;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.teamops.bench.dto.BenchDtos.ConsultantRef;
import com.teamops.bench.entity.Allocation;
import com.teamops.bench.entity.ClientEngagement;
import com.teamops.bench.entity.Consultant;
import com.teamops.bench.repository.ClientEngagementRepository;
import com.teamops.bench.repository.ClientRepository;
import com.teamops.bench.repository.ConsultantRepository;
import com.teamops.celebration.entity.Employee;
import com.teamops.celebration.repository.EmployeeRepository;
import com.teamops.common.exception.ApiException;
import com.teamops.recyclebin.service.RecycleBinHandler;

/**
 * Bench records in the recycle bin. A consultant is named after its employee and filed under the employee's
 * department; it comes back only while its Employee record is live. An engagement comes back only while its client
 * is live, an allocation only while its consultant and engagement are. Skills and certifications stay with the
 * consultant and go with its permanent delete (foreign key cascade).
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
	RecycleBinHandler<Allocation> allocationRecycleHandler(ConsultantRepository consultants,
			ClientEngagementRepository engagements) {
		return new RecycleBinHandler<>() {

			@Override
			public Class<Allocation> type() {
				return Allocation.class;
			}

			@Override
			public String label(Allocation allocation) {
				ConsultantRef consultant = ConsultantRef.of(allocation.getConsultant());
				String name = consultant.fullName() == null ? "Consultant #" + consultant.id() : consultant.fullName();
				return name + " – " + allocation.getEngagement().getClient().getName() + " – "
						+ allocation.getEngagement().getName();
			}

			@Override
			public Long departmentOf(Allocation allocation) {
				Employee employee = allocation.getConsultant().getEmployee();
				return employee == null || employee.getDepartment() == null ? null : employee.getDepartment().getId();
			}

			@Override
			public void checkRestore(Allocation allocation) {
				if (!consultants.existsById(allocation.getConsultant().getId())) {
					throw ApiException.conflict("RESTORE_CONFLICT",
							"This allocation's consultant is in the recycle bin. Restore the consultant first.");
				}
				if (!engagements.existsById(allocation.getEngagement().getId())) {
					throw ApiException.conflict("RESTORE_CONFLICT",
							"This allocation's engagement is in the recycle bin. Restore the engagement first.");
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
