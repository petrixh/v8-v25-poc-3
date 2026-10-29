---
title: Custom Theme
parent: Guides
weight: 34
---

This guide describes how to change the visual look of the Eclipse hawkBit Management UI. The Management UI is built with Vaadin Flow (Vaadin 25) and styled with plain CSS: the Lumo theme, its compact preset, and hawkBit's own style sheet on top. A custom look is one more style sheet, loaded after hawkBit's.
<!--more-->

{{% note %}}
Earlier versions of the Management UI (Vaadin 8) used a Sass theme in `src/main/resources/VAADIN/themes/<theme>/`, configured through the variables of `hawkbitvariables.scss`. That mechanism no longer applies: the UI does not read Sass themes, `styles.scss` or theme variables, and the custom theme example of the hawkBit examples project, which is such a Sass theme, does not work with this version.
{{% /note %}}

## Overview

The application's app shell class (the class that implements Vaadin's `AppShellConfigurator`) loads the style sheets of the UI, in this order:

```java
@StyleSheet(Lumo.STYLESHEET)               // the Lumo theme
@StyleSheet(Lumo.COMPACT_STYLESHEET)       // Lumo's compact preset: smaller text and controls
@StyleSheet("VAADIN/hawkbit/hawkbit.css")  // hawkBit's settings
public class Vaadin8UIStart implements AppShellConfigurator {
```

In the hawkBit runtimes the app shell class is the application's main class: `org.eclipse.hawkbit.app.vv8ui.Vaadin8UIStart` (`hawkbit-vv8-ui`) and `org.eclipse.hawkbit.app.Start` (`hawkbit-update-server`).

`hawkbit.css` is part of the `hawkbit-ui` module, in `src/main/resources/META-INF/resources/VAADIN/hawkbit/`, with its images in the `images` folder next to it. It holds:

- global values as Lumo custom properties on `html`: primary, error and success colours, font family, the application background;
- rules for hawkBit's own class names: the menu (`.valo-menu`), the header band with the logo and the view title (`.main-content > .view-header`, `.view-header-layout`), grids, panels and their headers (`.widget-header`, `.header-caption`), the login page (`.login-panel`), detail tabs, status icons and bars.

The file is commented section by section; use it as the reference for the class names. A style sheet loaded later wins over an earlier one for rules of the same specificity, so a custom style sheet only needs the rules it changes.

The style sheets are served as they are: there is no theme compilation, and adding one does not need Node.js or a frontend build.

## Procedure to create a custom look

- Create the style sheet in the resources of your application (a hawkBit runtime, or your own Spring Boot application built with the hawkBit starters), under `src/main/resources/META-INF/resources/`, for example **`src/main/resources/META-INF/resources/VAADIN/mytheme/mytheme.css`**. Put its images in a sub folder, for example **`VAADIN/mytheme/images/`**.
- Add it to the application's app shell class, after hawkBit's style sheet. An application has one app shell class; in your own application it is the class that carries these annotations instead of the runtime's main class:

  ```java
  @StyleSheet(Lumo.STYLESHEET)
  @StyleSheet(Lumo.COMPACT_STYLESHEET)
  @StyleSheet("VAADIN/hawkbit/hawkbit.css")
  @StyleSheet("VAADIN/mytheme/mytheme.css")
  public class MyApplication implements AppShellConfigurator {
  ```

- In the style sheet, override what you want to change. Global values are Lumo custom properties; hawkBit's rules use its class names. For example:

  ```css
  html {
      --lumo-primary-color: #00629e;
      --lumo-primary-color-50pct: rgba(0, 98, 158, 0.5);
      --lumo-primary-color-10pct: rgba(0, 98, 158, 0.1);
      --lumo-primary-text-color: #00629e;
      --lumo-font-family: "Roboto", sans-serif;
  }

  /* the logo in the header band, top right of every view and of the login page */
  .main-content > .view-header {
      background-image: url("images/my_logo.png");
  }

  /* the menu */
  .valo-menu {
      background-color: #1f2d3d;
      color: #c0c8d0;
  }
  ```

  A relative `url(...)` is resolved against the address of the style sheet that contains it.
- hawkBit's primary colour (`#551f62`) is set in two ways: as the Lumo custom properties above, and directly in several rules of `hawkbit.css` (view titles, the selected grid row, panel headers, detail tabs, links under the mouse, the rollout groups chart, the running status icon). To change it everywhere, search `hawkbit.css` for the colour and override those rules too. Colours that come from data, such as tag and type colours, are not part of the style sheets.
- Build and start the application, and check the views and the login page.

## Procedure to add a custom footer

The login page shows the footer from the class path resource **`META-INF/resources/VAADIN/hawkbit/footer.html`**, the constant `AbstractHawkbitLoginUI.FOOTER_RESOURCE`. The default is in the `hawkbit-ui` module, in `src/main/resources/META-INF/resources/VAADIN/hawkbit/footer.html`, next to `hawkbit.css`. The folder name `hawkbit` is fixed.

{{% note %}}
Earlier versions of the Management UI (Vaadin 8) read the footer from `VAADIN/themes/hawkbit/layouts/footer.html`. A footer at that path is no longer read: move it to the path above.
{{% /note %}}

- To replace the footer, create **`src/main/resources/META-INF/resources/VAADIN/hawkbit/footer.html`** in your application, starting from the default file. The login page uses the first file of that name on the class path, and the application's own resources come before the library jars. The file is also served as a static resource at `/VAADIN/hawkbit/footer.html`, like `hawkbit.css`.
- The file is inserted into the login page as HTML, inside one `<div>`.
