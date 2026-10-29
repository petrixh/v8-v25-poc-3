# hawkBit User Interface

The hawkBit user interface is based on the Vaadin Flow framework (Vaadin 25, free core components) with Spring Boot, and allows to manage software updates and large scale roll-outs via a user interface. All UI logic runs on the server.

This module holds the views and components. The applications that serve the UI are `hawkbit-runtime/hawkbit-vv8-ui` (the UI on its own, together with `hawkbit-mgmt-server`) and `hawkbit-runtime/hawkbit-update-server` (all in one).

## Build

The build needs Java 21. No client-side code is compiled: there is no GWT widgetset and no Sass theme.

The two runtimes run Vaadin's frontend build (`vaadin-maven-plugin`, goals `prepare-frontend` and `build-frontend`) when they are packaged. As long as the UI uses only Vaadin's own components and no custom frontend files, Vaadin uses its pre-built production bundle (the build log says "A production mode bundle build is not needed") and needs neither Node.js nor npm. When a bundle has to be built (custom frontend files, add-ons with their own client-side code), the plugin downloads Node.js from `nodejs.org` and the npm packages from `registry.npmjs.org`.

## Theme

The UI uses the Lumo theme with its compact preset, and hawkBit's settings on top in `src/main/resources/META-INF/resources/VAADIN/hawkbit/hawkbit.css`. The application's app shell class loads them with `@StyleSheet`. How to change the look: [Custom Theme guide](../docs/content/guides/customtheme.md).

## Production and development mode

The UI runs in production mode: `src/main/resources/hawkbit-ui-defaults.properties` sets `vaadin.productionMode=true`, and the log of a starting runtime says "Vaadin is running in production mode."

Development mode (Vaadin's development tools in the browser) is for working on the UI from an IDE. It needs three things the packaged application does not have:

- The development dependencies. Add `com.vaadin:vaadin-dev` (version from the Vaadin BOM) to the runtime you start, while you develop; do not ship it in a production build.
- A class path Vaadin can scan. Start the runtime's main class (`org.eclipse.hawkbit.app.vv8ui.Vaadin8UIStart` or `org.eclipse.hawkbit.app.Start`) from the IDE, on the module's classes and dependency jars, with the module directory as working directory. Development mode does not work from the packaged Spring Boot jar: Vaadin does not find the frontend resources inside its nested jars, and it needs a Maven project directory as working directory.
- `-Dvaadin.productionMode=false`.

Vaadin then uses its pre-compiled development bundle: no Node.js, no Vite server. The development tools answer only the local machine. When the browser reaches the UI from another address (through a container network or a proxy), allow that address, for example `-Dvaadin.devmode.hostsAllowed=172.21.0.*`; otherwise the tools say "Dev tools functionality denied for this host".

## Debugging

Debug the UI with a Java debugger attached to the runtime: views, components and their event handlers run on the server. The browser's developer tools show Vaadin's web components and their styles. GWT SuperDevMode does not apply any more.

## Tests

The UI tests run with the module build, for example with the runtime and the modules it depends on:

```bash
mvn -pl hawkbit-runtime/hawkbit-vv8-ui -am verify
```

How CI runs them, with RabbitMQ for the DMF integration tests: [.github/CI.md](../.github/CI.md).
