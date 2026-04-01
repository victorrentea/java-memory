package com.victorrentea.mat.tests;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.swt.widgets.ToolItem;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeItem;
import org.eclipse.swtbot.eclipse.finder.SWTWorkbenchBot;
import org.eclipse.swtbot.swt.finder.junit.SWTBotJunit4ClassRunner;
import org.eclipse.swtbot.swt.finder.utils.SWTBotPreferences;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotShell;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.ide.IDE;
import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;

import static org.junit.Assert.*;

@RunWith(SWTBotJunit4ClassRunner.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class MatBasicTest {

    private static SWTWorkbenchBot bot;
    private static final String HEAP_DUMP_PATH =
            System.getProperty("user.home") + "/workspace/mat-automate/test-data/Leak3_SubList.hprof";
    private static final String SCREENSHOT_DIR =
            System.getProperty("user.home") + "/workspace/mat-automate/target/screenshots";

    private static <T> T pollFor(String desc, long timeoutMs, java.util.function.Supplier<T> check) throws Exception {
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < timeoutMs) {
            T result = check.get();
            if (result != null) {
                System.out.println("[TEST] " + desc + " (" + (System.currentTimeMillis() - start) + "ms)");
                return result;
            }
            Thread.sleep(200);
        }
        fail(desc + " timed out after " + timeoutMs + "ms");
        return null;
    }

    @BeforeClass
    public static void setUp() throws Exception {
        SWTBotPreferences.TIMEOUT = 15000;
        SWTBotPreferences.PLAYBACK_DELAY = 50;
        SWTBotPreferences.KEYBOARD_LAYOUT = "EN_US";
        new File(SCREENSHOT_DIR).mkdirs();
        bot = new SWTWorkbenchBot();

        pollFor("Shell", 10000, () -> {
            AtomicReference<String> t = new AtomicReference<>();
            Display.getDefault().syncExec(() -> {
                for (Shell s : Display.getDefault().getShells()) {
                    if (!s.isDisposed() && s.isVisible()) {
                        t.set(s.getText()); s.forceActive(); s.setFocus(); break;
                    }
                }
            });
            return (t.get() != null && !t.get().isEmpty()) ? t.get() : null;
        });
        for (String v : new String[]{"Welcome", "Getting Started"}) {
            try { bot.viewByTitle(v).close(); } catch (Exception e) { }
        }

        System.out.println("[TEST] Opening: " + HEAP_DUMP_PATH);
        Display.getDefault().syncExec(() -> {
            try {
                IFileStore fs = EFS.getLocalFileSystem().getStore(
                        new org.eclipse.core.runtime.Path(new File(HEAP_DUMP_PATH).getAbsolutePath()));
                IWorkbenchPage page = PlatformUI.getWorkbench()
                        .getActiveWorkbenchWindow().getActivePage();
                IDE.openEditorOnFileStore(page, fs);
            } catch (Exception e) { System.out.println("[TEST] Open failed: " + e); }
        });
        pollFor("Parse complete", 60000, () -> {
            try { return bot.shell("Getting Started Wizard"); }
            catch (Exception e) { return null; }
        });
        bot.shell("Getting Started Wizard").activate();
        bot.button("Cancel").click();
        System.out.println("[TEST] Heap dump ready");
    }

    static void takeScreenshot(String name) {
        try {
            BufferedImage capture = new Robot().createScreenCapture(
                    new Rectangle(Toolkit.getDefaultToolkit().getScreenSize()));
            File f = new File(SCREENSHOT_DIR, name + ".png");
            ImageIO.write(capture, "png", f);
            System.out.println("[TEST] Screenshot: " + f.getName());
        } catch (Exception e) { }
    }

    private static boolean clickToolbar(String tooltip) {
        AtomicReference<Boolean> clicked = new AtomicReference<>(false);
        Display.getDefault().syncExec(() -> {
            for (Shell s : Display.getDefault().getShells()) {
                if (!s.isDisposed() && s.isVisible() && doClickToolbar(s, tooltip)) {
                    clicked.set(true); return;
                }
            }
        });
        return clicked.get();
    }
    private static boolean doClickToolbar(Composite parent, String tooltip) {
        for (Control child : parent.getChildren()) {
            if (child instanceof ToolBar) {
                for (ToolItem ti : ((ToolBar) child).getItems()) {
                    if (tooltip.equals(ti.getToolTipText())) {
                        Event e = new Event(); e.widget = ti;
                        ti.notifyListeners(SWT.Selection, e);
                        return true;
                    }
                }
            }
            if (child instanceof Composite && doClickToolbar((Composite) child, tooltip)) return true;
        }
        return false;
    }

    /** Read the largest visible Tree with >1 columns, excluding Error Log. */
    private static List<String[]> readVisibleTree(int maxRows) {
        List<String[]> rows = new ArrayList<>();
        Display.getDefault().syncExec(() -> {
            List<Tree> trees = new ArrayList<>();
            for (Shell s : Display.getDefault().getShells()) {
                if (!s.isDisposed() && s.isVisible()) findAllTrees(s, trees);
            }
            Tree best = null;
            for (Tree tree : trees) {
                if (tree.getColumnCount() > 1 && tree.getItemCount() > 0 && tree.isVisible()) {
                    // Skip the Error Log tree
                    String col0 = tree.getColumn(0).getText();
                    if ("Message".equals(col0)) continue;
                    if (best == null || tree.getItemCount() > best.getItemCount()) best = tree;
                }
            }
            if (best == null) { System.out.println("[TEST] No visible data tree"); return; }

            int numCols = best.getColumnCount();
            StringBuilder hdr = new StringBuilder("[TEST]   ");
            for (int c = 0; c < numCols; c++) {
                if (c > 0) hdr.append(" | ");
                hdr.append(best.getColumn(c).getText());
            }
            System.out.println(hdr);

            TreeItem[] items = best.getItems();
            for (int i = 0; i < Math.min(maxRows, items.length); i++) {
                String[] cols = new String[numCols];
                for (int c = 0; c < numCols; c++) cols[c] = items[i].getText(c);
                rows.add(cols);
            }
        });
        return rows;
    }

    private static void findAllTrees(Composite parent, List<Tree> result) {
        for (Control child : parent.getChildren()) {
            if (child instanceof Tree) result.add((Tree) child);
            if (child instanceof Composite) findAllTrees((Composite) child, result);
        }
    }

    private static void printRows(List<String[]> rows) {
        for (String[] row : rows) {
            StringBuilder sb = new StringBuilder("[TEST]   ");
            for (int i = 0; i < row.length; i++) {
                if (i > 0) sb.append(" | ");
                sb.append(row[i]);
            }
            System.out.println(sb);
        }
    }

    /** Set text in the first visible editable StyledText. */
    private static boolean setOqlText(String text) {
        AtomicReference<Boolean> ok = new AtomicReference<>(false);
        Display.getDefault().syncExec(() -> {
            List<StyledText> widgets = new ArrayList<>();
            for (Shell s : Display.getDefault().getShells()) {
                if (!s.isDisposed() && s.isVisible()) findStyledTexts(s, widgets);
            }
            for (StyledText st : widgets) {
                if (st.getEditable() && st.isVisible()) {
                    st.setText(text);
                    ok.set(true);
                    return;
                }
            }
        });
        return ok.get();
    }
    private static void findStyledTexts(Composite parent, List<StyledText> result) {
        for (Control child : parent.getChildren()) {
            if (child instanceof StyledText) result.add((StyledText) child);
            if (child instanceof Composite) findStyledTexts((Composite) child, result);
        }
    }

    @Test
    public void test01_histogram() throws Exception {
        System.out.println("[TEST] === Step 1: Histogram ===");
        clickToolbar("Create a histogram from an arbitrary set of objects.");
        Thread.sleep(2000);

        List<String[]> rows = readVisibleTree(25);
        printRows(rows);
        takeScreenshot("01_histogram");
    }

    private void executeOql(String query, String screenshotName, int waitSec) throws Exception {
        clickToolbar("Open Object Query Language studio to execute statements");
        Thread.sleep(500);
        assertTrue("Should set OQL text", setOqlText(query));
        System.out.println("[TEST] OQL: " + query);
        Thread.sleep(300);
        clickToolbar("Execute Query (F5)");

        // Poll for results (exclude Error Log tree)
        pollFor("OQL results", waitSec * 1000L, () -> {
            List<String[]> r = readVisibleTree(1);
            return r.isEmpty() ? null : r;
        });

        List<String[]> rows = readVisibleTree(20);
        System.out.println("[TEST] Results (" + rows.size() + " rows):");
        printRows(rows);
        takeScreenshot(screenshotName);
    }

    @Test
    public void test02_oqlSubListCount() throws Exception {
        System.out.println("[TEST] === Step 2: OQL — How many SubLists? ===");
        // Simple count query — fast
        executeOql(
            "SELECT count(*) FROM java.util.ArrayList$SubList",
            "02_oql_count", 30);
    }

    @Test
    public void test03_oqlSubListDetails() throws Exception {
        System.out.println("[TEST] === Step 3: OQL — SubList details (first 10) ===");
        // Limit results for speed
        executeOql(
            "SELECT s.@objectId, s.@retainedHeapSize, s.size, s.parent.size FROM java.util.ArrayList$SubList s WHERE s.@retainedHeapSize > 100",
            "03_oql_details", 60);
    }

    @Test
    public void test04_dominatorTree() throws Exception {
        System.out.println("[TEST] === Step 4: Dominator Tree ===");
        clickToolbar("Open Dominator Tree for entire heap.");
        Thread.sleep(2000);

        List<String[]> rows = readVisibleTree(15);
        System.out.println("[TEST] Dominator top 15:");
        printRows(rows);
        takeScreenshot("04_dominator");
    }
}
