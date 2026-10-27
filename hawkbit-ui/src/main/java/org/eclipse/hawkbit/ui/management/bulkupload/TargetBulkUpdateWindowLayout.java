/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hawkbit.ui.management.bulkupload;

import java.util.Map;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

import org.eclipse.hawkbit.repository.DeploymentManagement;
import org.eclipse.hawkbit.repository.DistributionSetManagement;
import org.eclipse.hawkbit.repository.TargetManagement;
import org.eclipse.hawkbit.repository.TargetTagManagement;
import org.eclipse.hawkbit.repository.TargetTypeManagement;
import org.eclipse.hawkbit.ui.UiProperties;
import org.eclipse.hawkbit.ui.common.CommonUiDependencies;
import org.eclipse.hawkbit.ui.common.data.proxies.ProxyBulkUploadWindow;
import org.eclipse.hawkbit.ui.common.data.proxies.ProxyDistributionSet;
import org.eclipse.hawkbit.ui.common.data.proxies.ProxyTag;
import org.eclipse.hawkbit.ui.common.data.proxies.ProxyTarget;
import org.eclipse.hawkbit.ui.common.data.proxies.ProxyTypeInfo;
import org.eclipse.hawkbit.ui.components.SPUIComponentProvider;
import org.eclipse.hawkbit.ui.decorators.SPUIButtonStyleNoBorder;
import org.eclipse.hawkbit.ui.utils.UIComponentIdProvider;
import org.eclipse.hawkbit.ui.utils.UIMessageIdProvider;
import org.eclipse.hawkbit.ui.utils.UINotification;
import org.eclipse.hawkbit.ui.utils.VaadinMessageSource;

import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.shared.Registration;
import com.vaadin.shared.ui.ContentMode;
import com.vaadin.ui.Alignment;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.ui.CustomComponent;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.html.Span;
import com.vaadin.ui.Link;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

/**
 * Bulk target upload layout.
 */
public class TargetBulkUpdateWindowLayout extends CustomComponent {
    private static final long serialVersionUID = 1L;

    private final VaadinMessageSource i18n;
    private final UINotification uinotification;

    private final TargetBulkUploadUiState targetBulkUploadUiState;

    private final ComboBox<ProxyDistributionSet> dsCombo;
    private final ComboBox<ProxyTypeInfo> targetTypeCombo;

    private final transient TargetBulkTokenTags tagsComponent;
    private final TextArea descTextArea;
    private final ProgressBar progressBar;
    private final Span targetsCountLabel;
    private final Upload uploadButton;
    private final Link linkToSystemConfigHelp;

    private final Span windowCaption;
    private final Button closeButton;
    private Registration closeRegistration;

    private final Binder<ProxyBulkUploadWindow> binder;

    /**
     * Constructor for TargetBulkUpdateWindowLayout
     *
     * @param uiDependencies
     *            {@link CommonUiDependencies}
     * @param targetManagement
     *            TargetManagement
     * @param deploymentManagement
     *            DeploymentManagement
     * @param tagManagement
     *            TargetTagManagement
     * @param targetTypeManagement
     *            TargetTypeManagement
     * @param distributionSetManagement
     *            DistributionSetManagement
     * @param uiproperties
     *            UiProperties
     * @param uiExecutor
     *            Executor
     * @param targetBulkUploadUiState
     *            TargetBulkUploadUiState
     */
    public TargetBulkUpdateWindowLayout(final CommonUiDependencies uiDependencies, final TargetManagement targetManagement,
                                        final DeploymentManagement deploymentManagement, final TargetTypeManagement targetTypeManagement, final TargetTagManagement tagManagement,
                                        final DistributionSetManagement distributionSetManagement, final UiProperties uiproperties,
                                        final Executor uiExecutor, final TargetBulkUploadUiState targetBulkUploadUiState) {
        this.i18n = uiDependencies.getI18n();
        this.uinotification = uiDependencies.getUiNotification();
        this.targetBulkUploadUiState = targetBulkUploadUiState;
        this.binder = new Binder<>();

        final BulkUploadWindowLayoutComponentBuilder componentBuilder = new BulkUploadWindowLayoutComponentBuilder(i18n,
                distributionSetManagement, targetTypeManagement);

        this.dsCombo = componentBuilder.createDistributionSetCombo(binder);
        this.targetTypeCombo = componentBuilder.createTargetTypeCombo(binder);
        this.tagsComponent = new TargetBulkTokenTags(uiDependencies, tagManagement);

        this.descTextArea = componentBuilder.createDescriptionField(binder);
        this.progressBar = createProgressBar();
        this.targetsCountLabel = getStatusCountLabel();

        final BulkUploadHandler bulkUploadHandler = new BulkUploadHandler(uiDependencies, uiExecutor, targetManagement,
                tagManagement, distributionSetManagement, deploymentManagement, this::getBulkUploadInputsBean);
        this.uploadButton = createUploadButton(bulkUploadHandler);

        this.linkToSystemConfigHelp = SPUIComponentProvider.getHelpLink(i18n,
                uiproperties.getLinks().getDocumentation().getDeploymentView());

        this.windowCaption = new Span(i18n.getMessage("caption.bulk.upload.targets"));
        this.closeButton = getCloseButton();

        buildLayout();
        initInputs();
    }

    private ProgressBar createProgressBar() {
        final ProgressBar progressBarIndicator = new ProgressBar(0F);
        progressBarIndicator.setCaption(i18n.getMessage("artifact.upload.progress.caption"));
        progressBarIndicator.setSizeFull();

        return progressBarIndicator;
    }

    private static Span getStatusCountLabel() {
        final Span countLabel = new Span("", ContentMode.HTML);
        countLabel.setId(UIComponentIdProvider.BULK_UPLOAD_COUNT);

        return countLabel;
    }

    private ProxyBulkUploadWindow getBulkUploadInputsBean() {
        final ProxyBulkUploadWindow bean = new ProxyBulkUploadWindow();
        bean.setDistributionSetInfo(binder.getBean().getDistributionSetInfo());
        bean.setTypeInfo(binder.getBean().getTypeInfo());
        bean.setTagIdsWithNameToAssign(getTagIdsWithNameToAssign());
        bean.setDescription(binder.getBean().getDescription());

        return bean;
    }

    private Map<Long, String> getTagIdsWithNameToAssign() {
        return tagsComponent.getSelectedTagsForAssignment().stream()
                .collect(Collectors.toMap(ProxyTag::getId, ProxyTag::getName));
    }

    private Upload createUploadButton(final BulkUploadHandler uploadHandler) {
        final Upload upload = new Upload();

        upload.setButtonCaption(i18n.getMessage("caption.bulk.upload"));
        upload.setReceiver(uploadHandler);
        upload.addSucceededListener(uploadHandler);
        upload.addFailedListener(uploadHandler);
        upload.addStartedListener(uploadHandler);

        return upload;
    }

    private static Button getCloseButton() {
        final Button closeBtn = SPUIComponentProvider.getButton(UIComponentIdProvider.BULK_UPLOAD_CLOSE_BUTTON_ID, "",
                "", "", true, VaadinIcon.CLOSE, SPUIButtonStyleNoBorder.class);
        closeBtn.addClassNames("borderless");

        return closeBtn;
    }

    /**
     * Sets the close call back event listener
     *
     * @param closeCallback
     *            Runnable
     */
    public void setCloseCallback(final Runnable closeCallback) {
        if (closeRegistration != null) {
            closeRegistration.remove();
        }
        closeRegistration = closeButton.addClickListener(event -> closeCallback.run());
    }

    /**
     * Init the values in popup.
     */
    private void initInputs() {
        binder.setBean(new ProxyBulkUploadWindow());

        // init with dummy master entity in order to init tag panel
        tagsComponent.masterEntityChanged(new ProxyTarget());

        progressBar.setValue(0F);
        progressBar.setVisible(false);

        targetsCountLabel.setValue("");
        targetsCountLabel.setVisible(false);
    }

    private void buildLayout() {
        final HorizontalLayout captionLayout = new HorizontalLayout();
        captionLayout.setPadding(false);
        captionLayout.setSpacing(false);
        captionLayout.setSizeFull();
        captionLayout.addClassNames("v-window-header");

        captionLayout.add(windowCaption, closeButton);
        captionLayout.setFlexGrow(1.0F, windowCaption);

        final VerticalLayout tagsLayout = new VerticalLayout();
        tagsLayout.setId(UIComponentIdProvider.BULK_UPLOAD_TAG);
        tagsLayout.setCaption(i18n.getMessage("caption.tags.tab"));
        tagsLayout.setSpacing(false);
        tagsLayout.setPadding(false);
        tagsLayout.setSizeFull();
        tagsLayout.setHeight("100px");
        tagsLayout.addClassNames("bulk-target-tags-layout");

        tagsLayout.add(tagsComponent.getTagPanel());

        final HorizontalLayout uploaderLayout = new HorizontalLayout();
        uploaderLayout.setPadding(false);
        uploaderLayout.setSpacing(false);
        uploaderLayout.setSizeFull();

        uploaderLayout.add(uploadButton);
        uploaderLayout.add(linkToSystemConfigHelp);
        uploaderLayout.setComponentAlignment(linkToSystemConfigHelp, Alignment.BOTTOM_RIGHT);
        uploaderLayout.setFlexGrow(1.0F, uploadButton);

        final FormLayout inputsLayout = new FormLayout();
        inputsLayout.setPadding(true);
        inputsLayout.setSpacing(true);
        inputsLayout.setWidth("400px");

        inputsLayout.add(dsCombo, targetTypeCombo, tagsLayout, descTextArea, progressBar, targetsCountLabel, uploaderLayout);

        final VerticalLayout mainLayout = new VerticalLayout();
        mainLayout.setPadding(false);
        mainLayout.setSpacing(true);
        mainLayout.setId(UIComponentIdProvider.BULK_UPLOAD_MAIN_LAYOUT);

        mainLayout.add(captionLayout, inputsLayout);

        setCompositionRoot(mainLayout);
    }

    /**
     * Actions once start of upload
     */
    public void onStartOfUpload() {
        targetBulkUploadUiState.setDsInfo(binder.getBean().getDistributionSetInfo());
        targetBulkUploadUiState.setTypeInfo(binder.getBean().getTypeInfo());
        targetBulkUploadUiState.setTagIdsWithNameToAssign(getTagIdsWithNameToAssign());
        targetBulkUploadUiState.setDescription(binder.getBean().getDescription());

        targetsCountLabel.setVisible(true);
        targetsCountLabel.setValue(i18n.getMessage("message.bulk.upload.upload.started"));

        disableInputs();
    }

    /**
     * Actions once start of provisioning
     */
    public void onStartOfProvisioning() {
        targetsCountLabel.setVisible(true);
        targetsCountLabel.setValue(i18n.getMessage("message.bulk.upload.provisioning.started"));
    }

    /**
     * Sets the upload progress value
     *
     * @param value
     *            progress percentage
     */
    public void setProgressBarValue(final float value) {
        progressBar.setVisible(true);
        progressBar.setValue(value);
    }

    /**
     * Actions once start of assignment
     */
    public void onStartOfAssignment() {
        targetsCountLabel.setVisible(true);
        targetsCountLabel.setValue(i18n.getMessage("message.bulk.upload.assignment.started"));
    }

    /**
     * Actions once bulk upload is completed.
     *
     * @param successCount
     *            Total count of success upload
     * @param failCount
     *            Total count of fail upload
     */
    public void onUploadCompletion(final int successCount, final int failCount) {
        final String targetCountLabel = getFormattedCountLabelValue(successCount, failCount);
        targetsCountLabel.setVisible(true);
        targetsCountLabel.setValue(targetCountLabel);

        enableInputs();
    }

    private String getFormattedCountLabelValue(final int successfulUploadCount, final int failedUploadCount) {
        final StringBuilder countLabelBuilder = new StringBuilder();
        countLabelBuilder.append(
                i18n.getMessage(UIMessageIdProvider.MESSAGE_TARGET_BULKUPLOAD_RESULT_SUCCESS, successfulUploadCount));
        countLabelBuilder.append("<br/><font color=RED>");
        countLabelBuilder
                .append(i18n.getMessage(UIMessageIdProvider.MESSAGE_TARGET_BULKUPLOAD_RESULT_FAIL, failedUploadCount));
        countLabelBuilder.append("</font>");
        return countLabelBuilder.toString();
    }

    /**
     * Actions once upload fails
     *
     * @param failureReason
     *            Reason for failed upload
     */
    public void onUploadFailure(final String failureReason) {
        targetsCountLabel.setVisible(true);
        targetsCountLabel.setValue(
                new StringBuilder().append("<font color=RED>").append(failureReason).append("</font>").toString());

        uinotification.displayValidationError(failureReason);
        enableInputs();
    }

    /**
     * Actions once assignment fails
     *
     * @param failureReason
     *            Reason for failed upload
     */
    public void onAssignmentFailure(final String failureReason) {
        uinotification.displayValidationError(failureReason);
    }

    private void disableInputs() {
        changeInputsState(false);
    }

    private void changeInputsState(final boolean enabled) {
        dsCombo.setEnabled(enabled);
        targetTypeCombo.setEnabled(enabled);
        tagsComponent.getTagPanel().setEnabled(enabled);
        descTextArea.setEnabled(enabled);
        uploadButton.setEnabled(enabled);
    }

    private void enableInputs() {
        changeInputsState(true);
    }

    /**
     * Reset target bulk upload ui state
     */
    public void clearUiState() {
        targetBulkUploadUiState.setDsInfo(null);
        targetBulkUploadUiState.setTypeInfo(null);
        targetBulkUploadUiState.getTagIdsWithNameToAssign().clear();
        targetBulkUploadUiState.setDescription(null);
    }

    /**
     * Restore the target bulk upload layout field values.
     */
    public void restoreComponentsValue() {
        final ProxyBulkUploadWindow bulkUploadInputsToRestore = new ProxyBulkUploadWindow();
        bulkUploadInputsToRestore.setDistributionSetInfo(targetBulkUploadUiState.getDsInfo());
        bulkUploadInputsToRestore.setTypeInfo(targetBulkUploadUiState.getTypeInfo());
        bulkUploadInputsToRestore.setDescription(targetBulkUploadUiState.getDescription());
        bulkUploadInputsToRestore.setTagIdsWithNameToAssign(targetBulkUploadUiState.getTagIdsWithNameToAssign());

        binder.setBean(bulkUploadInputsToRestore);
        tagsComponent.getTagsById(targetBulkUploadUiState.getTagIdsWithNameToAssign().keySet())
                .forEach(tagsComponent::assignTag);

        disableInputs();
    }
}
