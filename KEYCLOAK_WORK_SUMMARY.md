# Keycloak Extension - Native Mode Support Implementation

## Date: 2025-12-03

## Overview
Successfully added comprehensive test coverage and full native mode support for the camel-quarkus-keycloak extension.

---

## Test Coverage Addition

### Integration Tests Created

#### 1. KeycloakTest.java (21 tests)
**Location**: `integration-tests/keycloak/src/test/java/org/apache/camel/quarkus/component/keycloak/it/KeycloakTest.java`

Tests for Keycloak producer operations:
- Component loading
- Realm operations (create with headers, create with POJO, get)
- User operations (create with headers/POJO, get, list, delete, non-existent user)
- Role operations (create with headers/POJO, get, list, delete, non-existent role)
- User-role assignments (assign, remove)
- Cleanup operations

#### 2. KeycloakConsumerTest.java (10 tests)
**Location**: `integration-tests/keycloak/src/test/java/org/apache/camel/quarkus/component/keycloak/it/KeycloakConsumerTest.java`

Tests for Keycloak consumer operations:
- Setup (create realm, enable events, create consumer routes)
- Admin events consumption (user creation, role creation)
- Regular events consumption (user login)
- Route lifecycle (start, stop)
- Cleanup operations

#### 3. KeycloakResource.java
**Location**: `integration-tests/keycloak/src/main/java/org/apache/camel/quarkus/component/keycloak/it/KeycloakResource.java`

REST endpoints for testing all Keycloak operations including:
- Realm CRUD
- User CRUD (with user ID lookup helper)
- Role CRUD
- User-role assignments
- Event consumption with dynamic route creation
- Admin events configuration

#### 4. KeycloakTestResource.java
**Location**: `integration-tests/keycloak/src/test/java/org/apache/camel/quarkus/component/keycloak/it/KeycloakTestResource.java`

QuarkusTestResource that:
- Starts Keycloak testcontainer (version 26.4.5)
- Configures admin credentials
- Provides server URL to tests

### Test Dependencies Added
**File**: `integration-tests/keycloak/pom.xml`
- `camel-mock` - for mock endpoints
- `awaitility` - for async event consumption testing
- `jackson-databind` - for JSON deserialization in tests

---

## Native Mode Support Implementation

### Problem Discovered
The default Keycloak `ClientBuilderWrapper` doesn't work in native mode because:
1. It uses reflection in ways incompatible with GraalVM
2. The default Jackson ObjectMapper doesn't handle unknown properties correctly
3. Various Keycloak classes need explicit reflection registration

### Solution Pattern
Followed the exact approach from Quarkus's own `quarkus-keycloak-admin-resteasy-client` extension.

### Changes Made

#### 1. Extension Runtime Dependencies
**File**: `extensions/keycloak/runtime/pom.xml`

Added dependencies:
```xml
<!-- Required for Apache HTTP Async Client engine used by RESTEasy -->
<dependency>
    <groupId>org.apache.httpcomponents</groupId>
    <artifactId>httpcore-nio</artifactId>
</dependency>
<dependency>
    <groupId>org.apache.httpcomponents</groupId>
    <artifactId>httpasyncclient</artifactId>
    <exclusions>
        <exclusion>
            <groupId>commons-logging</groupId>
            <artifactId>commons-logging</artifactId>
        </exclusion>
    </exclusions>
</dependency>
<!-- Required for QuarkusJacksonSerializer that works in native mode -->
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-resteasy-client-jackson</artifactId>
</dependency>
```

#### 2. Extension Deployment Dependencies
**File**: `extensions/keycloak/deployment/pom.xml`

Added:
```xml
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-resteasy-client-jackson-deployment</artifactId>
</dependency>
```

#### 3. KeycloakRecorder (NEW FILE)
**File**: `extensions/keycloak/runtime/src/main/java/org/apache/camel/quarkus/component/keycloak/KeycloakRecorder.java`

Created runtime recorder with:
- `avoidRuntimeInitIssueInClientBuilderWrapper()` - sets client provider to null during static init
- `setNativeModeClientProvider()` - sets custom ResteasyClientProvider at runtime
- `AppJsonQuarkusJacksonSerializer` - custom Jackson serializer that:
  - Extends `QuarkusJacksonSerializer` (native-compatible)
  - Configures `ObjectMapper` with:
    - `setSerializationInclusion(JsonInclude.Include.NON_NULL)`
    - `configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)`

#### 4. KeycloakProcessor Updates
**File**: `extensions/keycloak/deployment/src/main/java/org/apache/camel/quarkus/component/keycloak/deployment/KeycloakProcessor.java`

Added reflection registrations for:
- **RESTEasy client classes**:
  - `ResteasyClientBuilderImpl`
  - `ClientConfiguration`
  - `ProxyBuilderImpl`
  - `ResteasyClient`

- **Keycloak admin client and Jackson classes**:
  - `JacksonProvider`
  - `ResteasyJackson2Provider`
  - `StringListMapDeserializer`
  - `StringOrArrayDeserializer`
  - `StringOrArraySerializer`
  - `MultivaluedHashMap`

- **Custom serializer**:
  - `KeycloakRecorder$AppJsonQuarkusJacksonSerializer`

- **Keycloak representation classes**:
  - `RealmRepresentation`
  - `UserRepresentation`
  - `RoleRepresentation`
  - `CredentialRepresentation`
  - `ClientRepresentation`
  - `RoleRepresentation$Composites`
  - `RealmEventsConfigRepresentation`
  - `AdminEventRepresentation`
  - `EventRepresentation`
  - `AuthDetailsRepresentation`
  - `ErrorRepresentation`

Added runtime initialization for:
- `ApacheHttpAsyncClient4Engine` - optional RESTEasy engine
- `SecretGenerator` - contains SecureRandom (must not be cached at build time)

Added @Record methods:
- `@Record(ExecutionTime.STATIC_INIT)` - calls `avoidRuntimeInitIssueInClientBuilderWrapper()`
- `@Record(ExecutionTime.RUNTIME_INIT)` - calls `setNativeModeClientProvider()`

#### 5. Test Configuration for Native Mode
**Files**:
- `integration-tests/keycloak/src/test/java/org/apache/camel/quarkus/component/keycloak/it/KeycloakTest.java`
- `integration-tests/keycloak/src/test/java/org/apache/camel/quarkus/component/keycloak/it/KeycloakConsumerTest.java`

Added `@BeforeAll` method to configure REST-assured:
```java
@BeforeAll
public static void configureRestAssured() {
    // Configure REST-assured to ignore unknown properties when deserializing
    // This is needed because the Keycloak server may return newer fields
    // that the client representation classes don't know about
    RestAssured.config = RestAssuredConfig.config().objectMapperConfig(
            ObjectMapperConfig.objectMapperConfig().jackson2ObjectMapperFactory(
                    (cls, charset) -> {
                        ObjectMapper mapper = new ObjectMapper();
                        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
                        return mapper;
                    }));
}
```

---

## Build and Test Results

### JVM Mode
**Command**: `mvn clean test`
**Result**: ✅ **31/31 tests passing**
- KeycloakConsumerTest: 10/10
- KeycloakTest: 21/21

### Native Mode
**Command**: `mvn clean verify -Pnative`
**Result**: ✅ **31/31 tests passing**
- KeycloakConsumerIT: 10/10
- KeycloakIT: 21/21

---

## Technical Insights

### Key Problem #1: ClientBuilderWrapper Incompatibility
**Issue**: Default `org.keycloak.admin.client.ClientBuilderWrapper` doesn't work in native mode

**Solution**:
- Set client provider to null during static init
- Replace with custom `ResteasyClientProvider` at runtime
- Use `QuarkusJacksonSerializer` instead of standard Jackson serialization

### Key Problem #2: Unknown Properties
**Issue**: Keycloak server returns fields that may not exist in client representation classes (version mismatch)

**Solution**:
- Configure `ObjectMapper` with `FAIL_ON_UNKNOWN_PROPERTIES = false`
- Apply to both Keycloak client (via custom serializer) and REST-assured (in tests)

### Key Problem #3: Reflection Registration
**Issue**: GraalVM requires explicit registration of classes accessed via reflection

**Solution**:
- Register all RESTEasy client classes
- Register all Keycloak representation classes
- Register Jackson serializers and deserializers
- Use archive markers for automatic indexing

### Key Problem #4: Runtime Initialization
**Issue**: Some classes contain state that must not be cached at build time

**Solution**:
- `SecretGenerator` contains `SecureRandom` - must initialize at runtime
- `ApacheHttpAsyncClient4Engine` - optional engine, initialize at runtime

---

## Files Modified/Created

### Created:
1. `extensions/keycloak/runtime/src/main/java/org/apache/camel/quarkus/component/keycloak/KeycloakRecorder.java`
2. `integration-tests/keycloak/src/main/java/org/apache/camel/quarkus/component/keycloak/it/KeycloakResource.java`
3. `integration-tests/keycloak/src/test/java/org/apache/camel/quarkus/component/keycloak/it/KeycloakTest.java`
4. `integration-tests/keycloak/src/test/java/org/apache/camel/quarkus/component/keycloak/it/KeycloakConsumerTest.java`
5. `integration-tests/keycloak/src/test/java/org/apache/camel/quarkus/component/keycloak/it/KeycloakTestResource.java`

### Modified:
1. `extensions/keycloak/runtime/pom.xml` - added dependencies
2. `extensions/keycloak/deployment/pom.xml` - added deployment dependency
3. `extensions/keycloak/deployment/src/main/java/org/apache/camel/quarkus/component/keycloak/deployment/KeycloakProcessor.java` - added reflection registrations and recorder methods
4. `integration-tests/keycloak/pom.xml` - added test dependencies and native profile configuration

---

## Reference Material

### Inspiration Source
Pattern copied from Quarkus's official keycloak-admin-resteasy-client extension:
- **Location**: `/home/gfournie/work/ai/quarkus/extensions/keycloak-admin-resteasy-client/`
- **Key files**:
  - `deployment/src/main/java/io/quarkus/keycloak/admin/resteasy/client/deployment/KeycloakAdminResteasyClientProcessor.java`
  - `runtime/src/main/java/io/quarkus/keycloak/admin/resteasy/client/runtime/KeycloakAdminResteasyClientRecorder.java`

### Documentation
- Quarkus Native Image Guide: https://quarkus.io/guides/native-reference
- Keycloak Admin Client: https://www.keycloak.org/docs/latest/server_development/#example-using-java

---

## Next Steps (Optional)

### Potential Improvements:
1. **Performance**: Consider adding `@RegisterForReflection` annotations directly on classes instead of programmatic registration
2. **Coverage**: Add tests for additional Keycloak operations (groups, clients, identity providers)
3. **Documentation**: Update extension documentation to note native mode support
4. **Version Matrix**: Test with different Keycloak server versions

### Property Configuration:
The extension metadata in `runtime/pom.xml` shows:
```xml
<camel.quarkus.jvmSince>3.29.0</camel.quarkus.jvmSince>
<camel.quarkus.nativeSince>3.31.0</camel.quarkus.nativeSince>
```

This correctly indicates:
- JVM support since: 3.29.0
- Native support since: 3.31.0 (current version)

---

## Commands for Verification

### Test JVM Mode:
```bash
cd /home/gfournie/work/ai/camel-quarkus/integration-tests/keycloak
mvn clean test
```

### Test Native Mode:
```bash
cd /home/gfournie/work/ai/camel-quarkus/integration-tests/keycloak
mvn clean verify -Pnative
```

### Build Extension:
```bash
cd /home/gfournie/work/ai/camel-quarkus/extensions/keycloak
mvn clean install -DskipTests -Dquickly
```

---

## Success Metrics

✅ All 31 tests pass in JVM mode
✅ All 31 tests pass in native mode
✅ Native image builds successfully
✅ No reflection warnings during native compilation
✅ Keycloak admin client works correctly in native mode
✅ Event consumption works in native mode

**Status**: COMPLETE - Ready for code review and merge
