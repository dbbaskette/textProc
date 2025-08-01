# Gotchas and Solutions

This document tracks issues encountered during development and their solutions.

## Processing State Issues

### Problem: Messages Consumed and Lost When Processing Disabled
**Issue**: Application consumed messages from queue even when processing state was STOPPED, causing messages to be lost permanently when processing was re-enabled

**Root Cause**: Two conflicting mechanisms were used:
1. `ConsumerLifecycleService` tried to pause traditional `SimpleMessageListenerContainer`
2. `ScdfStreamProcessor` had a processing state check that consumed but skipped messages

Spring Cloud Function doesn't use `SimpleMessageListenerContainer`, so the lifecycle service wasn't actually pausing anything. Messages continued flowing to the function where they were consumed and acknowledged even when disabled.

**Solution**: 
1. **Removed processing state check from ScdfStreamProcessor function** - This was consuming messages
2. **Updated ConsumerLifecycleService to use Spring Cloud Stream's BindingsEndpoint** - This properly controls binding lifecycle
3. **Added spring-boot-actuator bindings endpoint** - Required for BindingsEndpoint functionality

```java
// NEW: Use BindingsEndpoint to control binding state
private void changeBindingState(String stateName) {
    Class<?> stateClass = Class.forName("org.springframework.cloud.stream.endpoint.BindingsEndpoint$State");
    Object stateValue = // ... find enum constant
    Method changeStateMethod = bindingsEndpoint.getClass().getMethod("changeState", String.class, stateClass);
    changeStateMethod.invoke(bindingsEndpoint, BINDING_NAME, stateValue);
}
```

**Result**: 
- When disabled: **Messages remain in the queue** and are not consumed
- When enabled: Processing resumes and processes queued messages
- No message loss during disable/enable cycles
- True pause/resume functionality as documented

## Architecture Improvements

### Problem: Circular Dependency Between Services
**Issue**: Initial design had `ProcessingStateService` and `ConsumerLifecycleService` dependent on each other

**Root Cause**: Tight coupling between services that needed to communicate state changes

**Solution**: Implemented event-driven architecture using Spring ApplicationEventPublisher
- `ProcessingStateService` publishes events when state changes
- `ConsumerLifecycleService` listens to these events using `@EventListener`
- No direct dependencies between services
- Loose coupling and better separation of concerns

**Benefits**:
- Eliminates circular dependency
- Easier to test individual services
- Can add more listeners without modifying existing services
- Better follows Single Responsibility Principle

### Event Classes:
- `ProcessingStartedEvent` - Published when processing is enabled
- `ProcessingStoppedEvent` - Published when processing is disabled

## RabbitMQ Connection Issues

### Problem: RabbitMQ Connection Refused Locally
**Issue**: Application fails to start locally with SCDF profile due to RabbitMQ connection errors

**Root Cause**: Application tries to connect to `localhost:5672` but no RabbitMQ server is running locally

**Solution**: 
For Cloud Foundry deployment, RabbitMQ configuration comes from service binding automatically.

**For Local Development**:
1. **Option 1**: Use `standalone` profile (no RabbitMQ needed)
   ```bash
   mvn spring-boot:run -Dspring-boot.run.profiles=standalone
   ```

2. **Option 2**: Start RabbitMQ locally
   ```bash
   docker run -d --name rabbitmq -p 5672:5672 -p 15672:15672 rabbitmq:3.12-management
   ```

**Key Learning**: Don't add explicit RabbitMQ configuration for SCDF apps - let Cloud Foundry service binding handle it

## Processing State Management

### Initial Design Issues
- Consumer management was tightly coupled to state management
- Hard to test and maintain
- Circular dependencies

### Improved Design
- **Event-driven**: State changes trigger events
- **Separation of Concerns**: Each service has a single responsibility
- **Testable**: Services can be tested independently
- **Extensible**: Easy to add new listeners

## Best Practices Learned

1. **Use Events for Loose Coupling**: When services need to communicate state changes
2. **Avoid Circular Dependencies**: If you need them, reconsider the design
3. **Single Responsibility**: Each service should have one clear purpose
4. **Let CF Handle Service Binding**: Don't override with explicit configuration
5. **Start with Simple**: Complex injection patterns usually indicate design issues
6. **Check State Early**: Always verify processing state before expensive operations 