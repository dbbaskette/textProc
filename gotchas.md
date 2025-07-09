# Gotchas and Solutions

This document tracks issues encountered during development and their solutions.

## Processing State Issues

### Problem: Files Processed Even When Disabled
**Issue**: Application processes files even when processing state is set to STOPPED

**Root Cause**: The `ScdfStreamProcessor` was not checking the processing state before processing files

**Solution**: Added processing state check at the beginning of the `textProc()` function
```java
// Check if processing is enabled
if (!processingStateService.isProcessingEnabled()) {
    logger.info("Processing is disabled, skipping message: {}", payload);
    return MessageBuilder.withPayload(new byte[0])
            .copyHeaders(inputMsg.getHeaders())
            .build();
}
```

**Result**: 
- Files are now only processed when processing is enabled
- When disabled, messages are received but skipped with empty response
- UI state now correctly reflects actual processing behavior
- Consumer lifecycle management works as expected

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