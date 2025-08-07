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

### Binding Lifecycle Management

The `ConsumerLifecycleService` now uses Spring Cloud Stream's `BindingsEndpoint` to properly control function binding lifecycle:

```java
@Service
public class ConsumerLifecycleService {
    private static final String BINDING_NAME = "textProc-in-0";
    
    @Autowired(required = false)
    private BindingsEndpoint bindingsEndpoint;
    
    @EventListener
    public void handleProcessingStarted(ProcessingStartedEvent event) {
        changeBindingState("STARTED");
    }
    
    @EventListener
    public void handleProcessingStopped(ProcessingStoppedEvent event) {
        changeBindingState("STOPPED");
    }
    
    private void changeBindingState(String stateName) {
        // Uses reflection to access private State enum
        Class<?> stateClass = Class.forName("org.springframework.cloud.stream.endpoint.BindingsEndpoint$State");
        // ... reflection code to invoke changeState method
    }
}
```

### Processing Flow

1. **Processing State Change**: User clicks start/stop in UI
2. **Event Published**: ProcessingStateService publishes event
3. **Binding Control**: ConsumerLifecycleService changes binding state via BindingsEndpoint
4. **Queue Behavior**: 
   - If disabled: **Messages remain in queue**, no consumption occurs
   - If enabled: Binding resumes, processes queued messages

### Benefits of Binding Control

- **True Pause/Resume**: Messages stay in queue when disabled
- **No Message Loss**: All messages preserved during disable/enable cycles
- **Proper Queue Management**: Leverages Spring Cloud Stream's built-in lifecycle
- **Actuator Integration**: Can be controlled via REST endpoints if needed

## Original Architecture (Deprecated)

The original design had circular dependencies:
- `ProcessingStateService` → `ConsumerLifecycleService`
- `ConsumerLifecycleService` → `ProcessingStateService`

This required complex injection patterns with `@Lazy` annotations and was difficult to test and maintain.

## Processing Controls Architecture

### Components

#### Web Layer
- **ProcessingApiController**: REST endpoints for start/stop/reset/state and files listing
- UI removed; headless API designed for external orchestrator

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