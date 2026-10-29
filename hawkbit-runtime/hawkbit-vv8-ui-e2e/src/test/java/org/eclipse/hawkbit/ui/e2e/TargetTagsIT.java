/**
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hawkbit.ui.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.regex.Pattern;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.KeyboardModifier;
import org.junit.jupiter.api.Test;

/**
 * Target tags in the Deployment view: create a tag, drag two targets onto it, filter by it, remove it from one target
 * in the target's details, see the delete refused while the tag filters the list, then delete it. Seed state: tags
 * {@code Lab} and {@code Field}; {@code Seed Device 09} and {@code 10} untagged.
 */
class TargetTagsIT extends AbstractUiE2eTest {

    private static final String USER = "admin";
    private static final String TAG = "Workflow";
    private static final String TAG_DESC = "Created by an e2e test";
    private static final String D9 = "Seed Device 09";
    private static final String D10 = "Seed Device 10";

    // The target tag panel and its gear menu are found by their component ids: the gear is an icon-only button without
    // an accessible name, as in the original UI.
    private Locator tagPanel() {
        return page.locator("[id=\"target.tag.tableId\"]");
    }

    /** The target tags shown in the panel, in panel order. */
    private String targetTags() {
        return String.join(", ", HawkbitUi.textsOf(tagPanel().getByRole(AriaRole.BUTTON)).stream().filter(t -> !t.isEmpty()).toList());
    }

    private Locator tagButton(final String name) {
        return HawkbitUi.buttonNamed(tagPanel(), name).first();
    }

    /** A click on a tag button toggles the filter by that tag. */
    private void toggleTagFilter(final String name) {
        // a tooltip left open by the previous click can cover the button: move the pointer off first, as a user does
        page.mouse().move(0, 0);
        ui.waitFor(() -> page.locator("vaadin-tooltip-overlay[opened]").count() == 0, 3000, 100);
        tagButton(name).click();
        ui.settle(1200);
    }

    /** The gear ("Configure..") of the tag panel and one of its entries (create, update, delete). */
    private void tagMenu(final String entry) {
        page.locator("[id=\"target.menu.bar.id\"]").click();
        ui.settle(400);
        page.getByRole(AriaRole.MENUITEM, new Page.GetByRoleOptions().setName(Pattern.compile("\\b" + entry + "\\b"))).click();
        ui.settle(800);
    }

    /** In delete mode each tag's row has a delete button ("Delete.."). */
    private Locator tagDeleteButton(final String name) {
        final Locator row = grids.rowOf(tagPanel(), Pattern.compile("^\\W*" + Pattern.quote(name) + "\\W*$"));
        final Locator button = grids.rowButton(row, "Delete");
        assertNotNull(button, "delete button of the tag " + name);
        return button;
    }

    private List<String> targetRows() {
        return grids.rowTexts(grids.grid("targets"));
    }

    private static List<String> sorted(final List<String> l) {
        return l.stream().sorted().toList();
    }

    @Test
    void createAssignFilterUnassignAndDeleteTag() {
        step(1, "sign in, the Deployment view: the target tags Lab and Field", () -> {
            ui.openAddress("login");
            ui.login(USER);
            assertEquals("Deployment", ui.currentView(), "view");
            assertEquals("Field, Lab", targetTags(), "tags");
        });

        step(2, "create the tag Workflow: success notification, it is listed", () -> {
            tagMenu("create");
            assertTrue(ui.dialogText().startsWith("Create new Tag"), "window: " + ui.dialogText());
            final Locator d = ui.dialog();
            d.getByLabel("Name").fill(TAG);
            d.getByLabel("Description").fill(TAG_DESC);
            ui.settle(500);
            // colour: the proposed one is kept
            final List<String> notes = ui.notificationsDuring(() -> ui.clickDialogButton("Save"), 6000, s -> !s.isEmpty());
            assertTrue(notes.contains(TAG + " saved successfully"), "notifications: " + notes);
            assertEquals("Field, Lab, Workflow", ui.waitForValue(() -> targetTags().contains(TAG) ? targetTags() : null, 10_000, 250), "tags");
        });

        step(3, "select Seed Device 09 and 10 and drag them onto Workflow: 2 targets assigned", () -> {
            ui.dismissNotifications(20_000);
            final Locator g = grids.grid("targets");
            grids.selectRow(grids.rowOf(g, D9));
            grids.clickRow(grids.rowOf(g, D10), KeyboardModifier.CONTROL, 800);
            assertEquals(List.of(D9, D10), sorted(Grids.selectedRows(g)), "selected");
            final List<String> notes = ui.notificationsDuring(() -> grids.dragRowOnto(grids.rowOf(g, D10), () -> tagButton(TAG)), 6000,
                    s -> !s.isEmpty());
            assertTrue(notes.contains("2 Targets are assigned to Tag " + TAG), "notifications: " + notes);
        });

        step(4, "filter by Workflow: exactly Seed Device 09 and 10", () -> {
            ui.dismissNotifications(20_000);
            toggleTagFilter(TAG);
            ui.waitFor(() -> targetRows().size() == 2, 10_000);
            assertEquals(List.of(D9, D10), sorted(targetRows()), "targets");
        });

        step(5, "remove Workflow in Seed Device 10's details: it no longer has it, the list shows only Seed Device 09", () -> {
            grids.selectRow(grids.rowOf(grids.grid("targets"), D10));
            final Locator d = ui.details("target");
            ui.openTab(d, "Tags");
            assertEquals(TAG + " ×", HawkbitUi.tabText(d), "tags of " + D10);
            // the assigned tag is a token ("Click to remove")
            final Locator token = d.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(Pattern.compile("^" + TAG + "\\s*×$")));
            final List<String> notes = ui.notificationsDuring(token::click, 6000, s -> !s.isEmpty());
            assertTrue(notes.contains("Target " + D10 + " is unassigned from Tag " + TAG), "notifications: " + notes);
            assertTrue(ui.waitFor(() -> HawkbitUi.tabText(d).isEmpty(), 10_000), "tags of " + D10 + " after: " + HawkbitUi.tabText(d));
            assertEquals(List.of(D9), ui.waitForValue(() -> {
                final List<String> r = targetRows();
                return r.size() == 1 ? r : null;
            }, 15_000, 250), "targets");
        });

        step(6, "with the filter on, deleting Workflow is refused: deselect the tag first", () -> {
            ui.dismissNotifications(20_000);
            tagMenu("delete");
            tagDeleteButton(TAG).click();
            ui.settle(800);
            assertTrue(ui.dialogText().contains("Are you sure you want to delete Target Tag " + TAG + "?"), "confirmation: " + ui.dialogText());
            final List<String> notes = ui.notificationsDuring(() -> ui.clickDialogButton("OK"), 6000,
                    s -> s.stream().anyMatch(t -> t.contains("deselect")) && s.stream().anyMatch(t -> t.contains("failed")));
            assertTrue(notes.contains("Please deselect the tag " + TAG + " before deleting"), "notifications: " + notes);
            assertEquals("Field, Lab, Workflow", targetTags(), "tags");
            assertEquals(List.of(D9), targetRows(), "targets");
        });

        step(7, "clear the filter and delete Workflow: it is gone, Seed Device 09 has no tags", () -> {
            ui.dismissNotifications(20_000);
            toggleTagFilter(TAG);
            assertTrue(ui.waitFor(() -> targetRows().size() == 10, 10_000), "filter cleared: " + targetRows());
            tagDeleteButton(TAG).click();
            ui.settle(800);
            assertTrue(ui.dialogText().contains("Are you sure you want to delete Target Tag " + TAG + "?"), "confirmation: " + ui.dialogText());
            final List<String> notes = ui.notificationsDuring(() -> ui.clickDialogButton("OK"), 6000, s -> !s.isEmpty());
            assertTrue(notes.contains("Target Tag " + TAG + " deleted successfully"), "notifications: " + notes);
            assertEquals("Field, Lab", ui.waitForValue(() -> targetTags().contains(TAG) ? null : targetTags(), 10_000, 250), "tags");
            grids.selectRow(grids.rowOf(grids.grid("targets"), D9));
            final Locator d = ui.details("target");
            ui.openTab(d, "Tags");
            assertEquals("", HawkbitUi.tabText(d), "tags of " + D9);
        });
    }
}
