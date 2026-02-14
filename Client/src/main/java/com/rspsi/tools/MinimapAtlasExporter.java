package com.rspsi.tools;

import com.jagex.Client;
import com.jagex.cache.def.ObjectDefinition;
import com.jagex.cache.def.RSArea;
import com.jagex.cache.graphics.Sprite;
import com.jagex.cache.loader.config.RSAreaLoader;
import com.jagex.cache.loader.map.MapIndexLoader;
import com.jagex.cache.loader.map.MapType;
import com.jagex.cache.loader.object.ObjectDefinitionLoader;
import com.jagex.map.MapRegion;
import com.jagex.map.SceneGraph;
import com.jagex.net.ResourceResponse;
import com.jagex.util.ObjectKey;
import com.rspsi.cache.CacheFileType;
import org.displee.util.GZIPUtils;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Arrays;

/**
 * Exports minimap region strips with optional scale, stitches them,
 * and deletes intermediate slices. Includes a small Swing progress console.
 *
 * Viewport mode: each region is rendered with a full PAD_TILES margin
 * around the 64x64 core so sprites at edges are not clipped. Strips include
 * global padding; the final stitched atlas overlaps adjacent strips by
 * exactly the padding height so padding exists only once at the very top
 * and bottom (and left/right).
 */
public final class MinimapAtlasExporter {
    private MinimapAtlasExporter() {}

    private static final int MIN_RX = 1, MIN_RY = 16;
    private static final int MAX_RX = 98, MAX_RY = 162;

    private static final int HI_BASE_PX = 4;
    private static final int PAD_TILES = 5;

    private static final int WALL_COLOR = 0xFFFFFFFF;
    private static final int DOOR_COLOR = 0xFFEE0000;
    private static final int OVERLAY_BIT = 0x01000000;

    private static final long MAP_REQUEST_TIMEOUT_MS = 500;

    private static final Pattern STRIP_NAME_RE = Pattern.compile("\\.y\\s*(\\d+)-(\\d+)\\.png$", Pattern.CASE_INSENSITIVE);

    /**
     * Global map request cache: fileId -> uncompressed contents.
     * Prevents re-requesting and re-unzipping the same map multiple times.
     */
    private static final ConcurrentHashMap<Integer, byte[]> MAP_CACHE = new ConcurrentHashMap<>();

    /**
     * Pending map requests waiting for a ResourceResponse.
     * Keyed by fileId (request.getFile()).
     */
    private static final ConcurrentHashMap<Integer, MapWaiter> PENDING_MAP_REQUESTS = new ConcurrentHashMap<>();

    /**
     * Ensures our EventBus listener is only registered once.
     */
    private static final AtomicBoolean MAP_LISTENER_REGISTERED = new AtomicBoolean(false);

    /**
     * Public static subscriber class so EventBus reflection can access it.
     */
    public static final class MapEventListener {
        @Subscribe(threadMode = ThreadMode.ASYNC)
        public void onResourceResponse(ResourceResponse response) {
            try {
                if (response.getRequest().getType() != CacheFileType.MAP) {
                    return;
                }
                int fileId = response.getRequest().getFile();
                MapWaiter waiter = PENDING_MAP_REQUESTS.remove(fileId);
                if (waiter == null) {
                    // No one waiting for this fileId (or it timed out); just ignore.
                    return;
                }

                byte[] data = response.getData();
                byte[] unzipped = null;
                try {
                    unzipped = GZIPUtils.unzip(data);
                } catch (Throwable ignore) {}

                byte[] result = (unzipped != null ? unzipped : data);
                waiter.result = result;
                // Cache it so future calls to requestMapSync for this fileId are instantaneous.
                MAP_CACHE.put(fileId, result);
                waiter.latch.countDown();
            } catch (Throwable t) {
                // If anything goes wrong, make sure the waiter is released.
                // We do not rethrow because that could kill the EventBus thread.
                t.printStackTrace();
            }
        }
    }

    /**
     * Single shared EventBus listener instance.
     */
    private static final MapEventListener MAP_EVENT_LISTENER = new MapEventListener();

    /**
     * Simple holder for a waiting map request.
     */
    private static final class MapWaiter {
        final CountDownLatch latch = new CountDownLatch(1);
        volatile byte[] result;
    }

    private static void ensureMapListenerRegistered() {
        if (MAP_LISTENER_REGISTERED.compareAndSet(false, true)) {
            try {
                EventBus.getDefault().register(MAP_EVENT_LISTENER);
            } catch (Throwable t) {
                // If this fails, future map requests will still fall back to timeout.
                t.printStackTrace();
            }
        }
    }

    /**
     * Reusable per-region buffers to reduce GC thrash:
     * - HI_BUFFER: raw hi-res tile colors
     * - ARGB_BUFFER: converted ARGB for BufferedImage
     * - REGION_IMAGE: per-region BufferedImage (same size as hi buffer)
     *
     * These are resized lazily if the required hiW/hiH changes
     * (e.g. switching between 64px and 256px tile sizes).
     */
    private static int[] HI_BUFFER;
    private static int[] ARGB_BUFFER;
    private static BufferedImage REGION_IMAGE;

    private static final class ProgressConsole {
        private final JFrame frame;
        private final JProgressBar bar;
        private final JTextArea log;
        private volatile int totalSteps = 0;
        private volatile int done = 0;

        private ProgressConsole(String title) {
            frame = new JFrame(title);
            frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
            frame.setAlwaysOnTop(false);
            bar = new JProgressBar();
            bar.setStringPainted(true);
            log = new JTextArea(12, 60);
            log.setEditable(false);
            JScrollPane sp = new JScrollPane(log, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
            JPanel p = new JPanel(new BorderLayout(8,8));
            p.setBorder(BorderFactory.createEmptyBorder(8,8,8,8));
            p.add(bar, BorderLayout.NORTH);
            p.add(sp, BorderLayout.CENTER);
            frame.setContentPane(p);
            frame.pack();
            frame.setLocationByPlatform(true);
        }

        static ProgressConsole show(String title) {
            final ProgressConsole[] ref = new ProgressConsole[1];
            try {
                SwingUtilities.invokeAndWait(() -> {
                    ref[0] = new ProgressConsole(title);
                    ref[0].frame.setVisible(true);
                });
            } catch (Exception ignored) {}
            return ref[0];
        }

        void setTotal(int total) {
            this.totalSteps = Math.max(total, 0);
            SwingUtilities.invokeLater(() -> {
                bar.setIndeterminate(totalSteps == 0);
                bar.setMinimum(0);
                bar.setMaximum(Math.max(totalSteps, 1));
                bar.setValue(0);
                bar.setString("Starting...");
            });
        }

        void step() {
            done++;
            SwingUtilities.invokeLater(() -> {
                if (!bar.isIndeterminate()) {
                    bar.setValue(Math.min(done, totalSteps));
                    bar.setString(done + " / " + totalSteps);
                }
            });
        }

        void logln(String s) {
            System.out.println(s);
            SwingUtilities.invokeLater(() -> {
                log.append(s + "\n");
                log.setCaretPosition(log.getDocument().getLength());
            });
        }

        void done(String finalMsg) {
            SwingUtilities.invokeLater(() -> {
                bar.setValue(bar.getMaximum());
                bar.setString("Done");
                if (finalMsg != null && !finalMsg.isEmpty()) {
                    log.append(finalMsg + "\n");
                    log.setCaretPosition(log.getDocument().getLength());
                }
            });
        }

        void closeLater() {
            SwingUtilities.invokeLater(() -> {
                try {
                    frame.dispose();
                } catch (Throwable ignored) {}
            });
        }
    }

    private static boolean isBridgeTile(MapRegion mr, int sx, int sy) {
        return (mr.tileFlags[1][sx][sy] & 0x02) != 0;
    }

    public static File exportStripsByYAndStitch(
            File basePngFile, int plane, int rowsPerStrip, int minRx, int minRy, int maxRx, int maxRy, int tileSize) throws Exception {
        if (plane < 0 || plane > 3) throw new IllegalArgumentException("plane must be 0..3");
        if (rowsPerStrip <= 0) throw new IllegalArgumentException("rowsPerStrip must be >= 1");
        if (basePngFile == null) throw new IllegalArgumentException("basePngFile is null");
        if (tileSize != 64 && tileSize != 256) throw new IllegalArgumentException("tileSize must be 64 or 256");
        if (minRx > maxRx || minRy > maxRy) throw new IllegalArgumentException("bad bounds");
        String base = basePngFile.getAbsolutePath();
        if (!base.toLowerCase().endsWith(".png")) base += ".png";
        File baseFile = new File(base);
        final int cols = (maxRx - minRx + 1);
        final int rows = (maxRy - minRy + 1);
        ProgressConsole pc = ProgressConsole.show("Minimap Export");
        try {
            pc.setTotal(cols * rows);
            pc.logln("[Export] plane=" + plane + " window=[" + minRx + "," + minRy + "]..[" + maxRx + "," + maxRy + "]");
            pc.logln("[Export] rowsPerStrip=" + rowsPerStrip + "  tileSize=" + tileSize);
            exportStripsByY_Internal(baseFile, plane, rowsPerStrip, minRx, minRy, maxRx, maxRy, tileSize, pc);
            pc.logln("[Stitch] Stitching strips...");
            File stitched = stitchStripsAdjacentY(baseFile, rows, tileSize, pc);
            pc.logln("[Clean] Deleting strip images...");
            deleteStrips(baseFile, pc);
            pc.done("Finished.");
            return stitched;
        } finally {
            pc.closeLater();
        }
    }

    public static void exportStripsByY(File basePngFile, int plane, int rowsPerStrip) throws Exception {
        String base = basePngFile.getAbsolutePath();
        if (!base.toLowerCase().endsWith(".png")) base += ".png";
        ProgressConsole pc = ProgressConsole.show("Minimap Export");
        try {
            final int cols = (MAX_RX - MIN_RX + 1);
            final int rows = (MAX_RY - MIN_RY + 1);
            pc.setTotal(cols * rows);
            pc.logln("[Export] plane=" + plane + " window=[" + MIN_RX + "," + MIN_RY + "]..[" + MAX_RX + "," + MAX_RY + "]");
            pc.logln("[Export] rowsPerStrip=" + rowsPerStrip);
            exportStripsByY_Internal(new File(base), plane, rowsPerStrip, MIN_RX, MIN_RY, MAX_RX, MAX_RY, 64, pc);
            pc.done("Finished.");
        } finally {
            pc.closeLater();
        }
    }

    private static void exportStripsByY_Internal(
            File basePngFile, int plane, int rowsPerStrip, int minRx, int minRy, int maxRx, int maxRy,
            int coreTileSize, ProgressConsole pc) throws Exception {

        final String base = basePngFile.getAbsolutePath();
        final int cols = (maxRx - minRx + 1);
        final int rows = (maxRy - minRy + 1);
        final int stripCount = (int) Math.ceil(rows / (double) rowsPerStrip);

        // Padding is used ONLY for sampling in renderRegionViewport, not for the output geometry.
        final int padPx = PAD_TILES * (coreTileSize == 256 ? HI_BASE_PX : 1);

        // Each region contributes exactly coreTileSize x coreTileSize pixels to the strip.
        final int stripCanvasW = cols * coreTileSize;

        int remainingRows = rows;
        int currentTopRy = maxRy;

        for (int s = 0; s < stripCount; s++) {
            int thisRows = Math.min(rowsPerStrip, remainingRows);
            int yTop = currentTopRy;
            int yBottom = currentTopRy - (thisRows - 1);

            final int stripCanvasH = thisRows * coreTileSize;
            BufferedImage strip = new BufferedImage(stripCanvasW, stripCanvasH, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = strip.createGraphics();
            try {
                pc.logln("[Export] Strip " + (s + 1) + "/" + stripCount + " ry=[" + yTop + ".." + yBottom + "]");
                for (int ry = yTop; ry >= yBottom; ry--) {
                    final int rowIndexFromTop = (yTop - ry);
                    final int rowOffsetYCore = rowIndexFromTop * coreTileSize;

                    for (int rx = minRx; rx <= maxRx; rx++) {
                        if (!regionExists(rx, ry)) {
                            pc.step();
                            continue;
                        }

                        BufferedImage tileViewport = renderRegionViewport(
                                rx, ry, plane,
                                coreTileSize,
                                coreTileSize + 2 * padPx,
                                coreTileSize + 2 * padPx,
                                padPx
                        );
                        if (tileViewport != null) {
                            int drawX = (rx - minRx) * coreTileSize;
                            int drawY = rowOffsetYCore;

                            // Only draw the core 64x64 (or 256x256) area, centered in the padded image.
                            // Source rectangle is [padPx .. padPx+coreTileSize) in both X and Y.
                            g.drawImage(
                                    tileViewport,
                                    drawX,                 // dst x1
                                    drawY,                 // dst y1
                                    drawX + coreTileSize,  // dst x2
                                    drawY + coreTileSize,  // dst y2
                                    padPx,                 // src x1
                                    padPx,                 // src y1
                                    padPx + coreTileSize,  // src x2
                                    padPx + coreTileSize,  // src y2
                                    null
                            );
                        }
                        pc.step();
                    }
                    pc.logln("  row ry=" + ry + " done");
                }
            } finally {
                g.dispose();
            }

            String outName = base.substring(0, base.length() - 4) + ".y" + yTop + "-" + yBottom + ".png";
            ImageIO.write(strip, "png", new File(outName));
            pc.logln("[Export] wrote " + outName);

            remainingRows -= thisRows;
            currentTopRy = yBottom - 1;
        }
        pc.logln("[Export] complete.");
    }


    private static final class StripInfo {
        final int top, bottom;
        final File file;
        final BufferedImage img;
        StripInfo(int top, int bottom, File file, BufferedImage img) {
            this.top = top; this.bottom = bottom; this.file = file; this.img = img;
        }
    }

    private static File stitchStripsAdjacentY(File basePngFile, int totalRows, int coreTileSize, ProgressConsole pc) throws Exception {
        File dir = basePngFile.getParentFile();
        String baseName = basePngFile.getName();
        String stem = baseName.substring(0, baseName.toLowerCase().lastIndexOf(".png"));

        List<StripInfo> strips = new ArrayList<>();
        File[] pngs = dir.listFiles((d, name) ->
                name.toLowerCase().endsWith(".png") && name.toLowerCase().startsWith(stem.toLowerCase() + ".y"));
        if (pngs != null) {
            for (File f : pngs) {
                Matcher m = STRIP_NAME_RE.matcher(f.getName());
                if (!m.find()) continue;
                int top = Integer.parseInt(m.group(1));
                int bottom = Integer.parseInt(m.group(2));
                BufferedImage img;
                try {
                    img = ImageIO.read(f);
                    if (img == null) continue;
                } catch (Exception e) {
                    pc.logln("[Stitch] Skipping unreadable: " + f.getName());
                    continue;
                }
                strips.add(new StripInfo(top, bottom, f, img));
            }
        }

        if (strips.isEmpty()) {
            pc.logln("[Stitch] No strips found to stitch beside " + basePngFile.getName());
            File outEmpty = new File(basePngFile.getParentFile(), stem + "_stitched.png");
            ImageIO.write(new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB), "png", outEmpty);
            return outEmpty;
        }

        // Sort from highest ry to lowest so we stack from top of world downwards.
        Collections.sort(strips, Comparator.comparingInt((StripInfo si) -> si.top).reversed());

        int atlasW = strips.stream().mapToInt(s -> s.img.getWidth()).max().orElse(0);
        int expectedH = totalRows * coreTileSize;

        // Sum up actual heights (should match expectedH if all strips are correct).
        int actualH = strips.stream().mapToInt(s -> s.img.getHeight()).sum();
        if (actualH != expectedH) {
            pc.logln("[Stitch] Warning: expectedH=" + expectedH + " but actualH=" + actualH);
        }

        BufferedImage atlas = new BufferedImage(atlasW, actualH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = atlas.createGraphics();
        g.setComposite(AlphaComposite.SrcOver);
        try {
            int y = 0;
            for (int i = 0; i < strips.size(); i++) {
                StripInfo s = strips.get(i);
                BufferedImage img = s.img;
                g.drawImage(img, 0, y, null);
                pc.logln("[Stitch] Pasted strip " + (i + 1) + " (ry " + s.top + "-" + s.bottom + ") at y=" + y);
                y += img.getHeight();
            }
        } finally {
            g.dispose();
        }

        File out = new File(basePngFile.getParentFile(), stem + "_stitched.png");
        ImageIO.write(atlas, "png", out);
        pc.logln("[Stitch] Wrote atlas: " + out.getAbsolutePath());
        return out;
    }


    private static void deleteStrips(File basePngFile, ProgressConsole pc) {
        File dir = basePngFile.getParentFile();
        String baseName = basePngFile.getName();
        String stem = baseName.substring(0, baseName.toLowerCase().lastIndexOf(".png"));
        File[] pngs = dir.listFiles((d, name) ->
                name.toLowerCase().endsWith(".png") && name.toLowerCase().startsWith(stem.toLowerCase() + ".y"));
        if (pngs == null) return;
        for (File f : pngs) {
            if (f.delete()) {
                pc.logln("[Clean] Deleted " + f.getName());
            } else {
                pc.logln("[Clean] Could not delete " + f.getName());
            }
        }
    }

    private static boolean regionExists(int rx, int ry) {
        return MapIndexLoader.resolve(rx, ry, MapType.LANDSCAPE) != -1;
    }

    /**
     * Create a translucent red tile for a failed region.
     * Shows a red 256x256 (or coreTileSize) square in the core area, with padding left transparent.
     */
    private static BufferedImage createMissingRegionImage(int coreTileSize, int padPx) {
        int regionSize = coreTileSize + 2 * padPx;
        BufferedImage img = new BufferedImage(regionSize, regionSize, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setComposite(AlphaComposite.SrcOver);
            // Translucent red
            g.setColor(new Color(255, 0, 0, 96));
            g.fillRect(padPx, padPx, coreTileSize, coreTileSize);
        } finally {
            g.dispose();
        }
        return img;
    }

    private static BufferedImage renderRegionViewport(int rx, int ry, int plane,
                                                      int coreTileSize, int regionW, int regionH, int padPx) throws Exception {
        int landId = MapIndexLoader.getLandscapeId(rx, ry);
        if (landId == -1) {
            // Region truly does not exist in index, skip entirely.
            return null;
        }

        byte[] land;
        try {
            land = requestMapSync(landId, (rx << 8) | ry);
        } catch (Throwable t) {
            System.out.println("[Export] Exception fetching land for region " + rx + "," + ry + ": " + t);
            return createMissingRegionImage(coreTileSize, padPx);
        }

        if (land == null) {
            // Timed out or failed to fetch: draw red box for this region
            return createMissingRegionImage(coreTileSize, padPx);
        }

        try {
            int objId = MapIndexLoader.resolve(rx, ry, MapType.OBJECT);
            byte[] obj = (objId != -1) ? requestMapSync(objId, (rx << 8) | ry) : null;

            // Neighbors: allowed to fail silently, they just come back null
            byte[] nLand = safeLoadNeighbor(rx, ry + 1, MapType.LANDSCAPE);
            byte[] sLand = safeLoadNeighbor(rx, ry - 1, MapType.LANDSCAPE);
            byte[] eLand = safeLoadNeighbor(rx + 1, ry, MapType.LANDSCAPE);
            byte[] wLand = safeLoadNeighbor(rx - 1, ry, MapType.LANDSCAPE);
            byte[] neLand = safeLoadNeighbor(rx + 1, ry + 1, MapType.LANDSCAPE);
            byte[] nwLand = safeLoadNeighbor(rx - 1, ry + 1, MapType.LANDSCAPE);
            byte[] seLand = safeLoadNeighbor(rx + 1, ry - 1, MapType.LANDSCAPE);
            byte[] swLand = safeLoadNeighbor(rx - 1, ry - 1, MapType.LANDSCAPE);
            byte[] nObj = safeLoadNeighbor(rx, ry + 1, MapType.OBJECT);
            byte[] sObj = safeLoadNeighbor(rx, ry - 1, MapType.OBJECT);
            byte[] eObj = safeLoadNeighbor(rx + 1, ry, MapType.OBJECT);
            byte[] wObj = safeLoadNeighbor(rx - 1, ry, MapType.OBJECT);
            byte[] neObj = safeLoadNeighbor(rx + 1, ry + 1, MapType.OBJECT);
            byte[] nwObj = safeLoadNeighbor(rx - 1, ry + 1, MapType.OBJECT);
            byte[] seObj = safeLoadNeighbor(rx + 1, ry - 1, MapType.OBJECT);
            byte[] swObj = safeLoadNeighbor(rx - 1, ry - 1, MapType.OBJECT);

            int sceneW = 64 + PAD_TILES * 2;
            int sceneH = 64 + PAD_TILES * 2;

            SceneGraph sg = new SceneGraph(sceneW, sceneH, 4);
            MapRegion  mr = new MapRegion(sg, sceneW, sceneH);

            for (int z = 0; z < 4; z++)
                for (int x = 0; x < sceneW; x++)
                    for (int y = 0; y < sceneH; y++)
                        mr.tileFlags[z][x][y] = 0;

            final int ox = PAD_TILES;
            final int oy = PAD_TILES;

            // Main + neighbor tile decode: any exception here -> red box
            try {
                mr.unpackTiles(land, ox, oy, rx, ry);
                if (eLand != null) mr.unpackTiles(eLand, ox + 64, oy, rx + 1, ry);
                if (wLand != null) mr.unpackTiles(wLand, ox - 64, oy, rx - 1, ry);
                if (nLand != null) mr.unpackTiles(nLand, ox, oy + 64, rx, ry + 1);
                if (sLand != null) mr.unpackTiles(sLand, ox, oy - 64, rx, ry - 1);
                if (neLand != null) mr.unpackTiles(neLand, ox + 64, oy + 64, rx + 1, ry + 1);
                if (nwLand != null) mr.unpackTiles(nwLand, ox - 64, oy + 64, rx - 1, ry + 1);
                if (seLand != null) mr.unpackTiles(seLand, ox + 64, oy - 64, rx + 1, ry - 1);
                if (swLand != null) mr.unpackTiles(swLand, ox - 64, oy - 64, rx - 1, ry - 1);
            } catch (Throwable t) {
                System.out.println("[Export] Tile decode failed for region " + rx + "," + ry + ": " + t);
                return createMissingRegionImage(coreTileSize, padPx);
            }

            ArrayList<PendingMark> marks = new ArrayList<>();
            // Object decode: individual errors are caught inside unpackObjectsSelective
            if (obj != null)  unpackObjectsSelective(mr, sg, obj,  ox, oy, sceneW, sceneH, marks);
            if (eObj != null) unpackObjectsSelective(mr, sg, eObj, ox + 64, oy, sceneW, sceneH, marks);
            if (wObj != null) unpackObjectsSelective(mr, sg, wObj, ox - 64, oy, sceneW, sceneH, marks);
            if (nObj != null) unpackObjectsSelective(mr, sg, nObj, ox, oy + 64, sceneW, sceneH, marks);
            if (sObj != null) unpackObjectsSelective(mr, sg, sObj, ox, oy - 64, sceneW, sceneH, marks);
            if (neObj != null) unpackObjectsSelective(mr, sg, neObj, ox + 64, oy + 64, sceneW, sceneH, marks);
            if (nwObj != null) unpackObjectsSelective(mr, sg, nwObj, ox - 64, oy + 64, sceneW, sceneH, marks);
            if (seObj != null) unpackObjectsSelective(mr, sg, seObj, ox + 64, oy - 64, sceneW, sceneH, marks);
            if (swObj != null) unpackObjectsSelective(mr, sg, swObj, ox - 64, oy - 64, sceneW, sceneH, marks);

            try {
                mr.method171(sg);
            } catch (Throwable t) {
                System.out.println("[Export] Region build failed for " + rx + "," + ry + ": " + t);
                return createMissingRegionImage(coreTileSize, padPx);
            }

            final int totalTiles = 64 + 2 * PAD_TILES;
            final int hiW = (coreTileSize == 256) ? totalTiles * HI_BASE_PX : totalTiles;
            final int hiH = hiW;
            final int pxPerTile = (coreTileSize == 256) ? HI_BASE_PX : 1;

            // Reuse hi buffer
            int neededSize = hiW * hiH;
            int[] hi = HI_BUFFER;
            if (hi == null || hi.length != neededSize) {
                hi = new int[neededSize];
                HI_BUFFER = hi;
            } else {
                Arrays.fill(hi, 0);
            }

            for (int ty = -PAD_TILES; ty < 64 + PAD_TILES; ty++) {
                int sy = oy + ty;
                int yPix = (totalTiles - 1 - (ty + PAD_TILES)) * pxPerTile;
                for (int tx = -PAD_TILES; tx < 64 + PAD_TILES; tx++) {
                    int sx = ox + tx;

                    boolean bridge = isBridgeTile(mr, sx, sy);
                    int tileZ = plane + (bridge ? 1 : 0);
                    if (tileZ > 3) continue;

                    if ((mr.tileFlags[plane][sx][sy] & 0x18) == 0) {
                        if (plane == 0 && bridge) {
                            int dst = (yPix * hiW) + ((tx + PAD_TILES) * pxPerTile);
                            sg.drawMinimapTile(hi, sx, sy, 0, dst, hiW);
                        }
                        int dst = (yPix * hiW) + ((tx + PAD_TILES) * pxPerTile);
                        sg.drawMinimapTile(hi, sx, sy, tileZ, dst, hiW);
                    }
                    if (tileZ < 3 && (mr.tileFlags[tileZ + 1][sx][sy] & 8) != 0) {
                        int dst = (yPix * hiW) + ((tx + PAD_TILES) * pxPerTile);
                        sg.drawMinimapTile(hi, sx, sy, tileZ + 1, dst, hiW);
                    }
                }
            }

            overlayObjectsIntoHi(sg, mr, plane, ox, oy, hi, hiW, hiH, pxPerTile);

            drawCollectedSpritesAndIcons(marks, plane, ox, oy, mr, hi, hiW, hiH, pxPerTile);

            // Reuse ARGB buffer
            int[] argb = ARGB_BUFFER;
            if (argb == null || argb.length != neededSize) {
                argb = new int[neededSize];
                ARGB_BUFFER = argb;
            }

            for (int i = 0; i < neededSize; i++) {
                int c = hi[i] & 0x00FFFFFF;
                argb[i] = (c == 0) ? 0x00000000 : (0xFF000000 | c);
            }

            // Reuse per-region BufferedImage
            if (REGION_IMAGE == null || REGION_IMAGE.getWidth() != hiW || REGION_IMAGE.getHeight() != hiH) {
                REGION_IMAGE = new BufferedImage(hiW, hiH, BufferedImage.TYPE_INT_ARGB);
            }
            BufferedImage out = REGION_IMAGE;
            out.setRGB(0, 0, hiW, hiH, argb, 0, hiW);

            return out;
        } catch (Throwable t) {
            System.out.println("[Export] Region render failed for " + rx + "," + ry + ": " + t);
            return createMissingRegionImage(coreTileSize, padPx);
        }
    }

    private static final class PendingMark {
        final int objId, tx, ty, plane;
        final boolean isMapscene;
        PendingMark(int objId, int tx, int ty, int plane, boolean isMapscene) {
            this.objId = objId; this.tx = tx; this.ty = ty; this.plane = plane; this.isMapscene = isMapscene;
        }
    }

    private static void unpackObjectsSelective(
            MapRegion mr, SceneGraph sg, byte[] data, int baseX, int baseY, int sceneW, int sceneH, List<PendingMark> marks) {

        if (data == null || data.length == 0) return;
        try {
            ByteReader br = new ByteReader(data);
            int id = -1;
            while (true) {
                int idDelta = br.readUSmartSafe();
                if (idDelta == -1) return;
                if (idDelta == 0) break;
                id += idDelta;
                int packedPos = 0;
                while (true) {
                    int posDelta = br.readUSmartSafe();
                    if (posDelta == -1) return;
                    if (posDelta == 0) break;
                    packedPos += posDelta - 1;
                    int localY =  packedPos & 0x3F;
                    int localX = (packedPos >> 6) & 0x3F;
                    int plane = (packedPos >> 12) & 0x3;
                    int info = br.readUByteSafe();
                    if (info == -1) return;
                    int type = info >> 2;
                    int ori  = info & 3;
                    int x = baseX + localX;
                    int y = baseY + localY;
                    if (x < 0 || y < 0 || x >= sceneW || y >= sceneH) continue;

                    try {
                        if (type == 0 || type == 1 || type == 2 || type == 3 || type == 9 || type == 22) {
                            mr.spawnObjectToWorld(sg, id, x, y, plane, type, ori, false);
                        }

                        ObjectDefinition def = ObjectDefinitionLoader.lookup(id);
                        if (def == null) continue;

                        if (def.getMapscene() >= 0) {
                            marks.add(new PendingMark(id, x, y, plane, true));
                        }

                        boolean hasFn = false;
                        int areaId = def.getAreaId();
                        if (areaId >= 0) hasFn = true;
                        if (!hasFn && def.getMinimapFunction() >= 0) hasFn = true;
                        if (hasFn) {
                            marks.add(new PendingMark(id, x, y, plane, false));
                        }
                    } catch (Throwable t) {
                        // Skip this object if anything about it fails to decode.
                        System.out.println("[Export] Skipping invalid object id=" + id + " at " + x + "," + y + ": " + t);
                    }
                }
            }
        } catch (Throwable t) {
            // Entire object blob for this region is invalid, skip all its objects.
            System.out.println("[Export] Skipping invalid object data blob: " + t);
        }
    }

    private static void drawCollectedSpritesAndIcons(
            List<PendingMark> marks, int viewZ, int ox, int oy, MapRegion mr, int[] hi, int hiW, int hiH, int pxPerTile) {
        for (PendingMark m : marks) {
            try {
                int sx = m.tx, sy = m.ty;
                boolean bridge = isBridgeTile(mr, sx, sy);
                int tileZ = m.plane + (bridge ? 1 : 0);
                if (tileZ != viewZ) continue;

                int tx = sx - ox;
                int ty = sy - oy;

                if (tx < -PAD_TILES || ty < -PAD_TILES || tx >= 64 + PAD_TILES || ty >= 64 + PAD_TILES) continue;

                ObjectDefinition def = ObjectDefinitionLoader.lookup(m.objId);
                if (def == null) continue;

                if (m.isMapscene) {
                    Sprite img = sceneSprite(def);
                    if (img == null) continue;
                    int iw = dispW(img), ih = dispH(img);
                    int footW = def.getWidth()  * pxPerTile;
                    int footH = def.getLength() * pxPerTile;
                    int offX  = (footW - iw) / 2;
                    int offY  = (footH - ih) / 2;
                    int fx = (tx + PAD_TILES) * pxPerTile;
                    int fy = ((64 + 2 * PAD_TILES - 1 - (ty + PAD_TILES)) * pxPerTile) - (footH - pxPerTile);
                    blitSprite(img, fx + offX, fy + offY, hi, hiW, hiH, true);
                } else {
                    Sprite icon = functionSprite(def);
                    if (icon == null) continue;
                    int iw = dispW(icon), ih = dispH(icon);
                    int cx = (tx + PAD_TILES) * pxPerTile + (pxPerTile / 2) + (pxPerTile == 4 ? 2 : 0);
                    int cy = (64 + 2 * PAD_TILES - 1 - (ty + PAD_TILES)) * pxPerTile + (pxPerTile / 2) + (pxPerTile == 4 ? 2 : 0);
                    int dx = cx - iw / 2;
                    int dy = cy - ih / 2;
                    blitSprite(icon, dx, dy, hi, hiW, hiH, true);
                }
            } catch (Throwable t) {
                // Skip problematic sprite/icon; do not break the region.
                System.out.println("[Export] Skipping sprite/icon for obj " + m.objId + ": " + t);
            }
        }
    }

    private static void overlayObjectsIntoHi(SceneGraph sg, MapRegion mr, int z, int ox, int oy, int[] hi, int hiW, int hiH, int pxPerTile) {
        for (int ty = 0; ty < 64; ty++) {
            for (int tx = 0; tx < 64; tx++) {
                int sx = ox + tx, sy = oy + ty;
                boolean bridge = isBridgeTile(mr, sx, sy);
                int tileZ = z + (bridge ? 1 : 0);
                if (tileZ > 3) continue;

                try {
                    drawWallsAndMapscenes_HI(sg, sx, sy, tileZ, tx, ty, hi, hiW, hiH, pxPerTile);

                    if (tileZ < 3 && (mr.tileFlags[tileZ + 1][sx][sy] & 8) != 0) {
                        drawWallsAndMapscenes_HI(sg, sx, sy, tileZ + 1, tx, ty, hi, hiW, hiH, pxPerTile);
                    }
                } catch (Throwable t) {
                    // Any bad object/sprite on this tile is skipped.
                    System.out.println("[Export] Skipping overlay at tile (" + sx + "," + sy + "," + z + "): " + t);
                }
            }
        }
    }

    private static boolean isDoorLike(ObjectDefinition def) {
        if (def == null) return false;
        String n = def.getName();
        if (n == null) return false;
        n = n.toLowerCase();
        return n.contains("door") || n.contains("gate");
    }

    private static void drawWallsAndMapscenes_HI(SceneGraph sg, int sx, int sy, int z, int tx, int ty, int[] hi, int hiW, int hiH, int pxPerTile) {
        int base = (((64 + 2 * PAD_TILES - 1 - (ty + PAD_TILES)) * pxPerTile) * hiW) + ((tx + PAD_TILES) * pxPerTile);

        Consumer<ObjectKey> drawMapsceneIfAny = (key) -> {
            if (key == null) return;
            ObjectDefinition def;
            try {
                def = ObjectDefinitionLoader.lookup(key.getId());
            } catch (Throwable t) {
                System.out.println("[Export] Skipping mapscene lookup for obj " + key.getId() + ": " + t);
                return;
            }
            if (def == null) return;
            Sprite img = sceneSprite(def);
            if (img == null) return;
            int iw = dispW(img), ih = dispH(img);
            int footW = def.getWidth()  * pxPerTile;
            int footH = def.getLength() * pxPerTile;
            int offX  = (footW - iw) / 2;
            int offY  = (footH - ih) / 2;
            int fx = (tx + PAD_TILES) * pxPerTile;
            int fy = ((64 + 2 * PAD_TILES - 1 - (ty + PAD_TILES)) * pxPerTile) - (footH - pxPerTile);
            blitSprite(img, fx + offX, fy + offY, hi, hiW, hiH, true);
        };

        ObjectKey wall = sg.getWallKey(sx, sy, z);
        if (wall != null) {
            ObjectDefinition def = null;
            try {
                def = ObjectDefinitionLoader.lookup(wall.getId());
            } catch (Throwable t) {
                System.out.println("[Export] Skipping wall obj " + wall.getId() + ": " + t);
            }
            if (def != null && def.getMapscene() != -1 && Client.mapScenes != null && def.getMapscene() < Client.mapScenes.length) {
                drawMapsceneIfAny.accept(wall);
                return;
            }
            int color = (isDoorLike(def) ? DOOR_COLOR : WALL_COLOR) | OVERLAY_BIT;
            int ori = wall.getOrientation();
            int type = wall.getType();

            if (type == 0 || type == 2) {
                if (ori == 0) { hi[base] = color; hi[base + hiW] = color; hi[base + hiW*2] = color; hi[base + hiW*3] = color; }
                else if (ori == 1) { hi[base] = color; hi[base+1] = color; hi[base+2] = color; hi[base+3] = color; }
                else if (ori == 2) { int b = base + (pxPerTile - 1); hi[b] = color; hi[b + hiW] = color; hi[b + hiW*2] = color; hi[b + hiW*3] = color; }
                else if (ori == 3) { int b = base + hiW*(pxPerTile - 1); hi[b] = color; hi[b+1] = color; hi[b+2] = color; hi[b+3] = color; }
            }
            if (type == 3) {
                if (ori == 0) hi[base] = color;
                else if (ori == 1) hi[base + (pxPerTile - 1)] = color;
                else if (ori == 2) hi[base + (pxPerTile - 1) + hiW*(pxPerTile - 1)] = color;
                else if (ori == 3) hi[base + hiW*(pxPerTile - 1)] = color;
            }
        }

        ObjectKey wallDeco = sg.getWallDecorationKey(sx, sy, z);
        if (wallDeco != null) {
            drawMapsceneIfAny.accept(wallDeco);
        }

        ObjectKey inter = sg.getInteractableObjectKey(sx, sy, z);
        if (inter != null) {
            ObjectDefinition def = null;
            try {
                def = ObjectDefinitionLoader.lookup(inter.getId());
            } catch (Throwable t) {
                System.out.println("[Export] Skipping interactable obj " + inter.getId() + ": " + t);
            }
            if (def != null && def.getMapscene() != -1 && Client.mapScenes != null && def.getMapscene() < Client.mapScenes.length) {
                drawMapsceneIfAny.accept(inter);
                return;
            }
            if (inter.getType() == 9) {
                int color = (isDoorLike(def) ? DOOR_COLOR : WALL_COLOR) | OVERLAY_BIT;
                int idx = base;
                int ori = inter.getOrientation();
                if (ori == 0 || ori == 2) {
                    hi[idx + hiW*(pxPerTile - 1)] = color;
                    hi[idx + hiW*(pxPerTile - 2) + 1] = color;
                    hi[idx + hiW*(pxPerTile - 3) + 2] = color;
                    hi[idx + (pxPerTile - 1)] = color;
                } else {
                    hi[idx] = color;
                    hi[idx + hiW + 1] = color;
                    hi[idx + hiW*2 + 2] = color;
                    hi[idx + hiW*3 + (pxPerTile - 1)] = color;
                }
            }
        }

        ObjectKey floor = sg.getFloorDecorationKey(sx, sy, z);
        if (floor != null) {
            drawMapsceneIfAny.accept(floor);
        }
    }

    private static void blitSprite(Sprite sprite, int destX, int destY, int[] hi, int hiW, int hiH, boolean markOverlay) {
        if (sprite == null) return;
        int w = sprite.getWidth(), h = sprite.getHeight();
        int[] src = sprite.getRaster();
        int sx = 0, sy = 0, dx = destX, dy = destY, rw = w, rh = h;

        if (dx < 0) { sx = -dx; rw -= sx; dx = 0; }
        if (dy < 0) { sy = -dy; rh -= sy; dy = 0; }
        if (dx + rw > hiW) rw = hiW - dx;
        if (dy + rh > hiH) rh = hiH - dy;
        if (rw <= 0 || rh <= 0) return;

        int overlayMask = markOverlay ? OVERLAY_BIT : 0;
        for (int yy = 0; yy < rh; yy++) {
            int si = (sy + yy) * w + sx;
            int di = (dy + yy) * hiW + dx;
            for (int xx = 0; xx < rw; xx++, si++, di++) {
                int c = src[si] & 0x00FFFFFF;
                if (c != 0) hi[di] = overlayMask | c;
            }
        }
    }

    private static Sprite sceneSprite(ObjectDefinition def) {
        if (def == null) return null;
        int id = def.getMapscene();
        if (id < 0) return null;
        Sprite[] scenes = Client.mapScenes;
        if (scenes == null) return null;
        if (id < 0 || id >= scenes.length) return null;
        return scenes[id];
    }

    private static Sprite functionSprite(ObjectDefinition def) {
        if (def == null) return null;
        try {
            int areaId = def.getAreaId();
            if (areaId >= 0) {
                RSArea area = RSAreaLoader.get(areaId);
                if (area != null) {
                    int spriteId = area.getSpriteId();
                    if (spriteId >= 0) {
                        Client c = Client.getSingleton();
                        if (c != null && c.getCache() != null) {
                            try {
                                Sprite s = c.getCache().getSprite(spriteId);
                                if (s != null) return s;
                            } catch (Throwable ignored) {}
                        }
                    }
                }
            }
        } catch (Throwable t) {
            System.out.println("[Export] Skipping area sprite for def " + def.getId() + ": " + t);
        }
        try {
            int fn = def.getMinimapFunction();
            if (fn >= 0 && Client.mapFunctions != null && fn < Client.mapFunctions.length) {
                return Client.mapFunctions[fn];
            }
        } catch (Throwable t) {
            System.out.println("[Export] Skipping minimap function sprite for def " + def.getId() + ": " + t);
        }
        return null;
    }

    private static int dispW(Sprite s) {
        return (s.getResizeWidth()  > 0) ? s.getResizeWidth()  : s.getWidth();
    }

    private static int dispH(Sprite s) {
        return (s.getResizeHeight() > 0) ? s.getResizeHeight() : s.getHeight();
    }

    private static final class ByteReader {
        private final byte[] buf;
        private int pos, len;
        ByteReader(byte[] b) {
            this.buf = b; this.len = b.length;
        }
        int readUByteSafe() {
            return (pos < len) ? (buf[pos++] & 0xFF) : -1;
        }
        int readUSmartSafe() {
            if (pos >= len) return -1;
            int peek = buf[pos] & 0xFF;
            if (peek < 128) {
                return readUByteSafe();
            }
            if (pos + 1 >= len) return -1;
            int hi = readUByteSafe();
            int lo = readUByteSafe();
            return ((hi << 8) | lo) - 32768;
        }
    }

    /**
     * Optimized synchronous map request:
     * - Uses a single global EventBus listener instead of registering a new one each time.
     * - Caches map contents per fileId so repeated calls are fast.
     *
     * In your RSPSi fork, this is all local IO through the embedded client/provider.
     */
    private static byte[] requestMapSync(int fileId, int regionHash) throws Exception {
        // Fast path: already cached
        byte[] cached = MAP_CACHE.get(fileId);
        if (cached != null) {
            return cached;
        }

        Client client = Client.getSingleton();
        if (client == null) throw new IllegalStateException("Client singleton is null");

        ensureMapListenerRegistered();

        MapWaiter waiter = new MapWaiter();
        MapWaiter prev = PENDING_MAP_REQUESTS.put(fileId, waiter);
        if (prev != null) {
            // Very unlikely (two overlapping requests for the same fileId).
            // We will replace the previous waiter to avoid leaks.
            prev.latch.countDown();
        }

        client.getProvider().requestMap(fileId, regionHash);

        boolean ok = waiter.latch.await(MAP_REQUEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        if (!ok) {
            //System.out.println("[Export] Timeout waiting for map id=" + fileId + " (region " + regionHash + ")");
            PENDING_MAP_REQUESTS.remove(fileId);
            return null;
        }
        return waiter.result;
    }

    private static byte[] safeLoadNeighbor(int rx, int ry, MapType type) {
        try {
            int id = MapIndexLoader.resolve(rx, ry, type);
            if (id == -1) return null;
            return requestMapSync(id, (rx << 8) | ry);
        } catch (Throwable t) {
            // Neighbor failing should not stall the exporter.
            System.out.println("[Export] Neighbor load failed for " + rx + "," + ry + " type " + type + ": " + t);
            return null;
        }
    }

    private static byte[] loadNeighbor(int rx, int ry, MapType type) throws Exception {
        int id = MapIndexLoader.resolve(rx, ry, type);
        if (id == -1) return null;
        return requestMapSync(id, (rx << 8) | ry);
    }
}
