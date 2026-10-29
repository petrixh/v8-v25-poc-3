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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import com.microsoft.playwright.Locator;
import org.junit.jupiter.api.Test;

/**
 * Distribution sets in the Distributions view: edit a set's description, the rules for assigning modules (module type
 * not supported by the set type, module already in the set, set already assigned to a target), and deleting a set,
 * which both views then reflect. Seed state: four sets, no assignments.
 */
class DistributionSetRulesIT extends AbstractUiE2eTest {

    private static final String USER = "admin";
    private static final String DESCRIPTION = "Edited by an e2e test";
    private static final List<String> SEED_SETS = List.of("Seed OS Base 1.0.0", "Seed Bundle 1.0.0", "Seed Bundle 2.0.0", "Seed Apps 1.0.0");
    private static final List<String> AFTER_DELETE = List.of("Seed OS Base 1.0.0", "Seed Bundle 1.0.0", "Seed Bundle 2.0.0");

    /** A row whose text starts with {@code name} as a word ("Seed App 1.0.0" does not match "Seed Apps 1.0.0"). */
    private static Pattern startsWith(final String name) {
        return Pattern.compile("^" + Pattern.quote(name) + "\\b");
    }

    // Both lists of the Distributions view have the columns Name, Version, Delete: they are told apart by their ids.
    private Locator setList() {
        return page.locator("[id=\"distSet.tableId\"]");
    }

    private Locator moduleList() {
        return page.locator("[id=\"swModule.table\"]");
    }

    private record DragResult(List<String> notifications, boolean confirmation) {
    }

    /** Drags a module onto a set in the Distributions view: the notifications, and whether a confirmation opened. */
    private DragResult dragModule(final String module, final String set, final Predicate<List<String>> until) {
        final List<String> notes = ui.notificationsDuring(
                () -> grids.dragRowOnto(grids.rowOf(moduleList(), startsWith(module)), grids.rowOf(setList(), startsWith(set))), 8000, until);
        return new DragResult(notes, ui.dialogOpen());
    }

    private static Predicate<List<String>> anyContains(final String text) {
        return s -> s.stream().anyMatch(t -> t.contains(text));
    }

    @Test
    void editAssignRulesAndDelete() {
        step(1, "sign in and open Distributions: 4 sets", () -> {
            ui.openAddress("login");
            ui.login(USER);
            ui.goTo("Distributions");
            assertEquals("Distributions", ui.currentView(), "view");
            assertEquals(SEED_SETS, grids.rowTexts(setList()), "sets");
        });

        step(2, "edit Seed Apps 1.0.0's description: update notification, the details show it", () -> {
            grids.selectRow(grids.rowOf(setList(), startsWith("Seed Apps 1.0.0")));
            // "Edit.." (an icon-only button) in the selected set's details header
            page.locator("[id=\"ds.edit.button\"]").click();
            ui.settle(800);
            assertTrue(ui.dialogText().startsWith("Edit Distribution"), "window: " + ui.dialogText());
            final Locator d = ui.dialog();
            assertEquals("Seed Apps", d.getByLabel("Name").inputValue(), "name");
            d.getByLabel("Description").fill(DESCRIPTION);
            final List<String> notes = ui.notificationsDuring(() -> ui.clickDialogButton("Save"), 6000, anyContains("updated"));
            assertTrue(notes.contains("Seed Apps:1.0.0 updated successfully"), "notifications: " + notes);
            assertTrue(ui.dialogClosed(10_000), "window closed");
            final Locator sheet = ui.details("distributionset");
            ui.openTab(sheet, "Description");
            assertEquals(DESCRIPTION, HawkbitUi.tabTextArea(sheet), "description");
        });

        step(3, "modules refused: type not supported by the set's type, module already in the set", () -> {
            final DragResult type = dragModule("Seed Agent 1.0.0", "Seed OS Base 1.0.0", anyContains("cannot be assigned"));
            assertTrue(type.notifications().contains("Software Module Seed Agent:1.0.0 cannot be assigned, because Distribution "
                    + "Seed OS Base:1.0.0 does not support the Software Module Type Application"), "type refused: " + type.notifications());
            assertFalse(type.confirmation(), "confirmation opened for the unsupported type");
            final DragResult dup = dragModule("Seed OS 1.0.0", "Seed Bundle 1.0.0", anyContains("is already assigned to Distribution"));
            assertTrue(dup.notifications().contains("Software Module Seed OS:1.0.0 is already assigned to Distribution Seed Bundle:1.0.0"),
                    "duplicate refused: " + dup.notifications());
            assertFalse(dup.confirmation(), "confirmation opened for the duplicate");
        });

        step(4, "in Deployment, drag Seed Bundle 2.0.0 onto Seed Device 10 and confirm: 1 assignment", () -> {
            ui.goTo("Deployment");
            grids.dragRowOnto(grids.rowOf(grids.grid("distributionSets"), startsWith("Seed Bundle 2.0.0")),
                    grids.rowOf(grids.grid("targets"), "Seed Device 10"));
            assertTrue(ui.dialogText().contains("assign Distribution set Seed Bundle:2.0.0 to Target Seed Device 10?"),
                    "confirmation: " + ui.dialogText());
            final List<String> notes = ui.notificationsDuring(() -> ui.clickDialogButton("OK"), 8000, anyContains("Assignment(s) done"));
            assertTrue(notes.contains("1 Assignment(s) done"), "notifications: " + notes);
        });

        step(5, "in Distributions, a module onto the assigned Seed Bundle 2.0.0 is refused", () -> {
            ui.goTo("Distributions");
            final DragResult r = dragModule("Seed App 1.0.0", "Seed Bundle 2.0.0", anyContains("already assigned to target"));
            assertTrue(r.notifications().contains("Distribution Seed Bundle:2.0.0 is already assigned to target"), "refused: " + r.notifications());
            assertFalse(r.confirmation(), "confirmation opened");
        });

        step(6, "delete Seed Apps 1.0.0: success notification, 3 sets", () -> {
            final Locator delete = grids.rowButton(grids.rowOf(setList(), startsWith("Seed Apps 1.0.0")), "Delete");
            assertNotNull(delete, "delete button of the set");
            delete.click();
            ui.settle(800);
            assertTrue(ui.dialogText().contains("Are you sure you want to delete Distribution set Seed Apps:1.0.0?"), "question: " + ui.dialogText());
            final List<String> notes = ui.notificationsDuring(() -> ui.clickDialogButton("OK"), 8000, anyContains("deleted"));
            assertTrue(notes.contains("Distribution set Seed Apps:1.0.0 deleted successfully"), "notifications: " + notes);
            assertEquals(AFTER_DELETE, ui.waitForValue(() -> {
                final List<String> sets = grids.rowTexts(setList());
                return sets.size() == 3 ? sets : null;
            }, 10_000, 250), "sets");
        });

        step(7, "the Deployment view also lists 3 sets", () -> {
            ui.goTo("Deployment");
            assertEquals(AFTER_DELETE, grids.rowTexts(grids.grid("distributionSets")), "sets");
        });
    }
}
