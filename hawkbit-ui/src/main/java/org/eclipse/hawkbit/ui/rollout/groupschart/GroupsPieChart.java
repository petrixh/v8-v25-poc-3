/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hawkbit.ui.rollout.groupschart;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.vaadin.flow.component.html.Div;

/**
 * Draws a pie charts for the provided groups.
 * <p>
 * Vaadin 8 drew it in the browser with a GWT widget and d3 (A7). On Flow the
 * same SVG is built on the server: 260x200 px, radius 90, one slice for the
 * unassigned targets followed by one per group, clockwise from 12 o'clock. The
 * slices keep the {@code pie} class in the same order (the theme colours them
 * by position). Hovering a slice shows "Unassigned: n" or "i: count" at the
 * slice's centroid, as before; the label is shown by a CSS rule instead of a
 * mouse listener.
 */
public class GroupsPieChart extends Div {

    private static final long serialVersionUID = 1311542227339430098L;

    private static final int WIDTH = 260;
    private static final int HEIGHT = 200;
    private static final int OUTER_RADIUS = 90;
    // d3 arc centroid: middle of inner (0) and outer radius
    private static final double CENTROID_RADIUS = OUTER_RADIUS / 2.0;
    // the browser measured the label text; here it is estimated for the 12px
    // bold label font of the theme
    private static final double LABEL_CHAR_WIDTH = 7.2;
    private static final double LABEL_HEIGHT = 14;

    private List<Long> groupTargetCounts;
    private Long totalTargetCount;

    /**
     * Constructor: an empty chart until the state is set.
     */
    public GroupsPieChart() {
        draw();
    }

    /**
     * Updates the state of the chart
     * 
     * @param totalTargetsCount
     *            total count of targets that are represented by the pie
     * @param groupTargetCounts
     *            list of target counts
     */
    public void setChartState(final Long totalTargetsCount, final List<Long> groupTargetCounts) {
        this.groupTargetCounts = groupTargetCounts;
        this.totalTargetCount = totalTargetsCount;
        draw();
    }

    private void draw() {
        // only numbers and fixed text go into the markup
        getElement().setProperty("innerHTML", buildSvg());
    }

    private String buildSvg() {
        final StringBuilder slices = new StringBuilder();
        final StringBuilder labels = new StringBuilder();
        final StringBuilder hoverRules = new StringBuilder();

        if (groupTargetCounts != null && totalTargetCount != null && totalTargetCount > 0) {
            long sum = 0;
            for (final Long targetCount : groupTargetCounts) {
                sum += valueOf(targetCount);
            }
            final long unassignedTargets = totalTargetCount - sum;

            final List<String> texts = new ArrayList<>();
            final List<Long> counts = new ArrayList<>();
            texts.add("Unassigned: " + unassignedTargets);
            counts.add(unassignedTargets);
            for (int i = 0; i < groupTargetCounts.size(); i++) {
                texts.add((i + 1) + ": " + groupTargetCounts.get(i));
                counts.add(valueOf(groupTargetCounts.get(i)));
            }

            double startAngle = 0;
            for (int index = 0; index < counts.size(); index++) {
                final double endAngle = startAngle
                        + counts.get(index).doubleValue() / totalTargetCount.doubleValue() * 2 * Math.PI;
                slices.append("<path class=\"pie pie-").append(index).append("\" d=\"")
                        .append(arcPath(startAngle, endAngle)).append("\"></path>");
                labels.append(label(index, texts.get(index), startAngle, endAngle));
                hoverRules.append(".groups-pie-chart-svg .pie-").append(index).append(":hover ~ .pie-info-")
                        .append(index).append("{visibility:visible}");
                startAngle = endAngle;
            }
        }

        return "<svg class=\"groups-pie-chart-svg\" width=\"" + WIDTH + "\" height=\"" + HEIGHT + "\">"
                + "<style>.groups-pie-chart-svg .pie-info{visibility:hidden;pointer-events:none}" + hoverRules
                + "</style><g transform=\"translate(" + fmt(WIDTH / 2.0) + ", " + fmt(HEIGHT / 2.0) + ")\"><g>"
                + slices + labels + "</g></g></svg>";
    }

    private static long valueOf(final Long count) {
        return count != null ? count : 0L;
    }

    // d3 v3 arc with inner radius 0: angles clockwise from 12 o'clock
    private static String arcPath(final double startAngle, final double endAngle) {
        final double r = OUTER_RADIUS;
        if (endAngle - startAngle >= 2 * Math.PI - 1e-6) {
            return "M0," + fmt(r) + "A" + fmt(r) + "," + fmt(r) + " 0 1,1 0," + fmt(-r) + "A" + fmt(r) + ","
                    + fmt(r) + " 0 1,1 0," + fmt(r) + "Z";
        }
        final double x0 = r * Math.sin(startAngle);
        final double y0 = -r * Math.cos(startAngle);
        final double x1 = r * Math.sin(endAngle);
        final double y1 = -r * Math.cos(endAngle);
        final int largeArc = endAngle - startAngle > Math.PI ? 1 : 0;
        return "M" + fmt(x0) + "," + fmt(y0) + "A" + fmt(r) + "," + fmt(r) + " 0 " + largeArc + ",1 " + fmt(x1)
                + "," + fmt(y1) + "L0,0Z";
    }

    private static String label(final int index, final String text, final double startAngle,
            final double endAngle) {
        final double angle = (startAngle + endAngle) / 2 - Math.PI / 2;
        final double x = Math.cos(angle) * CENTROID_RADIUS;
        final double y = Math.sin(angle) * CENTROID_RADIUS;
        final double width = text.length() * LABEL_CHAR_WIDTH * 1.1;
        return "<g class=\"pie-info pie-info-" + index + "\" transform=\"translate(" + fmt(x) + ", " + fmt(y)
                + ")\"><rect width=\"" + fmt(width) + "\" height=\"" + fmt(LABEL_HEIGHT) + "\" transform=\"translate("
                + fmt(-width / 2.0) + ", " + fmt(-LABEL_HEIGHT * 0.8) + ")\"></rect><text text-anchor=\"middle\">"
                + text + "</text></g>";
    }

    private static String fmt(final double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }
}
