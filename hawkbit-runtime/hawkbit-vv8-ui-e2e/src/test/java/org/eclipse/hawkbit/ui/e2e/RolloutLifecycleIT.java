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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.options.AriaRole;
import org.junit.jupiter.api.Test;

/**
 * A rollout's life in the Rollout view: create it, start it, pause and resume it, trigger its next group, delete it
 * while its updates run; the targets keep their actions. Seed state: no rollouts, approval off, no device connected
 * (groups never finish on their own). The rollout scheduler runs in the app's background, so states are waited for.
 */
class RolloutLifecycleIT extends AbstractUiE2eTest {

    private static final String USER = "admin";
    private static final String NAME = "WF Rollout Ops";
    private static final String SET_SHOWN = "Seed OS Base:1.0.0";
    private static final List<String> LAB = List.of("Seed Device 01", "Seed Device 02", "Seed Device 03", "Seed Device 04");

    private static final List<String> ROLLOUT_COLUMNS = List.of("Name", "Distribution set", "Status", "Detail status", "Groups");
    private static final List<String> GROUP_COLUMNS = List.of("Name", "Status", "Detail status", "Finished", "Trigger threshold");
    private static final Pattern PROGRESS_PART = Pattern.compile("([A-Za-z][A-Za-z ]*?) : (\\d+)");

    /**
     * The rollout actions are icon-only buttons (their meaning is in the tooltip). They are found by their component id
     * within the row: a tooltip still showing from the previous hover could be read for the next button.
     */
    private static final Map<String, Integer> ACTIONS = Map.of("Run", 6, "Pause", 7, "Trigger next group", 13, "Delete..", 10);

    // ---- the rollout list

    private Locator rolloutList() {
        return grids.byColumns(ROLLOUT_COLUMNS, null, 15_000);
    }

    /** The Rollout view with its list (the view keeps a drill-down into groups when left: back to the list then). */
    private void openRolloutList() {
        ui.goTo("Rollout");
        if (grids.byColumns(ROLLOUT_COLUMNS, null) == null) {
            ui.buttonNamed("Rollouts").click();
            ui.settle(1000);
        }
        rolloutList();
    }

    /** Detail status as its tooltip says it, parts in a fixed order: "Running : 2, Scheduled : 2". */
    private static String progress(final String tooltip) {
        final List<String> parts = new ArrayList<>();
        final Matcher m = PROGRESS_PART.matcher(tooltip == null ? "" : tooltip);
        while (m.find()) {
            parts.add(m.group(1).trim() + " : " + m.group(2));
        }
        return String.join(", ", parts.stream().sorted().toList());
    }

    /** The rollout as the list shows it (status and detail status from their tooltips), or null when it is not listed. */
    private Map<String, String> rollout() {
        final Map<String, String> r = grids.records(rolloutList(), List.of("Status", "Detail status")).stream()
                .filter(x -> NAME.equals(x.get("Name"))).findFirst().orElse(null);
        if (r != null) {
            r.put("Detail status", progress(r.get("Detail status")));
        }
        return r;
    }

    /** Polls the list every second until the rollout's record satisfies {@code pred}; returns the last record read. */
    private Map<String, String> waitRollout(final Predicate<Map<String, String>> pred, final int ms) {
        final List<Map<String, String>> last = new ArrayList<>();
        last.add(null);
        final List<String> seen = new ArrayList<>();
        final long t0 = System.currentTimeMillis();
        ui.waitFor(() -> {
            last.set(0, rollout());
            final String s = status(last.get(0)) + " @" + (System.currentTimeMillis() - t0) / 1000 + "s";
            if (seen.isEmpty() || !seen.get(seen.size() - 1).startsWith(status(last.get(0)) + " @")) {
                seen.add(s);
            }
            return pred.test(last.get(0));
        }, ms, 1000);
        // the statuses the list showed while waiting, with the time each was first seen (the test output, for slow runs)
        System.out.println("rollout status sequence: " + String.join(" > ", seen));
        return last.get(0);
    }

    private static String status(final Map<String, String> r) {
        return r == null ? "(not listed)" : r.get("Status");
    }

    private Locator rolloutButton(final String action) {
        final Locator row = grids.rowNamed(rolloutList(), NAME);
        if (row == null) {
            throw new IllegalStateException("rollout " + NAME + " not listed");
        }
        return grids.cells(row).all().locator("[id^=\"rollout.action.button.id." + ACTIONS.get(action) + ".\"]").first();
    }

    /** Clicks a rollout action; the notifications until one matches {@code text}. */
    private List<String> act(final String action, final String text) {
        return ui.notificationsDuring(() -> rolloutButton(action).click(), 6000, s -> s.stream().anyMatch(t -> t.contains(text)));
    }

    // ---- the create window (its fields have placeholders, no captions)

    private void createRollout() {
        page.locator("[id=\"rollout.add.button.id\"]").click(); // "+" in the Rollouts header
        assertTrue(ui.waitFor(() -> ui.dialogOpen() && ui.dialogText().contains("Create new Rollout"), 10_000), "create window");
        ui.settle(500);
        final Locator d = ui.dialog();
        ui.byPlaceholder(d, "Name").fill(NAME);
        ui.pickByPlaceholder(d, "Distribution set", SET_SHOWN);
        ui.pickByPlaceholder(d, "Custom Target Filter", "Seed Lab devices");
        ui.chooseOption(d, "Manual");
        final Locator groups = ui.byPlaceholder(d, "Number of groups");
        groups.fill("2");
        groups.press("Tab");
        ui.settle(1500);
        final Locator trigger = ui.byPlaceholder(d, "Trigger threshold");
        final Locator error = ui.byPlaceholder(d, "Error threshold");
        trigger.fill("50");
        error.fill("50");
        error.press("Tab");
        ui.settle(500);
        assertEquals("50/50", trigger.inputValue() + "/" + error.inputValue(), "thresholds");
        final List<String> notes = ui.notificationsDuring(() -> ui.clickDialogButton("Save"), 8000,
                s -> s.stream().anyMatch(t -> t.contains("saved successfully")));
        assertTrue(notes.contains(NAME + " saved successfully"), "notifications: " + notes);
        assertTrue(ui.dialogClosed(10_000), "window closed");
    }

    /** The action history of the selected target: "set type status active=..." per action. */
    private List<String> actionHistory() {
        final Locator g = grids.grid("actionHistory");
        final List<String> heads = Grids.columnHeaders(g);
        final Locator rows = Grids.rows(g);
        final List<String> out = new ArrayList<>();
        for (int i = 0, n = rows.count(); i < n; i++) {
            final Locator cells = rows.nth(i).getByRole(AriaRole.GRIDCELL);
            final String activeClass = grids.cells(rows.nth(i)).cell(heads.indexOf("Active"))
                    .locator("[id^=\"action.history.table.activeStateId\"]").first().getAttribute("class");
            out.add(HawkbitUi.textOf(cells.nth(heads.indexOf("Distribution set"))) + " " + ui.tooltip(cells.nth(heads.indexOf("Type"))) + " "
                    + ui.tooltip(cells.nth(heads.indexOf("Status"))) + " active="
                    + (activeClass != null && activeClass.contains("statusIconActive")));
        }
        return out;
    }

    /** The status of a target as its status icon's tooltip says it ("Status pending"). */
    private String targetStatus(final String name) {
        final Locator row = grids.rowOf(grids.grid("targets"), name);
        return ui.tooltip(grids.cells(row).all().locator("[id^=\"target.table.statusId\"]").first());
    }

    @Test
    void startPauseResumeTriggerNextGroupAndDelete() {
        step(1, "create the rollout (Seed OS Base 1.0.0, Seed Lab devices, manual, 2 groups, 50/50), start it: running", () -> {
            ui.openAddress("login");
            ui.login(USER);
            openRolloutList();
            assertEquals(List.of(), grids.records(rolloutList(), List.of()), "rollouts before");
            createRollout();
            final Map<String, String> ready = waitRollout(r -> r != null && "Ready".equals(r.get("Status")), 60_000);
            assertEquals("Ready 4", ready == null ? "(not listed)" : ready.get("Status") + " " + ready.get("Targets"), "ready, 4 targets");
            final List<String> notes = act("Run", "started");
            assertTrue(notes.contains("Rollout " + NAME + " started successfully"), "notifications: " + notes);
            final Map<String, String> run = waitRollout(
                    r -> r != null && "Running".equals(r.get("Status")) && "Running : 2, Scheduled : 2".equals(r.get("Detail status")), 60_000);
            assertEquals("Running Running : 2, Scheduled : 2", run == null ? "(not listed)" : run.get("Status") + " " + run.get("Detail status"),
                    "running");
        });

        step(2, "pause it: notification, status paused", () -> {
            final List<String> notes = act("Pause", "paused");
            assertTrue(notes.contains("Rollout " + NAME + " paused successfully"), "notifications: " + notes);
            assertEquals("Paused", status(waitRollout(r -> r != null && "Paused".equals(r.get("Status")), 30_000)), "status");
        });

        step(3, "resume it: notification, status running", () -> {
            // resume is the same Run button as start
            final List<String> notes = act("Run", "resumed");
            assertTrue(notes.contains("Rollout " + NAME + " resumed successfully"), "notifications: " + notes);
            assertEquals("Running", status(waitRollout(r -> r != null && "Running".equals(r.get("Status")), 30_000)), "status");
        });

        step(4, "trigger the next group: notification, both groups running, the four Lab targets pending", () -> {
            rolloutButton("Trigger next group").click();
            ui.settle(800);
            final String confirmation = ui.dialogText();
            assertTrue(confirmation.startsWith("Trigger next rollout group") && confirmation.contains("You are about to trigger the next rollout group"),
                    "confirmation: " + confirmation);
            final List<String> notes = ui.notificationsDuring(() -> ui.clickDialogButton("OK"), 6000,
                    s -> s.stream().anyMatch(t -> t.contains("triggered")));
            assertTrue(notes.contains("Next group successfully triggered"), "notifications: " + notes);
            final Map<String, String> r = waitRollout(x -> x != null && "Running : 4".equals(x.get("Detail status")), 30_000);
            assertEquals("Running : 4", r == null ? "(not listed)" : r.get("Detail status"), "progress");
            ui.buttonNamed(NAME).click(); // the rollout's name opens its groups
            ui.settle(1000);
            final List<String> groups = grids.records(grids.byColumns(GROUP_COLUMNS, null, 15_000), List.of("Status")).stream()
                    .map(x -> x.get("Name") + " " + x.get("Status") + " " + x.get("Targets")).toList();
            assertEquals(List.of("group-1 Running 2", "group-2 Running 2"), groups, "groups");
            ui.goTo("Deployment");
            final List<String> statuses = new ArrayList<>();
            for (final String t : LAB) {
                final String s = ui.waitForValue(() -> "Status pending".equals(targetStatus(t)) ? "Status pending" : null, 20_000, 250);
                statuses.add(s != null ? s : targetStatus(t));
            }
            assertEquals(List.of("Status pending", "Status pending", "Status pending", "Status pending"), statuses, "Lab targets");
        });

        step(5, "delete it: the confirmation counts running and scheduled updates; deleted, gone within 60 s", () -> {
            openRolloutList();
            rolloutButton("Delete..").click();
            ui.settle(800);
            final String confirmation = ui.dialogText();
            assertTrue(confirmation.contains("Are you sure you want to delete Rollout " + NAME + "?")
                    && confirmation.contains("There are 4 running updates that will continue and 0 scheduled updates that will terminate."),
                    "confirmation: " + confirmation);
            final List<String> notes = ui.notificationsDuring(() -> ui.clickDialogButton("OK"), 6000,
                    s -> s.stream().anyMatch(t -> t.contains("deleted")));
            assertTrue(notes.contains("Rollout " + NAME + " deleted successfully"), "notifications: " + notes);
            assertEquals("(not listed)", status(waitRollout(x -> x == null, 60_000)), "rollout after deleting");
        });

        step(6, "Seed Device 01's action history still lists its Seed OS Base 1.0.0 action", () -> {
            ui.goTo("Deployment");
            grids.selectRow(grids.rowOf(grids.grid("targets"), LAB.get(0)));
            assertEquals(List.of(SET_SHOWN + " Forced Running active=true"), actionHistory(), "actions");
        });
    }
}
