/**
 * Copyright (c) 2021 Bosch.IO GmbH and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hawkbit.ui.common.data.suppliers;

import com.vaadin.flow.data.provider.DataProvider;

/**
 * Interface for backend data retrieval provider.
 *
 * @param <T>
 *            UI Proxy entity type
 * @param <F>
 *            Filter type
 */
public interface DataSupplier<T, F> {

    /**
     * Provides back end data.
     *
     * @return back end data provider
     */
    DataProvider<T, F> dataProvider();

    // Vaadin Flow: the data communicator is created by the Grid itself
    // (Grid#getDataCommunicator()); it is bound to the grid's element and
    // cannot be created up front, so it is no longer supplied here.

}
