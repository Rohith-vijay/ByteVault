package com.bytevault.fulfillment.service;

import com.bytevault.fulfillment.dto.DlqIncidentDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class DlqIncidentRegistry {

    private static final int MAX_INCIDENTS = 100;
    private final Map<String, DlqIncidentDto> incidents = new ConcurrentHashMap<>();
    private final LinkedList<String> orderList = new LinkedList<>();

    public synchronized void recordIncident(DlqIncidentDto incident) {
        if (incident == null || incident.getIncidentId() == null) return;

        if (orderList.size() >= MAX_INCIDENTS) {
            String oldestId = orderList.removeFirst();
            incidents.remove(oldestId);
        }

        orderList.addLast(incident.getIncidentId());
        incidents.put(incident.getIncidentId(), incident);
        log.info("[DlqIncidentRegistry] Recorded DLQ incident: id={}, orderId={}",
                incident.getIncidentId(), incident.getOrderId());
    }

    public List<DlqIncidentDto> getAllIncidents() {
        return new ArrayList<>(incidents.values());
    }

    public Optional<DlqIncidentDto> getIncident(String incidentId) {
        return Optional.ofNullable(incidents.get(incidentId));
    }

    public Optional<DlqIncidentDto> findByOrderId(String orderId) {
        return incidents.values().stream()
                .filter(i -> orderId.equals(i.getOrderId()))
                .findFirst();
    }

    public synchronized void markReplayed(String incidentId) {
        DlqIncidentDto incident = incidents.get(incidentId);
        if (incident != null) {
            incident.setStatus("REPLAYED");
        }
    }
}
