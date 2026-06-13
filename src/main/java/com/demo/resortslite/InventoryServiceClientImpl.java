package com.demo.resortslite;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * Implementation of inventory service client
 * Communicates with external inventory microservice
 */
@Service
public class InventoryServiceClientImpl implements InventoryServiceClient {

    @Value("${app.inventory.endpoint}")
    private String inventoryEndpoint;

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public boolean checkRoomAvailability(String roomType) {
        try {
            String url = inventoryEndpoint + "/available?roomType=" + roomType;
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            return response != null && Boolean.TRUE.equals(response.get("available"));
        } catch (Exception e) {
            // Fallback for service unavailability
            return false;
        }
    }

    @Override
    public Map<String, Object> getRoomDetails(String roomType) {
        try {
            String url = inventoryEndpoint + "/details?roomType=" + roomType;
            return restTemplate.getForObject(url, Map.class);
        } catch (Exception e) {
            Map<String, Object> fallback = new HashMap<>();
            fallback.put("error", "Inventory service unavailable");
            return fallback;
        }
    }
}
