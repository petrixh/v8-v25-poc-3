/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hawkbit.ui.common;

import java.awt.Color;
import java.io.Serializable;

/**
 * Converts 2d-coordinates to a Color.
 */
public class CoordinatesToColor implements Serializable {
    private static final long serialVersionUID = 1L;

    // Vaadin 8: implemented Coordinates2Color of the colour picker gradient;
    // Vaadin 25 has no colour picker (A10), the colour is java.awt.Color

    public Color calculate(final int x, final int y) {
        return calculateHSVColor(x, y);
    }

    public int[] calculate(final Color color) {
        final float[] hsv = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
        final int x = (int) Math.round(hsv[0] * 220.0);
        final int y = calculateYCoordinateOfColor(hsv);
        return new int[] { x, y };
    }

    private static Color calculateHSVColor(final int x, final int y) {
        final float h = (float) (x / 220.0);
        float s = 1F;
        float v = 1F;
        if (y < 110) {
            s = (float) (y / 110.0);
        } else if (y > 110) {
            v = (float) (1.0 - (y - 110.0) / 110.0);
        }
        return new Color(Color.HSBtoRGB(h, s, v));
    }

    private static int calculateYCoordinateOfColor(final float[] hsv) {
        int y;
        // lower half
        /* Assuming hsv[] array value will have in the range of 0 to 1 */
        if (hsv[1] < 1F) {
            y = (int) Math.round(hsv[1] * 110.0);
        } else {
            y = (int) Math.round(110.0 - ((double) hsv[1] + (double) hsv[2]) * 110.0);
        }
        return y;
    }

}
