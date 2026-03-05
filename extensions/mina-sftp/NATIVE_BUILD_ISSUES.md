# Summary: camel-mina-sftp Native Image Build Issues

## Context
Goal: Enable GraalVM native image compilation for the camel-mina-sftp extension by moving it from `integration-tests-jvm/mina-sftp` to `integration-tests/mina-sftp`.

## Problems Encountered

### 1. Missing commons-net Classes (RESOLVED ✓)
**Issue:** Native build failed with missing FTP endpoint classes
```
Error: Classes that should be initialized at run time got initialized during image building:
  org.apache.camel.component.file.remote.FtpEndpoint
```

**Root Cause:**
- `camel-mina-sftp` depends on `camel-ftp` for base classes (RemoteFileEndpoint, etc.)
- `camel-ftp` excludes `commons-net` dependency
- GraalVM tried to analyze FTP/FTPS endpoint classes but `commons-net` was missing

**Solution Applied:**
- Added `commons-net.version` property to root `pom.xml`: `3.12.0`
- Added `commons-net` to `poms/build-parent/pom.xml` dependencyManagement
- Added `commons-net` dependency to `extensions/mina-sftp/runtime/pom.xml`
- Regenerated BOM with updated dependency management

**Files Modified:**
- `/pom.xml` - added commons-net.version property
- `/poms/build-parent/pom.xml` - added dependencyManagement entry
- `/extensions/mina-sftp/runtime/pom.xml` - added commons-net dependency

---

### 2. Component Auto-Discovery Conflicts (RESOLVED ✓)
**Issue:** Unwanted FTP/FTPS/SFTP components from camel-ftp were being auto-registered

**Root Cause:**
- `camel-ftp` includes component definitions for ftp://, ftps://, and sftp:// schemes
- These were being discovered and registered even though camel-mina-sftp provides its own mina-sftp:// component

**Solution Applied:**
- Used `CamelServiceFilterBuildItem` pattern (learned from xslt and disruptor extensions)
- Added build step to filter out ftp, ftps, and sftp components
- Marked FTP endpoint classes for runtime initialization

**Files Modified:**
- `/extensions/mina-sftp/deployment/src/main/java/org/apache/camel/quarkus/component/mina/sftp/deployment/MinaSftpProcessor.java`:
```java
@BuildStep
void filterFtpComponents(BuildProducer<CamelServiceFilterBuildItem> serviceFilter) {
    serviceFilter.produce(new CamelServiceFilterBuildItem(CamelServiceFilter.forComponent("ftp")));
    serviceFilter.produce(new CamelServiceFilterBuildItem(CamelServiceFilter.forComponent("ftps")));
    serviceFilter.produce(new CamelServiceFilterBuildItem(CamelServiceFilter.forComponent("sftp")));
}

@BuildStep
void runtimeInitializedClasses(BuildProducer<RuntimeInitializedClassBuildItem> runtimeInitializedClass) {
    runtimeInitializedClass.produce(new RuntimeInitializedClassBuildItem("org.apache.camel.component.file.remote.FtpEndpoint"));
    runtimeInitializedClass.produce(new RuntimeInitializedClassBuildItem("org.apache.camel.component.file.remote.FtpsEndpoint"));
    runtimeInitializedClass.produce(new RuntimeInitializedClassBuildItem("org.apache.camel.component.file.remote.SftpEndpoint"));
}
```

---

### 3. SftpFileSystemProvider Thread Creation (RESOLVED ✓)
**Issue:** Native build failed with "thread in image heap" error
```
Error: Detected a started Thread in the image heap. Threads running in the image generator are no longer running at image run time.
Object was reached by:
  ...SftpFileSystemProvider...
```

**Root Cause:**
- `org.apache.sshd.sftp.client.fs.SftpFileSystemProvider` is discovered via ServiceLoader at build time
- During instantiation, it creates `SshClient` instances with threads
- GraalVM doesn't allow threads to exist in the image heap

**Attempted Solutions (all failed):**
1. **RuntimeInitializedClassBuildItem** - Catch-22: Class marked for runtime init but instance already in heap
2. **Empty service file override** - ServiceLoader merges all files, didn't prevent discovery
3. **GraalVM substitution @RecomputeFieldValue** - Compilation errors and static modifier mismatches
4. **Excluding sshd-sftp entirely** - Broke functionality, MinaSftpOperations needs SftpClient$OpenMode

**Final Solution Applied:**
- Used GraalVM `@Delete` annotation to remove `SftpFileSystemProvider` from native image
- Safe because camel-mina-sftp uses `SftpClient` API directly, not the NIO FileSystemProvider API

**Files Created:**
- `/extensions/mina-sftp/runtime/src/main/java/org/apache/camel/quarkus/component/mina/sftp/graal/MinaSftpSubstitutions.java`:
```java
@TargetClass(className = "org.apache.sshd.sftp.client.fs.SftpFileSystemProvider")
@Delete
final class DeleteSftpFileSystemProvider {
}
```

**Files Modified:**
- `/extensions/mina-sftp/runtime/pom.xml` - added graalvm nativeimage dependency (provided scope)

---

### 4. Apache SSHD Version Mismatch (CURRENT BLOCKER ❌)
**Issue:** Native build fails with unresolved field error
```
Error: Discovered unresolved field during parsing: org.apache.sshd.core.CoreModuleProperties.HEARTBEAT_NO_REPLY_MAX
This error is reported at image build time because class org.apache.camel.component.file.remote.mina.MinaSftpOperations is registered for linking at image build time
```

**Root Cause - Version Incompatibility:**
```
Current dependency tree:
├── sshd-sftp: 2.17.1 (from camel-mina-sftp 4.18.0)
│   └── sshd-core: 2.12.1 (from Quarkus BOM via ${sshd.version})
│       └── sshd-common: 2.12.1
```

**The Problem:**
- `sshd-sftp 2.17.1` is compiled against `sshd-core 2.17.1` APIs
- Quarkus BOM 3.32.2 enforces `sshd-core 2.12.1` and `sshd-common 2.12.1`
- `CoreModuleProperties.HEARTBEAT_NO_REPLY_MAX` field exists in Apache SSHD 2.17.x but NOT in 2.12.x
- At native image build time, code compiled against 2.17.1 tries to access a field that doesn't exist in 2.12.1

**Verification:**
```bash
./mvnw dependency:tree -pl extensions/mina-sftp/runtime -Dincludes=org.apache.sshd:*
```
Shows:
- `sshd-sftp:2.17.1` (direct from camel-mina-sftp)
- `sshd-core:2.12.1` (managed by Quarkus BOM)

**Quarkus BOM 3.32.2 (latest) Contains:**
```xml
<dependency>
  <groupId>org.apache.sshd</groupId>
  <artifactId>sshd-common</artifactId>
  <version>2.12.1</version>
</dependency>
<dependency>
  <groupId>org.apache.sshd</groupId>
  <artifactId>sshd-core</artifactId>
  <version>2.12.1</version>
</dependency>
```
Note: `sshd-sftp` is NOT in Quarkus BOM

**Impact:**
- `camel-ssh` extension also uses `sshd-core 2.12.1` (from Quarkus BOM)
- Upgrading globally might affect camel-ssh

**Attempted Solutions:**
1. **Reflection registration** - Didn't help, field still unresolved
2. **Runtime initialization of MinaSftpOperations** - Ignored, class forced to build-time linking
3. **Excluding and re-adding sshd-sftp** - Caused Maven validation errors

**Potential Solutions (not yet implemented):**

**Option 1: Override sshd.version globally to 2.17.1**
```xml
<!-- pom.xml -->
<sshd.version>2.17.1</sshd.version><!-- @sync org.apache.camel:camel-mina-sftp:${camel.version} dep:org.apache.sshd:sshd-sftp -->
```
- ✓ Aligns all Apache SSHD libraries
- ✗ Breaks sync with Quarkus BOM
- ✗ May impact camel-ssh extension
- ⚠ Need to verify camel-ssh works with 2.17.1

**Option 2: Local dependency management override**
```xml
<!-- extensions/mina-sftp/pom.xml or parent -->
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>org.apache.sshd</groupId>
      <artifactId>sshd-core</artifactId>
      <version>2.17.1</version>
    </dependency>
    <dependency>
      <groupId>org.apache.sshd</groupId>
      <artifactId>sshd-common</artifactId>
      <version>2.17.1</version>
    </dependency>
  </dependencies>
</dependencyManagement>
```
- ✓ Localized to mina-sftp extension
- ✗ Creates version inconsistency in the project
- ✗ Still might conflict due to Maven dependency mediation

**Option 3: Wait for Quarkus BOM update**
- ✗ Not a short-term solution
- ? Unknown timeline for Quarkus to upgrade to SSHD 2.17.x

---

## Current Status

### Working:
- ✅ JVM mode tests pass
- ✅ Extension builds successfully
- ✅ Commons-net dependency properly managed
- ✅ Component filtering working correctly
- ✅ SftpFileSystemProvider excluded from native image

### Blocked:
- ❌ Native image build fails due to Apache SSHD version mismatch
- ❌ Cannot resolve `CoreModuleProperties.HEARTBEAT_NO_REPLY_MAX` field

### Next Steps Required:
1. Decide on approach for handling Apache SSHD version conflict
2. Verify chosen approach doesn't break camel-ssh extension
3. Test native image build after version alignment
4. Run full integration tests in native mode

---

## Files Modified Summary

**Configuration:**
- `/pom.xml` - Added commons-net.version property
- `/poms/build-parent/pom.xml` - Added commons-net dependencyManagement
- `/poms/bom/pom.xml` - Auto-regenerated with commons-net

**Extension Code:**
- `/extensions/mina-sftp/runtime/pom.xml` - Added commons-net and graalvm dependencies
- `/extensions/mina-sftp/deployment/src/main/java/org/apache/camel/quarkus/component/mina/sftp/deployment/MinaSftpProcessor.java` - Added component filtering and runtime initialization
- `/extensions/mina-sftp/runtime/src/main/java/org/apache/camel/quarkus/component/mina/sftp/graal/MinaSftpSubstitutions.java` - Created with @Delete for SftpFileSystemProvider

**Test Infrastructure:**
- Integration tests already set up (from previous session)
- `MinaSftpIT.java` extends `MinaSftpTest.java` with `@QuarkusIntegrationTest`

---

## Debugging Commands

**Check dependency tree for SSHD libraries:**
```bash
./mvnw dependency:tree -pl extensions/mina-sftp/runtime -Dincludes=org.apache.sshd:*
```

**Build extension only:**
```bash
./mvnw clean install -pl extensions/mina-sftp -am -Dquickly
```

**Run JVM tests:**
```bash
./mvnw clean verify -pl integration-tests/mina-sftp
```

**Run native tests:**
```bash
./mvnw clean verify -pl integration-tests/mina-sftp -Dnative -Ddocker
```

**Check what Camel expects:**
```bash
grep -A5 -B5 "sshd" ~/.m2/repository/org/apache/camel/camel-mina-sftp/*/camel-mina-sftp-*.pom
```
