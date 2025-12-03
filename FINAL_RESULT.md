✅ Final Results

  JVM Mode: 31/31 tests passing ✓
  - KeycloakConsumerTest: 10/10
  - KeycloakTest: 21/21

  Native Mode: 31/31 tests passing ✓
  - KeycloakConsumerIT: 10/10
  - KeycloakIT: 21/21

  📝 Summary of Changes

  1. Extension Configuration (extensions/keycloak/)

  Runtime Dependencies (runtime/pom.xml):
  - Added httpcore-nio and httpasyncclient (with commons-logging exclusion)
  - Added quarkus-resteasy-client-jackson for native-compatible Jackson serialization

  Deployment Dependencies (deployment/pom.xml):
  - Added quarkus-resteasy-client-jackson-deployment

  2. Native Mode Support (deployment/src/main/java/.../KeycloakProcessor.java):

  - Registered RESTEasy client classes for reflection
  - Registered Keycloak admin client and Jackson classes for reflection
  - Registered Keycloak representation classes for reflection
  - Registered custom serializer for reflection
  - Added runtime initialization for ApacheHttpAsyncClient4Engine and SecretGenerator
  - Added recorder methods for static/runtime init phases

  3. Custom Jackson Provider (runtime/src/main/java/.../KeycloakRecorder.java):

  - Created KeycloakRecorder with custom ResteasyClientProvider
  - Implemented AppJsonQuarkusJacksonSerializer extending QuarkusJacksonSerializer
  - Configured ObjectMapper to ignore unknown properties (FAIL_ON_UNKNOWN_PROPERTIES = false)
  - Set client provider to null at static init and to custom provider at runtime init

  4. Test Configuration (integration-tests/keycloak/src/test/java/):

  - Configured REST-assured's ObjectMapper in both test classes to ignore unknown properties
  - This ensures tests can deserialize Keycloak responses that contain newer fields than the client knows about

  🎯 Key Technical Insights

  1. The ClientBuilderWrapper Problem: The default Keycloak ClientBuilderWrapper doesn't work in native mode. Solution: custom ResteasyClientProvider using QuarkusJacksonSerializer.
  2. REST-assured vs Keycloak Client: The initial test failures were caused by REST-assured (the testing library) using its own Jackson deserializer, not the Keycloak client's. Both needed
  configuration.
  3. Pattern from Quarkus: The solution follows the exact pattern from Quarkus's own quarkus-keycloak-admin-resteasy-client extension.

  The camel-quarkus-keycloak extension now has full native mode support with comprehensive test coverage! 🚀

