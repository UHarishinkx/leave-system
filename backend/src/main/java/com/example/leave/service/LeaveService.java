package com.example.leave.service;

import com.example.leave.api.DecisionRequest;
import com.example.leave.api.SubmitLeaveRequest;
import com.example.leave.domain.*;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.enums.UserTaskState;
import io.camunda.client.api.search.response.UserTask;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class LeaveService {
    private static final Logger log = LoggerFactory.getLogger(LeaveService.class);
    private static final int DEFAULT_ENTITLEMENT = 20;

    private final LeaveRequestRepository requests;
    private final LeaveBalanceRepository balances;
    private final CamundaClient camunda;

    public LeaveService(LeaveRequestRepository requests, LeaveBalanceRepository balances, CamundaClient camunda) {
        this.requests = requests;
        this.balances = balances;
        this.camunda = camunda;
    }

    /** Not @Transactional: the row must be committed before the worker reads it. */
    public LeaveRequest submit(SubmitLeaveRequest in) {
        LeaveRequest r = new LeaveRequest();
        r.setEmployeeName(in.employeeName());
        r.setEmployeeEmail(in.employeeEmail());
        r.setLeaveType(in.leaveType());
        r.setStartDate(in.startDate());
        r.setEndDate(in.endDate());
        r.setReason(in.reason());
        r.setDays(weekdays(in.startDate(), in.endDate()));
        r = requests.save(r);
        balance(in.employeeEmail());

        long key = camunda.newCreateInstanceCommand()
            .bpmnProcessId("leave-request").latestVersion()
            .variables(Map.of(
                "requestId", r.getId(),
                "employeeEmail", r.getEmployeeEmail(),
                "leaveType", r.getLeaveType(),
                "days", r.getDays(),
                "startDate", r.getStartDate().toString(),
                "endDate", r.getEndDate().toString()))
            .send().join().getProcessInstanceKey();
        requests.updateProcessInstanceKey(r.getId(), key);
        return requests.findById(r.getId()).orElseThrow();
    }

    public void decide(Long id, DecisionRequest d) {
        LeaveRequest r = requests.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found"));
        if (r.getStatus() != LeaveStatus.PENDING_APPROVAL || r.getProcessInstanceKey() == null)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Request is not awaiting approval");

        List<UserTask> tasks = camunda.newUserTaskSearchRequest()
            .filter(f -> f.processInstanceKey(r.getProcessInstanceKey()).state(UserTaskState.CREATED))
            .send().join().items();
        if (tasks.isEmpty())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Approval task not ready yet, retry");

        camunda.newCompleteUserTaskCommand(tasks.get(0).getUserTaskKey())
            .variables(Map.of("approved", d.approved(),
                              "managerComment", d.comment() == null ? "" : d.comment()))
            .send().join();
    }

    @Transactional
    public Map<String, Object> validate(Long requestId) {
        LeaveRequest r = requests.findById(requestId).orElseThrow();
        String msg = null;
        if (r.getEndDate().isBefore(r.getStartDate())) msg = "End date is before start date";
        else if (r.getDays() == 0) msg = "Selected dates contain no working days";
        else if ("ANNUAL".equalsIgnoreCase(r.getLeaveType())
                 && balance(r.getEmployeeEmail()).getRemainingDays() < r.getDays())
            msg = "Insufficient annual leave balance";

        boolean valid = msg == null;
        if (valid) {
            r.setStatus(LeaveStatus.PENDING_APPROVAL);
            r.setStatusMessage("Waiting for manager approval");
            requests.save(r);
        }
        return Map.of("valid", valid, "validationMessage", valid ? "OK" : msg);
    }

    @Transactional
    public void finalizeRequest(Long id, boolean approved, String managerComment, String validationMessage) {
        LeaveRequest r = requests.findById(id).orElseThrow();
        if (r.getStatus() == LeaveStatus.APPROVED || r.getStatus() == LeaveStatus.REJECTED)
            return; // idempotent: a retried job must not deduct twice
        if (approved) {
            if ("ANNUAL".equalsIgnoreCase(r.getLeaveType())) {
                LeaveBalance b = balance(r.getEmployeeEmail());
                b.setRemainingDays(b.getRemainingDays() - r.getDays());
                balances.save(b);
            }
            r.setStatus(LeaveStatus.APPROVED);
            r.setStatusMessage(managerComment == null || managerComment.isBlank() ? "Approved" : managerComment);
        } else {
            r.setStatus(LeaveStatus.REJECTED);
            r.setStatusMessage(managerComment != null && !managerComment.isBlank()
                ? managerComment : validationMessage);
        }
        requests.save(r);
        notifyEmployee(r);
    }

    public LeaveBalance balance(String email) {
        return balances.findById(email)
            .orElseGet(() -> balances.save(new LeaveBalance(email, DEFAULT_ENTITLEMENT)));
    }

    private void notifyEmployee(LeaveRequest r) {
        log.info("NOTIFY {}: leave request #{} is {} ({})",
            r.getEmployeeEmail(), r.getId(), r.getStatus(), r.getStatusMessage());
    }

    static int weekdays(LocalDate from, LocalDate to) {
        int n = 0;
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            DayOfWeek w = d.getDayOfWeek();
            if (w != DayOfWeek.SATURDAY && w != DayOfWeek.SUNDAY) n++;
        }
        return n;
    }
}
