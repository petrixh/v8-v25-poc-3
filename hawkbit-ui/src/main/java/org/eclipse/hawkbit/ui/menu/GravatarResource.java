/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hawkbit.ui.menu;

import org.springframework.util.DigestUtils;

import java.io.Serializable;

/**
 * {@link ExternalResource} for user profile pictures hosted by gravatar.
 * 
 * @see <a href="https://en.gravatar.com/site/implement/images/">Gravatar Image
 *      Requests</a>
 *
 */
public class GravatarResource implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * Construct based on given email address. Generates external resource
     * pointing to gravatar with rating "g: suitable for display on all websites
     * with any audience type." and "mystery-man" icon as backup as secure
     * request.
     * 
     * @param email
     *            to generate resource for
     */
    private final String url;

    GravatarResource(final String email) {
        url = "https://www.gravatar.com/avatar/" + DigestUtils.md5DigestAsHex(email.getBytes()) + ".jpg?s=56&r=g&d=mm";
    }

    /**
     * @return the image URL (Vaadin 25 has no ExternalResource)
     */
    public String getURL() {
        return url;
    }

}
