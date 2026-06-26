# Add Route Diagram Dev UI Page (Issue #8624)

## Context

 Issue #8624 requests a visual route diagram page in the Quarkus Dev Console. 
 
 Camel 4.21 introduces camel-diagram with a DiagramDevConsole (console ID "route-diagram") that returns
 base64-encoded PNG images as JSON. Following the established pattern (per jamesnetherton's comment), we wrap this console in a QwcCamelCore-extending web component — the same approach used for
 Routes, Context, Events, etc.

## Approach

 Add the diagram page directly to extensions-core/core — matching where all other DevConsole-based pages live. No new extension module needed. The existing CamelCoreDevUIService already handles
 any console ID dynamically via Camel's DevConsoleRegistry.

## Changes

 1. Register the page — CamelCoreDevConsoleProcessor.java

 File: extensions-core/core/deployment/src/main/java/org/apache/camel/quarkus/core/deployment/devui/CamelCoreDevConsoleProcessor.java

 Add a new .addPage() call in createDevUICards():

```
 cardPageBuildItem.addPage(Page.webComponentPageBuilder()
         .title("Route Diagram")
         .icon("font-awesome-solid:diagram-project")
         .componentLink("qwc-camel-core-diagram.js"));

             .icon("font-awesome-solid:diagram-project")
             .componentLink("qwc-camel-core-diagram.js"));
```

2. Create the web component — qwc-camel-core-diagram.js

File: extensions-core/core/deployment/src/main/resources/dev-ui/qwc-camel-core-diagram.js

- Extend QwcCamelCore with console ID "route-diagram"
- DiagramDevConsole.doCallJson() returns {"image": "base64..."} or {"text": "..."}
- Render the base64 image as an inline <img src="data:image/png;base64,...">
- Add controls for:
     - Mode: route / topology (via putOption('mode', ...))
     - Theme: dark / light / transparent (via putOption('theme', ...))
     - Filter: route ID filter text field (via putOption('filter', ...))
- Handle empty data gracefully with "No data available" message

3. Add camel-diagram dependency to core runtime

File: extensions-core/core/runtime/pom.xml

Add:
```
<dependency>
     <groupId>org.apache.camel</groupId>
     <artifactId>camel-diagram</artifactId>
</dependency>
```

This puts DiagramDevConsole on the classpath so it auto-registers in the DevConsoleRegistry.

## Verification

1. Build core extension: mvn clean install -pl extensions-core/core/runtime,extensions-core/core/deployment -DskipTests
2. Run the test app at ~/work/sources/WORKSPACE/camel-dev-console-test with mvn quarkus:dev, pointing to the SNAPSHOT version
3. Open http://localhost:8080/q/dev-ui/ → Camel card → "Route Diagram" page
4. Verify diagram image renders for the 5 test routes
5. Test mode/theme/filter controls

