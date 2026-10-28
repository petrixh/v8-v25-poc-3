/**
 * Copyright (c) 2020 Bosch.IO GmbH and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hawkbit.ui.common.detailslayout;

import org.eclipse.hawkbit.repository.ArtifactManagement;
import org.eclipse.hawkbit.ui.artifacts.details.ArtifactDetailsGrid;
import org.eclipse.hawkbit.ui.artifacts.smtable.SmMetaDataWindowBuilder;
import org.eclipse.hawkbit.ui.artifacts.smtable.SmWindowBuilder;
import org.eclipse.hawkbit.ui.common.CommonDialogWindow;
import org.eclipse.hawkbit.ui.common.CommonUiDependencies;
import org.eclipse.hawkbit.ui.common.data.proxies.ProxySoftwareModule;
import org.eclipse.hawkbit.ui.common.grid.header.AbstractDetailsHeader;
import org.eclipse.hawkbit.ui.utils.SPUIStyleDefinitions;
import org.eclipse.hawkbit.ui.utils.UIComponentIdProvider;
import org.eclipse.hawkbit.ui.utils.UIMessageIdProvider;

import com.vaadin.flow.component.ModalityMode;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.dialog.Dialog;

/**
 * Software module details header
 */
public class SoftwareModuleDetailsHeader extends AbstractDetailsHeader<ProxySoftwareModule> {
    private static final long serialVersionUID = 1L;

    private final transient SmWindowBuilder smWindowBuilder;
    private final transient SmMetaDataWindowBuilder smMetaDataWindowBuilder;
    private final transient CommonUiDependencies uiDependencies;

    private transient ArtifactDetailsHeaderSupport artifactDetailsHeaderSupport;
    private transient ArtifactManagement artifactManagement;
    private ArtifactDetailsGrid artifactDetailsGrid;

    /**
     * Constructor for SoftwareModuleDetailsHeader
     *
     * @param uiDependencies
     *            {@link CommonUiDependencies}
     * @param smWindowBuilder
     *            SmWindowBuilder
     * @param smMetaDataWindowBuilder
     *            SmMetaDataWindowBuilder
     */
    public SoftwareModuleDetailsHeader(final CommonUiDependencies uiDependencies, final SmWindowBuilder smWindowBuilder,
            final SmMetaDataWindowBuilder smMetaDataWindowBuilder) {
        super(uiDependencies.getI18n(), uiDependencies.getPermChecker(), uiDependencies.getEventBus(), uiDependencies.getUiNotification());
        this.uiDependencies = uiDependencies;

        this.smWindowBuilder = smWindowBuilder;
        this.smMetaDataWindowBuilder = smMetaDataWindowBuilder;
    }

    @Override
    protected String getMasterEntityType() {
        return i18n.getMessage("upload.swModuleTable.header");
    }

    @Override
    protected String getMasterEntityDetailsCaptionId() {
        return UIComponentIdProvider.SOFTWARE_MODULE_DETAILS_HEADER_LABEL_ID;
    }

    @Override
    protected String getMasterEntityName(final ProxySoftwareModule masterEntity) {
        return masterEntity.getNameAndVersion();
    }

    @Override
    public void masterEntityChanged(final ProxySoftwareModule entity) {
        super.masterEntityChanged(entity);

        if (artifactDetailsHeaderSupport != null) {
            if (entity == null) {
                artifactDetailsHeaderSupport.disableArtifactDetailsIcon();
            } else {
                artifactDetailsHeaderSupport.enableArtifactDetailsIcon();
                if (artifactDetailsGrid != null) {
                    artifactDetailsGrid.refreshAll();
                }
            }
        }
    }

    @Override
    protected String getEditIconId() {
        return UIComponentIdProvider.UPLOAD_SW_MODULE_EDIT_BUTTON;
    }

    @Override
    protected void onEdit() {
        if (selectedEntity == null) {
            return;
        }

        final Dialog updateWindow = smWindowBuilder.getWindowForUpdate(selectedEntity);

        updateWindow.setHeaderTitle(i18n.getMessage("caption.update", i18n.getMessage("caption.software.module")));
        updateWindow.open();
        updateWindow.setVisible(Boolean.TRUE);
    }

    @Override
    protected String getMetaDataIconId() {
        return UIComponentIdProvider.UPLOAD_SW_MODULE_METADATA_BUTTON;
    }

    @Override
    protected void showMetaData() {
        if (selectedEntity == null) {
            return;
        }

        final Dialog metaDataWindow = smMetaDataWindowBuilder.getWindowForShowSmMetaData(selectedEntity.getId(),
                selectedEntity.getNameAndVersion());

        metaDataWindow.open();
        metaDataWindow.setVisible(Boolean.TRUE);
    }

    /**
     * Add artifact details header support
     *
     * @param artifactManagement
     *            ArtifactManagement
     */
    public void addArtifactDetailsHeaderSupport(final ArtifactManagement artifactManagement) {
        if (artifactDetailsHeaderSupport == null) {
            this.artifactManagement = artifactManagement;

            artifactDetailsHeaderSupport = new ArtifactDetailsHeaderSupport(i18n,
                    UIComponentIdProvider.SW_MODULE_ARTIFACT_DETAILS_BUTTON, this::showArtifactDetailsWindow);
            addHeaderSupport(artifactDetailsHeaderSupport);
        }
    }

    private static final String MAXIMIZED_STYLE = "maximized";

    /**
     * Show artifact detail window layout
     */
    private void showArtifactDetailsWindow() {
        if (selectedEntity == null) {
            return;
        }

        if (artifactDetailsGrid == null) {
            artifactDetailsGrid = new ArtifactDetailsGrid(uiDependencies, artifactManagement);
        }
        setInitialArtifactDetailsGridSize(artifactDetailsGrid);
        artifactDetailsGrid.getMasterEntitySupport().masterEntityChanged(selectedEntity);

        final Dialog artifactDtlsWindow = new Dialog();
        artifactDtlsWindow.setId(UIComponentIdProvider.SHOW_ARTIFACT_DETAILS_POPUP_ID);

        // Vaadin 8 setClosable(true): close button and Esc; a click outside
        // does not close, as with a Vaadin 8 modal window
        artifactDtlsWindow.setCloseOnEsc(true);
        artifactDtlsWindow.setCloseOnOutsideClick(false);
        artifactDtlsWindow.setResizable(true);
        artifactDtlsWindow.setModality(ModalityMode.STRICT);
        artifactDtlsWindow.addClassNames(SPUIStyleDefinitions.CONFIRMATION_WINDOW_CAPTION);

        // Vaadin 8: the caption was the name and version with the assistive prefix, which the old
        // theme showed in the title
        artifactDtlsWindow.setHeaderTitle(
                i18n.getMessage(UIMessageIdProvider.CAPTION_ARTIFACT_DETAILS_OF) + " " + selectedEntity.getNameAndVersion());

        // Vaadin 8: the window's maximize/restore button (WindowMode); Flow
        // dialogs have none, so the header gets a button that does the same
        final Button maximizeButton = new Button(VaadinIcon.EXPAND_SQUARE.create());
        maximizeButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ICON);
        maximizeButton.addClickListener(event -> {
            if (!artifactDtlsWindow.hasClassName(MAXIMIZED_STYLE)) {
                artifactDtlsWindow.addClassName(MAXIMIZED_STYLE);
                artifactDtlsWindow.setSizeFull();
                artifactDetailsGrid.setSizeFull();

                artifactDetailsGrid.createMaximizedContent();
            } else {
                artifactDtlsWindow.removeClassName(MAXIMIZED_STYLE);
                artifactDtlsWindow.setSizeUndefined();
                setInitialArtifactDetailsGridSize(artifactDetailsGrid);

                artifactDetailsGrid.createMinimizedContent();
            }
        });
        artifactDtlsWindow.getHeader().add(maximizeButton);
        CommonDialogWindow.addCloseButton(artifactDtlsWindow);

        artifactDtlsWindow.addOpenedChangeListener(event -> {
            if (!event.isOpened()) {
                artifactDetailsGrid = null;
            }
        });

        artifactDtlsWindow.add(artifactDetailsGrid);

        artifactDtlsWindow.open();
    }

    private static void setInitialArtifactDetailsGridSize(final ArtifactDetailsGrid artifactDetailsGrid) {
        artifactDetailsGrid.setWidth("700px");
        artifactDetailsGrid.setHeight("500px");
    }
}
