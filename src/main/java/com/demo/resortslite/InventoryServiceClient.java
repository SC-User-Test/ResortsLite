package com.demo.resortslite;

import java.util.Map;

/**
 * Interface for inventory service communication
 * Enables loose coupling and microservices architecture
 */
public interface InventoryServiceClient {
    
    /**
     * Check room availability
     * @param roomType Type of room to check
     * @return Availability status
     */
    boolean checkRoomAvailability(String roomType);
    
    /**
     * Get room details
     * @param roomType Type of room
     * @return Room details
     */
    Map<String, Object> getRoomDetails(String roomType);
}
