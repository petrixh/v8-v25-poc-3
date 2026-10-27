/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hawkbit.ui.tenantconfiguration;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.hawkbit.repository.TenantConfigurationManagement;
import org.eclipse.hawkbit.repository.model.TenantConfigurationValue;
import org.eclipse.hawkbit.ui.common.data.proxies.ProxySystemConfigWindow;
import org.springframework.beans.factory.InitializingBean;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Composite;
import com.vaadin.flow.component.HasSize;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.data.binder.Binder;

/**
 * Base class for all configuration views. This class implements the logic for
 * the handling of the configurations in a consistent way.
 * 
 */
public abstract class BaseConfigurationView<B extends ProxySystemConfigWindow> extends Composite<Div>
        implements ConfigurationGroup, InitializingBean, HasSize {

    private static final long serialVersionUID = 1L;

    private final List<ConfigurationItemChangeListener> configurationChangeListeners = new ArrayList<>();
    private final transient TenantConfigurationManagement tenantConfigurationManagement;
    private final Binder<B> binder;

    protected BaseConfigurationView(final TenantConfigurationManagement tenantConfigurationManagement) {
        this.tenantConfigurationManagement = tenantConfigurationManagement;
        binder = new Binder<>();
    }

    @Override
    public void afterPropertiesSet() {
        binder.setBean(populateSystemConfig());
    }

    protected abstract B populateSystemConfig();

    @Override
    public void addChangeListener(final ConfigurationItemChangeListener listener) {
        configurationChangeListeners.add(listener);
    }

    @Override
    public boolean isUserInputValid() {
        // default return value is true, because often user can only choose from
        // different valid options.
        return true;
    }

    @Override
    public void undo() {
        binder.setBean(populateSystemConfig());
    }

    protected boolean readConfigOption(final String configurationKey) {
        final TenantConfigurationValue<Boolean> enabled = tenantConfigurationManagement
                .getConfigurationValue(configurationKey, Boolean.class);

        return enabled.getValue() && !enabled.isGlobal();
    }

    protected <T extends Serializable> TenantConfigurationValue<T> writeConfigOption(final String key, final T value) {
        return tenantConfigurationManagement.addOrUpdateConfiguration(key, value);
    }

    protected TenantConfigurationManagement getTenantConfigurationManagement() {
        return tenantConfigurationManagement;
    }

    protected Binder<B> getBinder() {
        return binder;
    }

    protected B getBinderBean() {
        return getBinder().getBean();
    }

    /**
     * Vaadin 8 {@code GridLayout(3, rows)} with an expanding middle column
     * ({@code setColumnExpandRatio(1, 1.0F)}): a {@link Div} laid out as a CSS
     * grid. Spacing and look are left to the theme.
     *
     * @return the grid container
     */
    protected static Div createGridLayout() {
        final Div gridLayout = new Div();
        gridLayout.getStyle().set("display", "grid").set("grid-template-columns", "auto 1fr auto");
        return gridLayout;
    }

    /**
     * Vaadin 8 {@code GridLayout.addComponent(component, column, row)}.
     *
     * @param gridLayout
     *            container created by {@link #createGridLayout()}
     * @param component
     *            component to add
     * @param column
     *            zero-based column
     * @param row
     *            zero-based row
     */
    protected static void addToGridLayout(final Div gridLayout, final Component component, final int column,
            final int row) {
        component.getElement().getStyle().set("grid-column", String.valueOf(column + 1)).set("grid-row",
                String.valueOf(row + 1));
        gridLayout.add(component);
    }

    /**
     * Vaadin 8 {@code GridLayout.setComponentAlignment(component,
     * Alignment.BOTTOM_RIGHT)}.
     *
     * @param component
     *            component in a grid created by {@link #createGridLayout()}
     */
    protected static void alignBottomRight(final Component component) {
        component.getElement().getStyle().set("justify-self", "end").set("align-self", "end");
    }

}
