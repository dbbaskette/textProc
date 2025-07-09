# Implementation Details

## Architecture Overview

### Event-Driven State Management

The application uses Spring's event publishing mechanism to decouple services and avoid circular dependencies.

#### ProcessingStateService
- **Purpose**: Manages the application's processing state (started/stopped)
- **Responsibilities**: 
  - Maintains atomic boolean state
  - Publishes events when state changes
  - Provides state inquiry methods

```java
@Service
public class ProcessingStateService {
    private final AtomicBoolean isProcessingEnabled = new AtomicBoolean(false);
    private final ApplicationEventPublisher eventPublisher;
    
    public void startProcessing() {
        isProcessingEnabled.set(true);
        eventPublisher.publishEvent(new ProcessingStartedEvent(this));
    }
    
    public void stopProcessing() {
        isProcessingEnabled.set(false);
        eventPublisher.publishEvent(new ProcessingStoppedEvent(this));
    }
}
```

#### ConsumerLifecycleService
- **Purpose**: Manages RabbitMQ consumer lifecycle
- **Responsibilities**:
  - Registers message listener containers
  - Pauses/resumes consumers based on processing state
  - Listens to processing state events

```java
@Service
public class ConsumerLifecycleService {
    @EventListener
    public void handleProcessingStarted(ProcessingStartedEvent event) {
        resumeConsumers();
    }
    
    @EventListener
    public void handleProcessingStopped(ProcessingStoppedEvent event) {
        pauseConsumers();
    }
}
```

### Benefits of Event-Driven Architecture

1. **Loose Coupling**: Services don't directly depend on each other
2. **Testability**: Each service can be tested independently
3. **Extensibility**: Easy to add new event listeners
4. **Single Responsibility**: Each service has a clear, focused purpose
5. **No Circular Dependencies**: Events flow in one direction

### Communication Flow

```
User Action (Start/Stop) 
  ↓
Controller calls ProcessingStateService
  ↓
ProcessingStateService publishes event
  ↓
ConsumerLifecycleService handles event
  ↓
RabbitMQ consumers are paused/resumed
```

## Stream Processing with State Management

### Processing State Check

The `ScdfStreamProcessor` now checks the processing state before processing any files:

```java
@Bean
public Function<Message<String>, Message<byte[]>> textProc() {
    return inputMsg -> {
        // Check if processing is enabled
        if (!processingStateService.isProcessingEnabled()) {
            logger.info("Processing is disabled, skipping message: {}", payload);
            return MessageBuilder.withPayload(new byte[0])
                    .copyHeaders(inputMsg.getHeaders())
                    .build();
        }
        
        // Process file only if enabled
        // ... rest of processing logic
    };
}
```

### Processing Flow

1. **Message Received**: RabbitMQ message arrives
2. **State Check**: Verify if processing is enabled
3. **Skip or Process**: 
   - If disabled: Return empty response, skip processing
   - If enabled: Continue with file processing
4. **Consumer Management**: Consumers remain active but processing is controlled

### Benefits of State Check

- **Prevents Unwanted Processing**: Files are not processed when disabled
- **Maintains Message Flow**: Messages are still received but skipped
- **UI Consistency**: Processing state matches actual behavior
- **Demo Control**: Perfect for controlled demonstrations

## Original Architecture (Deprecated)

The original design had circular dependencies:
- `ProcessingStateService` → `ConsumerLifecycleService`
- `ConsumerLifecycleService` → `ProcessingStateService`

This required complex injection patterns with `@Lazy` annotations and was difficult to test and maintain.

## Processing Controls Architecture

### Components

#### Web Layer
- **FileProcessingController**: REST endpoints for start/stop/reset operations
- **Templates**: Thymeleaf templates with JavaScript for real-time updates

#### Service Layer  
- **ProcessingStateService**: Central state management with event publishing
- **ConsumerLifecycleService**: RabbitMQ consumer management with event listening
- **HdfsService**: HDFS operations for reset functionality
- **FileProcessingService**: File tracking and statistics

#### Configuration Layer
- **ConsumerLifecycleConfig**: Auto-discovery and registration of message containers

### Data Flow

1. **User Interaction**: Clicks start/stop/reset button in UI
2. **AJAX Request**: JavaScript sends POST request to controller
3. **State Change**: Controller calls appropriate service method
4. **Event Publishing**: ProcessingStateService publishes event
5. **Event Handling**: ConsumerLifecycleService receives and handles event
6. **Consumer Management**: Containers are paused/resumed as needed
7. **Response**: Controller returns status JSON
8. **UI Update**: JavaScript updates UI based on response

### Stream Processing Integration

The stream processor integrates with the processing state system:

1. **Message Arrival**: RabbitMQ message received
2. **State Verification**: Check if processing is enabled
3. **Conditional Processing**: Only process if state allows
4. **Result Handling**: Return appropriate response based on state

This ensures that the processing controls work correctly in the SCDF environment. 