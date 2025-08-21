# TextProc API Standardization Implementation Guide

This guide provides specific code changes needed to align textProc's API with the standardized structure used by embedProc.

## 🎯 **Overview**

Based on the API Consistency Comparison, textProc needs the following changes:
1. **Response Fields**: Standardize field names (`isProcessingEnabled` → `enabled`)
2. **Consumer Status Values**: Change from `"running"/"stopped"` to `"CONSUMING"/"IDLE"`
3. **Response Structure**: Add missing fields like `lastChanged`, `lastChangeReason`, `stateChanged`
4. **Timestamp Fields**: Add timestamp fields with ISO 8601 format
5. **Success Response Format**: Align with embedProc's response structure

## 📁 **Files to Modify**

### 1. **ProcessingApiController.java**

#### Update GET /api/processing/state Response
```java
// CHANGE FROM:
return Map.of(
    "processingState", processingStateService.getProcessingState(),
    "isProcessingEnabled", processingStateService.isProcessingEnabled(),
    "consumerStatus", consumerLifecycleService.getConsumerStatus()
);

// CHANGE TO:
return Map.of(
    "enabled", processingStateService.isProcessingEnabled(),
    "status", processingStateService.getProcessingState(), // Will return "STARTED" or "STOPPED"
    "consumerStatus", mapConsumerStatus(consumerLifecycleService.getConsumerStatus()),
    "lastChanged", processingStateService.getLastChanged().toString(),
    "lastChangeReason", processingStateService.getLastChangeReason(),
    "timestamp", OffsetDateTime.now(ZoneOffset.UTC).toString()
);
```

#### Add Helper Method for Consumer Status Mapping
```java
private String mapConsumerStatus(String currentStatus) {
    // Map from textProc's current values to standardized values
    return switch (currentStatus.toLowerCase()) {
        case "running" -> "CONSUMING";
        case "stopped" -> "IDLE";
        default -> currentStatus.toUpperCase(); // fallback
    };
}
```

#### Update POST /api/processing/start Response
```java
// CHANGE FROM:
return Map.of(
    "status", "success",
    "message", "Processing started",
    "processingState", processingStateService.getProcessingState(),
    "consumerStatus", consumerLifecycleService.getConsumerStatus()
);

// CHANGE TO:
boolean stateChanged = processingStateService.startProcessing(); // Modify method to return boolean
return Map.of(
    "success", true,
    "message", "Processing started successfully",
    "stateChanged", stateChanged,
    "enabled", true,
    "status", "STARTED",
    "consumerStatus", "CONSUMING",
    "lastChanged", processingStateService.getLastChanged().toString(),
    "timestamp", OffsetDateTime.now(ZoneOffset.UTC).toString()
);
```

#### Update POST /api/processing/stop Response
```java
// CHANGE FROM:
return Map.of(
    "status", "success",
    "message", "Processing stopped",
    "processingState", processingStateService.getProcessingState(),
    "consumerStatus", consumerLifecycleService.getConsumerStatus()
);

// CHANGE TO:
boolean stateChanged = processingStateService.stopProcessing(); // Modify method to return boolean
return Map.of(
    "success", true,
    "message", "Processing stopped successfully. Messages will remain in queue.",
    "stateChanged", stateChanged,
    "enabled", false,
    "status", "STOPPED",
    "consumerStatus", "IDLE",
    "lastChanged", processingStateService.getLastChanged().toString(),
    "timestamp", OffsetDateTime.now(ZoneOffset.UTC).toString()
);
```

#### Update POST /api/processing/toggle Response
```java
// CHANGE FROM:
boolean wasEnabled = processingStateService.isProcessingEnabled();
if (wasEnabled) {
    processingStateService.stopProcessing();
} else {
    processingStateService.startProcessing();
}

String action = wasEnabled ? "stopped" : "started";
return Map.of(
    "status", "success",
    "message", "Processing " + action,
    "previousState", wasEnabled ? "enabled" : "disabled",
    "currentState", processingStateService.isProcessingEnabled() ? "enabled" : "disabled",
    "processingState", processingStateService.getProcessingState(),
    "consumerStatus", consumerLifecycleService.getConsumerStatus()
);

// CHANGE TO:
boolean wasEnabled = processingStateService.isProcessingEnabled();

// Toggle the state
boolean stateChanged;
if (wasEnabled) {
    stateChanged = processingStateService.stopProcessing();
} else {
    stateChanged = processingStateService.startProcessing();
}

boolean currentEnabled = processingStateService.isProcessingEnabled();
String action = currentEnabled ? "started" : "stopped";

return Map.of(
    "success", true,
    "message", String.format("Processing %s successfully. Previous state: %s, Current state: %s. %s",
        action,
        wasEnabled ? "enabled" : "disabled",
        currentEnabled ? "enabled" : "disabled",
        currentEnabled ? "Now consuming messages from queue." : "Messages will remain in queue."),
    "action", action,
    "previousState", Map.of(
        "enabled", wasEnabled,
        "status", wasEnabled ? "STARTED" : "STOPPED"
    ),
    "currentState", Map.of(
        "enabled", currentEnabled,
        "status", currentEnabled ? "STARTED" : "STOPPED",
        "consumerStatus", currentEnabled ? "CONSUMING" : "IDLE"
    ),
    "lastChanged", processingStateService.getLastChanged().toString(),
    "timestamp", OffsetDateTime.now(ZoneOffset.UTC).toString()
);
```

#### Update POST /api/processing/reset Response
```java
// CHANGE FROM:
return Map.of(
    "status", "success",
    "message", "Reset completed",
    "processingState", processingStateService.getProcessingState(),
    "consumerStatus", consumerLifecycleService.getConsumerStatus(),
    "hdfsCleared", hdfsCleared,
    "directoryRecreated", directoryRecreated
);

// CHANGE TO:
return Map.of(
    "success", true,
    "message", "Reset completed successfully. Processing stopped and files cleared.",
    "stateChanged", true, // Reset always changes state
    "enabled", false,
    "status", "STOPPED",
    "consumerStatus", "IDLE",
    "hdfsCleared", hdfsCleared,
    "directoryRecreated", directoryRecreated,
    "lastChanged", processingStateService.getLastChanged().toString(),
    "timestamp", OffsetDateTime.now(ZoneOffset.UTC).toString()
);
```

### 2. **ProcessingStateService.java**

#### Add State Change Tracking Fields
```java
@Service
public class ProcessingStateService {
    private final AtomicBoolean isProcessingEnabled = new AtomicBoolean(false);
    private final ApplicationEventPublisher eventPublisher;
    
    // ADD THESE FIELDS:
    private volatile OffsetDateTime lastStateChange = OffsetDateTime.now(ZoneOffset.UTC);
    private volatile String lastChangeReason = "Application startup";
```

#### Update startProcessing Method to Return Boolean
```java
// CHANGE FROM:
public void startProcessing() {
    isProcessingEnabled.set(true);
    eventPublisher.publishEvent(new ProcessingStartedEvent(this));
}

// CHANGE TO:
public boolean startProcessing() {
    boolean previousState = isProcessingEnabled.getAndSet(true);
    if (!previousState) {
        lastStateChange = OffsetDateTime.now(ZoneOffset.UTC);
        lastChangeReason = "Processing started via API";
        eventPublisher.publishEvent(new ProcessingStartedEvent(this));
        return true; // State changed
    }
    return false; // State didn't change
}
```

#### Update stopProcessing Method to Return Boolean
```java
// CHANGE FROM:
public void stopProcessing() {
    isProcessingEnabled.set(false);
    eventPublisher.publishEvent(new ProcessingStoppedEvent(this));
}

// CHANGE TO:
public boolean stopProcessing() {
    boolean previousState = isProcessingEnabled.getAndSet(false);
    if (previousState) {
        lastStateChange = OffsetDateTime.now(ZoneOffset.UTC);
        lastChangeReason = "Processing stopped via API";
        eventPublisher.publishEvent(new ProcessingStoppedEvent(this));
        return true; // State changed
    }
    return false; // State didn't change
}
```

#### Add New Getter Methods
```java
public OffsetDateTime getLastChanged() {
    return lastStateChange;
}

public String getLastChangeReason() {
    return lastChangeReason;
}
```

### 3. **ConsumerLifecycleService.java** (if needed)

#### Update Consumer Status Values
If the `getConsumerStatus()` method returns "running"/"stopped", consider updating it to return the standardized values, or use the mapping function in the controller.

```java
// OPTION 1: Update the service to return standardized values
public String getConsumerStatus() {
    // Change from "running"/"stopped" to "CONSUMING"/"IDLE"
    return isRunning() ? "CONSUMING" : "IDLE";
}

// OPTION 2: Keep existing and use mapping in controller (recommended for minimal impact)
// Use the mapConsumerStatus() helper method in ProcessingApiController
```

### 4. **Add Required Imports**

In **ProcessingApiController.java**:
```java
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
```

In **ProcessingStateService.java**:
```java
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
```

## 🔧 **Implementation Steps**

### Step 1: Update ProcessingStateService
1. Add state change tracking fields (`lastStateChange`, `lastChangeReason`)
2. Modify `startProcessing()` and `stopProcessing()` to return boolean and track changes
3. Add new getter methods for state change information

### Step 2: Update ProcessingApiController
1. Add helper method for consumer status mapping
2. Update all response structures to use standardized field names
3. Add missing fields (`lastChanged`, `lastChangeReason`, `timestamp`, `stateChanged`)
4. Update response structures to match embedProc pattern
5. Add comprehensive toggle response with previous/current state details

### Step 3: Optional - Update ConsumerLifecycleService
1. Consider updating `getConsumerStatus()` to return standardized values
2. Or rely on mapping function in controller (less intrusive)

### Step 4: Test the Changes
1. Test all endpoints with the new response format
2. Verify field names match the standardized structure
3. Test state change tracking works correctly
4. Confirm consumer status mapping works properly

### Step 5: Update Documentation
The API_ENDPOINTS_GUIDE.md has already been updated with the new standardized format.

## 🎉 **Expected Results**

After implementation:
- ✅ Field names: `enabled` vs `isProcessingEnabled` (consistent)
- ✅ Status values: `"STARTED"`, `"STOPPED"` (already consistent)
- ✅ Consumer status: `"CONSUMING"`, `"IDLE"` vs `"running"`, `"stopped"` (consistent)
- ✅ Timestamps: ISO 8601 format added (consistent)
- ✅ State tracking: `lastChanged`, `lastChangeReason` added (enhanced)
- ✅ Response structure: Matches embedProc pattern (consistent)
- ✅ Success responses: Include `stateChanged`, detailed messages (enhanced)

The textProc API will then be fully aligned with the standardized structure while preserving its unique file processing and HDFS integration capabilities.

## 📋 **Additional Considerations**

### Event Publishing
The existing Spring event publishing mechanism should continue to work with the updated methods. The boolean return values provide additional information about whether state actually changed.

### Backward Compatibility
If existing clients depend on the current field names, consider:
1. **Gradual Migration**: Include both old and new field names temporarily
2. **Version Headers**: Support both formats based on client version
3. **Documentation**: Clearly communicate the changes and timeline

### Consumer Lifecycle Integration
The consumer status mapping ensures compatibility between textProc's consumer lifecycle management and the standardized status values, maintaining the existing functionality while presenting a consistent interface.
