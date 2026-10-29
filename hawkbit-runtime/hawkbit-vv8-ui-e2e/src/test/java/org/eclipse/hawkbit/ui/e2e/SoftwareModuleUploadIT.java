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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

import com.microsoft.playwright.FileChooser;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import org.junit.jupiter.api.Test;

/**
 * Software modules and artifacts in the Upload view: create a module, upload one file with the upload button and one
 * by dropping it on the drop area, see each upload finish in the upload status window and both files listed with
 * their sizes and checksums, also after leaving the view. Seed state: five modules, no artifacts. The two files are
 * small fixtures in this module's test resources ({@code upload/}).
 */
class SoftwareModuleUploadIT extends AbstractUiE2eTest {

    private static final String USER = "admin";
    private static final String TYPE = "Application";
    private static final String NAME = "Workflow Tool";
    private static final String VERSION = "1.0.0";
    private static final String VENDOR = "Workflow QA";
    private static final String MODULE_ROW = NAME + " " + VERSION;
    private static final String MODULE_SHOWN = NAME + ":" + VERSION;
    private static final List<String> SEED_MODULES = List.of("Seed Agent 1.0.0", "Seed App 1.0.0", "Seed App 1.1.0", "Seed OS 1.0.0",
            "Seed OS 2.0.0");
    private static final Fixture A = Fixture.of("e2e-artifact-a.txt");
    private static final Fixture B = Fixture.of("e2e-artifact-b.bin");

    /** A fixture file with its size and checksums as the app shows them (decimal bytes, lower-case hex). */
    private record Fixture(Path path, String file, String size, String sha1, String md5, String sha256) {

        static Fixture of(final String name) {
            try {
                final Path p = Paths.get(Objects.requireNonNull(SoftwareModuleUploadIT.class.getResource("/upload/" + name), name).toURI());
                final byte[] b = Files.readAllBytes(p);
                return new Fixture(p, name, String.valueOf(b.length), hash("SHA-1", b), hash("MD5", b), hash("SHA-256", b));
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            } catch (final URISyntaxException e) {
                throw new IllegalStateException(e);
            }
        }

        private static String hash(final String algorithm, final byte[] b) {
            try {
                return HexFormat.of().formatHex(MessageDigest.getInstance(algorithm).digest(b));
            } catch (final NoSuchAlgorithmException e) {
                throw new IllegalStateException(e);
            }
        }

        /** As the maximised artifact list shows it: size and SHA-1. */
        String listed() {
            return file + " " + size + " " + sha1;
        }

        /** Size and all three checksums. */
        String listedFull() {
            return file + " " + size + " " + sha1 + " " + md5 + " " + sha256;
        }
    }

    // ---- the Upload view (icon-only buttons and the drop area by their component ids: they have no accessible name)

    private Locator moduleList() {
        return grids.grid("softwareModules");
    }

    private void uploadWithButton(final Path file) {
        final FileChooser chooser = page.waitForFileChooser(new Page.WaitForFileChooserOptions().setTimeout(10_000),
                () -> page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Upload File")).click());
        chooser.setFiles(file);
    }

    /** Drops a file onto the drop area, as dragging it from the desktop does (an HTML5 file drop). */
    private void dropFile(final Path file) throws IOException {
        page.locator("[id=\"artficat.upload.filedrop.layout\"]").evaluate("(el, f) => { const bin = atob(f.b64);"
                + " const u = new Uint8Array(bin.length); for (let i = 0; i < bin.length; i++) u[i] = bin.charCodeAt(i);"
                + " const dt = new DataTransfer(); dt.items.add(new File([u], f.name, { type: 'application/octet-stream' }));"
                + " for (const type of ['dragenter', 'dragover', 'drop'])"
                + " el.dispatchEvent(new DragEvent(type, { bubbles: true, cancelable: true, composed: true, dataTransfer: dt })); }",
                Map.of("name", file.getFileName().toString(), "b64", Base64.getEncoder().encodeToString(Files.readAllBytes(file))));
    }

    /** The upload status window's rows ("file module status [reason]") once every upload in it has ended (30 s at most). */
    private String uploadStatus() {
        final List<Map<String, String>> recs = ui.waitForValue(() -> {
            if (!ui.dialogOpen()) {
                return null;
            }
            final Locator g = grids.byColumns(List.of("Status", "Progress", "File name"), ui.dialog());
            if (g == null) {
                return null;
            }
            final List<Map<String, String>> r = grids.records(g, List.of("Status"));
            return !r.isEmpty() && r.stream().allMatch(x -> Pattern.compile("Finished|Failed", Pattern.CASE_INSENSITIVE)
                    .matcher(x.getOrDefault("Status", "")).find()) ? r : null;
        }, 30_000, 500);
        return recs == null ? "(not ended)" : String.join("; ", recs.stream().map(r -> (r.get("File name") + " " + r.get("Software Module") + " "
                + r.get("Status") + " " + r.getOrDefault("Reason", "")).trim()).toList());
    }

    private void closeUploadStatus() {
        page.locator("[id=\"artifact.upload.close.button.id\"]").click();
        ui.settle(500);
    }

    /** The artifacts of the selected module, read with the list maximised (only then are the checksums shown). */
    private List<Map<String, String>> artifacts() {
        final Locator max = page.locator("[id=\"expand.artifact.details\"]");
        max.click();
        ui.settle(800);
        final List<Map<String, String>> recs = grids.records(grids.grid("artifacts"), List.of());
        max.click();
        ui.settle(800);
        return recs;
    }

    private static String listed(final List<Map<String, String>> arts, final boolean full) {
        return String.join("; ", arts.stream().map(a -> a.get("File name") + " " + a.get("Size(B)") + " " + a.get("SHA1 checksum")
                + (full ? " " + a.get("MD5 checksum") + " " + a.get("SHA256 checksum") : "")).sorted().toList());
    }

    @Test
    void createModuleAndUploadArtifacts() {
        step(1, "sign in and open Upload: the 5 seed modules", () -> {
            ui.openAddress("login");
            ui.login(USER);
            ui.goTo("Upload");
            assertEquals("Upload", ui.currentView(), "view");
            assertEquals(SEED_MODULES, grids.rowTexts(moduleList()), "modules");
        });

        step(2, "create the module Workflow Tool 1.0.0 (Application): success notification, it is listed", () -> {
            page.locator("[id=\"sw.module.add.button\"]").click(); // "+" ("Create..") in the list's header
            ui.settle(800);
            assertTrue(ui.dialogText().startsWith("Create new Software Module"), "window: " + ui.dialogText());
            final Locator d = ui.dialog();
            assertEquals(TYPE, ui.pickFromCombo(d, "Type", TYPE), "type");
            d.getByLabel("Name").fill(NAME);
            d.getByLabel("Version").fill(VERSION);
            d.getByLabel("Vendor").fill(VENDOR);
            final List<String> notes = ui.notificationsDuring(() -> ui.clickDialogButton("Save"), 6000,
                    s -> s.stream().anyMatch(t -> t.contains("saved")));
            assertTrue(notes.contains(MODULE_SHOWN + " saved successfully"), "notifications: " + notes);
            assertTrue(grids.rowOf(moduleList(), t -> t.contains(MODULE_ROW)) != null, "module listed");
        });

        step(3, "upload file A with the button: Finished in the status window; listed with its size and SHA-1", () -> {
            grids.selectRow(grids.rowOf(moduleList(), MODULE_ROW));
            assertTrue(Pattern.compile("Artifact Details of ?" + Pattern.quote(MODULE_SHOWN)).matcher(ui.pageText()).find(),
                    "artifact panel of the module");
            uploadWithButton(A.path());
            assertEquals(A.file() + " " + MODULE_SHOWN + " Finished", uploadStatus(), "upload status");
            closeUploadStatus();
            assertEquals(A.listed(), listed(artifacts(), false), "artifacts");
        });

        step(4, "drop file B on the drop area: Finished in the status window; listed behind it", () -> {
            dropFile(B.path());
            assertEquals(B.file() + " " + MODULE_SHOWN + " Finished", uploadStatus(), "upload status");
            assertTrue(ui.waitFor(() -> grids.rowOf(grids.grid("artifacts"), t -> t.contains(B.file())) != null, 10_000),
                    "file B listed behind the window");
        });

        step(5, "close the status window: upload available again, both files with sizes and SHA-1", () -> {
            closeUploadStatus();
            assertFalse(ui.dialogOpen(), "window open");
            assertTrue(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Upload File")).isEnabled(), "upload button enabled");
            assertEquals(String.join("; ", List.of(A.listed(), B.listed()).stream().sorted().toList()), listed(artifacts(), false), "artifacts");
        });

        step(6, "leave for Distributions and come back: both artifacts still listed, with all checksums", () -> {
            ui.goTo("Distributions");
            assertEquals("Distributions", ui.currentView(), "view");
            ui.goTo("Upload");
            grids.selectRow(grids.rowOf(moduleList(), MODULE_ROW));
            assertEquals(String.join("; ", List.of(A.listedFull(), B.listedFull()).stream().sorted().toList()), listed(artifacts(), true),
                    "artifacts");
        });
    }
}
