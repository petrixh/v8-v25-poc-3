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
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.Composite;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.formlayout.FormLayout.ResponsiveStep;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.NativeLabel;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

/**
 * Bulk target upload layout.
 */
public class TargetBulkUpdateWindowLayout extends Composite<VerticalLayout> {
    private static final long serialVersionUID = 1L;

    private final VaadinMessageSource i18n;
    private final UINotification uinotification;

    private final TargetBulkUploadUiState targetBulkUploadUiState;

    private final ComboBox<ProxyDistributionSet> dsCombo;
    private final ComboBox<ProxyTypeInfo> targetTypeCombo;

    private final transient TargetBulkTokenTags tagsComponent;
    private final TextArea descTextArea;
    private final ProgressBar progressBar;
    private final NativeLabel progressBarCaption;
    private final Span targetsCountLabel;
    private final Upload uploadButton;
    private final Anchor linkToSystemConfigHelp;

    private final Span windowCaption;
    private final Button closeButton;
    private Registration closeRegistration;

    private VerticalLayout mainLayout;

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
        this.progressBarCaption = new NativeLabel(i18n.getMessage("artifact.upload.progress.caption"));
        this.progressBarCaption.setFor(progressBar);
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
        // Vaadin 8 caption: progressBarCaption, a label next to the bar
        final ProgressBar progressBarIndicator = new ProgressBar();
        progressBarIndicator.setValue(0F);
        progressBarIndicator.setSizeFull();

        return progressBarIndicator;
    }

    private static Span getStatusCountLabel() {
        // Vaadin 8 Label with ContentMode.HTML: the value is the span's inner
        // HTML (setTargetsCountLabelHtml)
        final Span countLabel = new Span();
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
        // Vaadin 8: a button-only upload of one file (Receiver and listeners
        // are one UploadHandler in Flow). Flow's Upload also lists the file and
        // accepts drops: no drop, one file at a time, list cleared when done
        // (here for a refused file, which leaves the upload enabled; else in
        // changeInputsState when the inputs are enabled again)
        final Upload upload = new Upload(uploadHandler);

        upload.setUploadButton(new Button(i18n.getMessage("caption.bulk.upload")));
        upload.setDropAllowed(false);
        upload.setMaxFiles(1);
        upload.addAllFinishedListener(event -> upload.clearFileList());

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
        progressBarCaption.setVisible(false);

        setTargetsCountLabelHtml("");
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
        tagsLayout.setSpacing(false);
        tagsLayout.setPadding(false);
        tagsLayout.setSizeFull();
        tagsLayout.setHeight("100px");
        tagsLayout.addClassNames("bulk-target-tags-layout");

        // Vaadin 8: tagsLayout.setCaption(...), shown by the form layout
        tagsLayout.add(new NativeLabel(i18n.getMessage("caption.tags.tab")));
        tagsLayout.add(tagsComponent.getTagPanel());

        final HorizontalLayout uploaderLayout = new HorizontalLayout();
        uploaderLayout.setPadding(false);
        uploaderLayout.setSpacing(false);
        uploaderLayout.setSizeFull();

        uploaderLayout.add(uploadButton);
        uploaderLayout.add(linkToSystemConfigHelp);
        uploaderLayout.setVerticalComponentAlignment(Alignment.END, linkToSystemConfigHelp);
        uploaderLayout.setFlexGrow(1.0F, uploadButton);

        final FormLayout inputsLayout = new FormLayout();
        // Vaadin 8 FormLayout: one column; Flow has no spacing/margin here
        inputsLayout.setResponsiveSteps(new ResponsiveStep("0", 1));
        inputsLayout.setWidth("400px");

        inputsLayout.add(dsCombo, targetTypeCombo, tagsLayout, descTextArea, progressBarCaption, progressBar,
                targetsCountLabel, uploaderLayout);

        mainLayout = new VerticalLayout();
        mainLayout.setPadding(false);
        mainLayout.setSpacing(true);
        mainLayout.setId(UIComponentIdProvider.BULK_UPLOAD_MAIN_LAYOUT);

        mainLayout.add(captionLayout, inputsLayout);
    }

    @Override
    protected VerticalLayout initContent() {
        // Vaadin 8: setCompositionRoot(mainLayout)
        return mainLayout;
    }

    // Vaadin 8 Label with ContentMode.HTML: same HTML as inner HTML of a span
    private void setTargetsCountLabelHtml(final String html) {
        targetsCountLabel.getElement().setProperty("innerHTML", html);
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
        setTargetsCountLabelHtml(i18n.getMessage("message.bulk.upload.upload.started"));

        disableInputs();
    }

    /**
     * Actions once start of provisioning
     */
    public void onStartOfProvisioning() {
        targetsCountLabel.setVisible(true);
        setTargetsCountLabelHtml(i18n.getMessage("message.bulk.upload.provisioning.started"));
    }

    /**
     * Sets the upload progress value
     *
     * @param value
     *            progress percentage
     */
    public void setProgressBarValue(final float value) {
        progressBar.setVisible(true);
        progressBarCaption.setVisible(true);
        progressBar.setValue(value);
    }

    /**
     * Actions once start of assignment
     */
    public void onStartOfAssignment() {
        targetsCountLabel.setVisible(true);
        setTargetsCountLabelHtml(i18n.getMessage("message.bulk.upload.assignment.started"));
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
        setTargetsCountLabelHtml(targetCountLabel);

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
        setTargetsCountLabelHtml(
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
        if (enabled) {
            // The upload's own all-finished event is a client event and is
            // dropped while the upload is disabled (from upload start to the
            // end of provisioning): clear the finished file here, so the button
            // takes the next file as in Vaadin 8
            uploadButton.clearFileList();
        }
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
