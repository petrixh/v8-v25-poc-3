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

import static org.eclipse.hawkbit.ui.e2e.HawkbitUi.textOf;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import com.microsoft.playwright.JSHandle;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Mouse;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.BoundingBox;
import com.microsoft.playwright.options.KeyboardModifier;

/**
 * The lists (grids) of the hawkBit UI as a user sees them: found by their column headers, rows by their text, cells
 * read through the slots of the grid's cells, rows selected and dragged with the mouse. A grid shows only the rows in
 * view (virtual scrolling): reading all rows pages through it with the grid's own {@code scrollToIndex}, the way a user
 * scrolls. The grid's table has the role {@code treegrid}; rows, cells and headers have their ARIA roles.
 * <p>
 * Not public Vaadin API: the helpers rely on the internal table structure of {@code vaadin-grid}'s shadow DOM: the
 * {@code table} (whether the grid scrolls), {@code thead} and {@code tbody} (headers and rows), and the {@code td}
 * cells with their slotted content (cell text and components), as well as the grid's {@code #scroller}, whose
 * {@code scrolling} attribute {@link HawkbitUi} reads. The other non-public dependencies are listed in {@link HawkbitUi}.
 * Check all of them after a Vaadin update.
 */
final class Grids {

    /** Column headers by which the lists are found. */
    private static final Map<String, List<String>> COLUMNS = Map.of("targets", List.of("Name", "Status", "Actions"), "distributionSets",
            List.of("Name", "Version", "Actions"), "actionHistory", List.of("Active", "Distribution set", "Date and time"), "softwareModules",
            List.of("Name", "Version", "Delete"), "artifacts", List.of("File name", "Size(B)"), "uploadStatus",
            List.of("Status", "Progress", "File name"));

    /** Repeats of a drag that ended without a drop (a user drags again). */
    private static final int DRAG_RETRIES = 2;

    private final Page page;
    private final HawkbitUi ui;
    private int tagSeq;

    Grids(final Page page, final HawkbitUi ui) {
        this.page = page;
        this.ui = ui;
    }

    // ---- finding grids

    /** The first grid in {@code scope} (the page when null) whose column headers include all of {@code cols}, or null. */
    Locator byColumns(final List<String> cols, final Locator scope) {
        final Locator grids = scope == null ? page.getByRole(AriaRole.TREEGRID) : scope.getByRole(AriaRole.TREEGRID);
        for (int i = 0, n = grids.count(); i < n; i++) {
            final Locator g = grids.nth(i);
            String head;
            try {
                head = textOf(g.locator("thead"), 3000);
            } catch (final PlaywrightException e) {
                head = "";
            }
            final String h = head;
            if (cols.stream().allMatch(h::contains)) {
                return g;
            }
        }
        return null;
    }

    /** A grid by its columns, waiting up to {@code ms}; fails when it is not there. */
    Locator byColumns(final List<String> cols, final Locator scope, final int ms) {
        final Locator g = ui.waitForValue(() -> byColumns(cols, scope), ms, 250);
        if (g == null) {
            throw new IllegalStateException("list with the columns " + cols + " not found");
        }
        return g;
    }

    /** One of the app's lists: targets, distributionSets, actionHistory, softwareModules, artifacts, uploadStatus. */
    Locator grid(final String which) {
        return grid(which, null);
    }

    Locator grid(final String which, final Locator scope) {
        return byColumns(Objects.requireNonNull(COLUMNS.get(which), which), scope, 10_000);
    }

    // ---- rows

    static Locator rows(final Locator g) {
        return g.locator("tbody").getByRole(AriaRole.ROW);
    }

    /**
     * Runs {@code fn} on the rows in view, then pages down through the grid until {@code fn} returns non-null (which is
     * returned) or the end is reached (null), then scrolls back to the top. A grid that does not scroll is read once.
     */
    <T> T eachPage(final Locator g, final Supplier<T> fn) {
        final JSHandle host = g.evaluateHandle("t => t.getRootNode().host || t");
        final boolean scrollable = Boolean.TRUE.equals(host.evaluate(
                "h => { const t = (h.shadowRoot && h.shadowRoot.querySelector('table')) || h; return t.scrollHeight > t.clientHeight + 2; }"));
        T out = null;
        try {
            int k = 0;
            String last = null;
            for (int i = 0; i < 500; i++) {
                if (i > 0) {
                    host.evaluate("(h, k) => h.scrollToIndex(k)", k);
                    page.waitForTimeout(200);
                }
                out = fn.get();
                if (out != null || !scrollable) {
                    return out;
                }
                final String sig = String.join("|", renderedRowTexts(g));
                if (sig.equals(last)) {
                    return null;
                }
                last = sig;
                k += Math.max(1, rows(g).count() - 3);
            }
            return null;
        } finally {
            if (scrollable && out == null) {
                try {
                    host.evaluate("h => h.scrollToIndex(0)");
                } catch (final PlaywrightException e) {
                    // the grid is gone
                }
            }
            host.dispose();
        }
    }

    /** The text of a row or cell; "" when it went away meanwhile (waits 3 s at most, not Playwright's default 30 s). */
    private static String rowText(final Locator row) {
        try {
            return textOf(row, 3000);
        } catch (final PlaywrightException e) {
            return "";
        }
    }

    /** The first row of {@code g} whose text matches, searched page by page, or null. */
    Locator rowOf(final Locator g, final Predicate<String> match) {
        return eachPage(g, () -> {
            final Locator rows = rows(g);
            for (int i = 0, n = rows.count(); i < n; i++) {
                if (match.test(rowText(rows.nth(i)))) {
                    return rows.nth(i);
                }
            }
            return null;
        });
    }

    /** The first row whose text contains {@code text}; fails when there is none. */
    Locator rowOf(final Locator g, final String text) {
        return required(rowOf(g, t -> t.contains(text)), text);
    }

    /** The first row whose text matches {@code re} (find); fails when there is none. */
    Locator rowOf(final Locator g, final Pattern re) {
        return required(rowOf(g, t -> re.matcher(t).find()), re.pattern());
    }

    private static Locator required(final Locator row, final String what) {
        if (row == null) {
            throw new IllegalStateException("no row " + what);
        }
        return row;
    }

    /** The row whose first cell is exactly {@code name}, or null. */
    Locator rowNamed(final Locator g, final String name) {
        return eachPage(g, () -> {
            final Locator rows = rows(g);
            for (int i = 0, n = rows.count(); i < n; i++) {
                if (name.equals(rowText(rows.nth(i).getByRole(AriaRole.GRIDCELL).first()))) {
                    return rows.nth(i);
                }
            }
            return null;
        });
    }

    /** The texts of the rows rendered right now. */
    static List<String> renderedRowTexts(final Locator g) {
        final Locator rows = rows(g);
        final List<String> out = new ArrayList<>();
        for (int i = 0, n = rows.count(); i < n; i++) {
            final String t = rowText(rows.nth(i));
            if (!t.isEmpty()) {
                out.add(t);
            }
        }
        return out;
    }

    /** The texts of all rows, page by page, rows of overlapping pages counted once. */
    List<String> rowTexts(final Locator g) {
        final List<String> all = new ArrayList<>();
        eachPage(g, () -> {
            appendNew(all, renderedRowTexts(g));
            return null;
        });
        return all;
    }

    /** Appends {@code page} to {@code all} without the entries at its start that repeat the end of {@code all}. */
    private static <T> void appendNew(final List<T> all, final List<T> page) {
        int o = Math.min(all.size(), page.size());
        while (o > 0 && !all.subList(all.size() - o, all.size()).equals(page.subList(0, o))) {
            o--;
        }
        all.addAll(page.subList(o, page.size()));
    }

    /** The texts of the selected rows. */
    static List<String> selectedRows(final Locator g) {
        final Locator rows = g.locator("tbody").getByRole(AriaRole.ROW, new Locator.GetByRoleOptions().setSelected(true));
        final List<String> out = new ArrayList<>();
        for (int i = 0, n = rows.count(); i < n; i++) {
            final String t = textOf(rows.nth(i));
            if (!t.isEmpty()) {
                out.add(t);
            }
        }
        return out;
    }

    /** The column header texts of a grid, in column order. */
    static List<String> columnHeaders(final Locator g) {
        final Locator hs = g.locator("thead").getByRole(AriaRole.COLUMNHEADER);
        final List<String> out = new ArrayList<>();
        for (int i = 0, n = hs.count(); i < n; i++) {
            out.add(textOf(hs.nth(i)));
        }
        return out;
    }

    /**
     * All rows as records keyed by column header; the columns named in {@code tips} are read from their tooltip (icon
     * columns).
     */
    List<Map<String, String>> records(final Locator g, final List<String> tips) {
        final List<String> heads = columnHeaders(g);
        final List<Map<String, String>> all = new ArrayList<>();
        eachPage(g, () -> {
            final List<Map<String, String>> recs = new ArrayList<>();
            final Locator rows = rows(g);
            for (int i = 0, n = rows.count(); i < n; i++) {
                final Locator cells = rows.nth(i).getByRole(AriaRole.GRIDCELL);
                final Map<String, String> rec = new LinkedHashMap<>();
                for (int j = 0, cn = cells.count(); j < cn && j < heads.size(); j++) {
                    final String h = heads.get(j).isEmpty() ? "col" + j : heads.get(j);
                    rec.put(h, tips.contains(h) ? ui.tooltip(cells.nth(j)) : rowText(cells.nth(j)));
                }
                recs.add(rec);
            }
            appendNew(all, recs);
            return null;
        });
        return all;
    }

    // ---- inside a row

    /**
     * The content of a row's cells (light DOM children of the grid) as locators: {@code all()} or {@code cell(k)}. The
     * content elements are marked with a data attribute so that ordinary locators work inside them.
     */
    RowCells cells(final Locator row) {
        final String tag = "r" + (++tagSeq);
        row.evaluate("(tr, tag) => { let k = 0; for (const cell of tr.querySelectorAll('td')) {"
                + " for (const slot of cell.querySelectorAll('slot')) for (const el of slot.assignedElements({ flatten: true })) {"
                + " el.setAttribute('data-e2e-row', tag); el.setAttribute('data-e2e-col', String(k)); } k++; } }", tag);
        return new RowCells(page, tag);
    }

    record RowCells(Page page, String tag) {

        Locator all() {
            return page.locator("[data-e2e-row=\"" + tag + "\"]");
        }

        Locator cell(final int k) {
            return page.locator("[data-e2e-row=\"" + tag + "\"][data-e2e-col=\"" + k + "\"]");
        }
    }

    /** A button in a row, found by the start of its tooltip ("Delete.."), or null. */
    Locator rowButton(final Locator row, final String tip) {
        final Locator buttons = cells(row).all().getByRole(AriaRole.BUTTON);
        for (int i = 0, n = buttons.count(); i < n; i++) {
            if (ui.tooltip(buttons.nth(i)).startsWith(tip)) {
                return buttons.nth(i);
            }
        }
        return null;
    }

    /** The cell to click to select a row or start a drag: the first one with text (not the selection check box). */
    static Locator pressCell(final Locator row) {
        final Locator cells = row.getByRole(AriaRole.GRIDCELL);
        for (int k = 0, n = cells.count(); k < n; k++) {
            if (!textOf(cells.nth(k)).isEmpty()) {
                return cells.nth(k);
            }
        }
        return cells.first();
    }

    void selectRow(final Locator row) {
        pressCell(row).click();
        ui.settle();
    }

    /** Clicks a row with a modifier, as a user does to add it to the selection (Control) or select a range (Shift). */
    void clickRow(final Locator row, final KeyboardModifier modifier, final int ms) {
        pressCell(row).click(new Locator.ClickOptions().setModifiers(List.of(modifier)));
        ui.settle(ms);
    }

    // ---- drag and drop

    /** Drags one row onto another with the mouse. */
    void dragRowOnto(final Locator src, final Locator dst) {
        mouseDrag(() -> pressCell(src), () -> pressCell(dst));
    }

    /** Drags a row onto an element (a tag button). */
    void dragRowOnto(final Locator row, final Supplier<Locator> target) {
        mouseDrag(() -> pressCell(row), target);
    }

    /**
     * An HTML5 drag with the mouse: press on the source, start with a small move, glide to the target, release. A drag
     * that ended without a drop (the browser reports the drop effect "none") is repeated, as a user drags again: the
     * browser now and then cancels the first drag onto a row right after the page opened.
     */
    void mouseDrag(final Supplier<Locator> getFrom, final Supplier<Locator> getTo) {
        page.evaluate("() => { window.__e2eDrops = 0; if (!window.__e2eDropHook) { window.__e2eDropHook = true;"
                + " document.addEventListener('dragend', (e) => { if (e.dataTransfer && e.dataTransfer.dropEffect !== 'none') window.__e2eDrops++; }, true); } }");
        for (int attempt = 0; attempt <= DRAG_RETRIES; attempt++) {
            final Locator from = getFrom.get();
            final Locator to = getTo.get();
            from.scrollIntoViewIfNeeded();
            to.scrollIntoViewIfNeeded();
            final BoundingBox a = from.boundingBox();
            final BoundingBox t = to.boundingBox();
            if (a == null || t == null) {
                throw new IllegalStateException("drag: source or target not in view");
            }
            final double ax = a.x + a.width / 2;
            final double ay = a.y + a.height / 2;
            // A press must reach the source: a dialog that has just closed keeps its backdrop for its closing animation.
            if (!ui.waitFor(() -> reaches(from, ax, ay), 5000, 100)) {
                throw new IllegalStateException("drag: the source is covered");
            }
            page.evaluate("() => { const s = window.getSelection(); if (s) s.removeAllRanges(); }");
            page.mouse().move(ax, ay);
            page.mouse().down();
            page.mouse().move(ax + 6, ay + 6, new Mouse.MoveOptions().setSteps(3));
            page.waitForTimeout(600); // the drag start reaches the server before the pointer moves on
            // the upper part of the target: a grid scrolls by itself when a drag hovers near its lower edge
            page.mouse().move(t.x + t.width / 2, t.y + Math.min(8, t.height / 2), new Mouse.MoveOptions().setSteps(15));
            page.waitForTimeout(300);
            page.mouse().up();
            ui.settle(1000);
            if (ui.waitFor(() -> ((Number) page.evaluate("() => window.__e2eDrops")).intValue() > 0, 800, 100)) {
                return;
            }
            page.waitForTimeout(500);
        }
    }

    /** Whether the pointer at (x, y) reaches the element: the topmost element there is it or inside it. */
    private static boolean reaches(final Locator loc, final double x, final double y) {
        return Boolean.TRUE.equals(loc.evaluate("(el, [x, y]) => { let h = document.elementFromPoint(x, y);"
                + " while (h && h.shadowRoot) { const d = h.shadowRoot.elementFromPoint(x, y); if (!d || d === h) break; h = d; }"
                + " for (let n = h; n; n = n.assignedSlot || n.parentNode || n.host) if (n === el) return true; return false; }",
                List.of(x, y)));
    }
}
