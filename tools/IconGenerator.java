import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class IconGenerator {

    private static final int[] SIZES = {16, 32, 48, 256};

    public static void main(String[] args) throws IOException {
        Path output = Path.of(args.length > 0 ? args[0] : "icon.ico");
        List<byte[]> frames = new ArrayList<>();
        for (int size : SIZES) {
            frames.add(renderDibFrame(renderImage(size)));
        }
        Files.write(output, buildIco(SIZES, frames));
        System.out.println("Wrote " + output.toAbsolutePath());
    }

    private static final Color BG = new Color(0x2E, 0x86, 0xDE);

    /**
     * Renders at a large supersampled canvas and downscales with bilinear filtering.
     * Drawing "HPC" directly at 16px produces mushy, barely-legible antialiasing;
     * supersampling gives the filter much more source detail to work with, which
     * turns out to keep all three letters readable even at 16px.
     */
    static BufferedImage renderImage(int size) {
        int big = Math.max(size, 512);

        BufferedImage large = new BufferedImage(big, big, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = large.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        float arc = big * 0.28f;
        g.setColor(BG);
        g.fill(new RoundRectangle2D.Float(0, 0, big, big, arc, arc));

        // Render the glyph outline directly (rather than drawString/FontMetrics
        // baseline placement) and center on its exact bounds - this lines up thin
        // strokes like the H crossbar much more solidly against the downsample
        // grid. A slight synthetic-bold stroke adds a safety margin so thin
        // strokes reliably survive the size<=32 threshold step below without
        // relying on exact sub-pixel luck.
        Font font = new Font(Font.SANS_SERIF, Font.BOLD, Math.round(big * 0.47f));
        FontRenderContext frc = g.getFontRenderContext();
        GlyphVector glyphVector = font.createGlyphVector(frc, "HPC");
        Shape outline = glyphVector.getOutline();
        Rectangle2D bounds = outline.getBounds2D();
        AffineTransform transform = AffineTransform.getTranslateInstance(
                (big - bounds.getWidth()) / 2 - bounds.getX(),
                (big - bounds.getHeight()) / 2 - bounds.getY());
        Shape text = transform.createTransformedShape(outline);

        g.setColor(Color.WHITE);
        g.fill(text);
        g.setStroke(new BasicStroke(big * 0.015f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(text);
        g.dispose();

        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D gs = image.createGraphics();
        gs.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        gs.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        gs.drawImage(large, 0, 0, size, size, null);
        gs.dispose();

        // At small sizes, thin strokes (e.g. the H crossbar, only 1-2px thick here)
        // end up made almost entirely of antialiased edge pixels - since those blend
        // white text into the already-opaque blue background, the whole stroke reads
        // as a washed-out blue-white blend instead of white. Snapping every pixel to
        // pure white/pure background/transparent removes that blending artifact.
        return size <= 32 ? threshold(image) : image;
    }

    private static BufferedImage threshold(BufferedImage image) {
        int w = image.getWidth();
        int h = image.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = image.getRGB(x, y);
                int a = (argb >>> 24) & 0xFF;
                if (a < 128) {
                    out.setRGB(x, y, 0);
                    continue;
                }
                int r = (argb >>> 16) & 0xFF;
                int gr = (argb >>> 8) & 0xFF;
                int b = argb & 0xFF;
                int distWhite = sq(255 - r) + sq(255 - gr) + sq(255 - b);
                int distBg = sq(BG.getRed() - r) + sq(BG.getGreen() - gr) + sq(BG.getBlue() - b);
                out.setRGB(x, y, distWhite <= distBg ? 0xFFFFFFFF : BG.getRGB());
            }
        }
        return out;
    }

    private static int sq(int v) {
        return v * v;
    }

    /** BITMAPINFOHEADER + 32bpp BGRA XOR mask + 1bpp AND mask, both bottom-up. */
    private static byte[] renderDibFrame(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        int andRowBytes = ((width + 31) / 32) * 4;
        int xorDataSize = width * 4 * height;
        int andDataSize = andRowBytes * height;
        int imageSize = xorDataSize + andDataSize;

        ByteBuffer buf = ByteBuffer.allocate(40 + imageSize);
        buf.order(ByteOrder.LITTLE_ENDIAN);

        buf.putInt(40);            // biSize
        buf.putInt(width);         // biWidth
        buf.putInt(height * 2);    // biHeight (XOR + AND combined, per ICO convention)
        buf.putShort((short) 1);   // biPlanes
        buf.putShort((short) 32);  // biBitCount
        buf.putInt(0);             // biCompression = BI_RGB
        buf.putInt(imageSize);     // biSizeImage
        buf.putInt(0);             // biXPelsPerMeter
        buf.putInt(0);             // biYPelsPerMeter
        buf.putInt(0);             // biClrUsed
        buf.putInt(0);             // biClrImportant

        for (int y = height - 1; y >= 0; y--) {
            for (int x = 0; x < width; x++) {
                int argb = image.getRGB(x, y);
                buf.put((byte) (argb & 0xFF));           // B
                buf.put((byte) ((argb >>> 8) & 0xFF));   // G
                buf.put((byte) ((argb >>> 16) & 0xFF));  // R
                buf.put((byte) ((argb >>> 24) & 0xFF));  // A
            }
        }

        for (int y = height - 1; y >= 0; y--) {
            int currentByte = 0;
            int bitsInByte = 0;
            int bytesWritten = 0;
            for (int x = 0; x < width; x++) {
                int alpha = (image.getRGB(x, y) >>> 24) & 0xFF;
                int bit = (alpha == 0) ? 1 : 0;
                currentByte = (currentByte << 1) | bit;
                bitsInByte++;
                if (bitsInByte == 8) {
                    buf.put((byte) currentByte);
                    currentByte = 0;
                    bitsInByte = 0;
                    bytesWritten++;
                }
            }
            if (bitsInByte > 0) {
                currentByte <<= (8 - bitsInByte);
                buf.put((byte) currentByte);
                bytesWritten++;
            }
            while (bytesWritten < andRowBytes) {
                buf.put((byte) 0);
                bytesWritten++;
            }
        }

        return buf.array();
    }

    private static byte[] buildIco(int[] sizes, List<byte[]> frames) {
        int headerSize = 6;
        int entrySize = 16;
        int dataOffset = headerSize + entrySize * sizes.length;

        int totalSize = dataOffset;
        for (byte[] frame : frames) {
            totalSize += frame.length;
        }

        ByteBuffer buffer = ByteBuffer.allocate(totalSize);
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        buffer.putShort((short) 0); // reserved
        buffer.putShort((short) 1); // type: icon
        buffer.putShort((short) sizes.length);

        int offset = dataOffset;
        for (int i = 0; i < sizes.length; i++) {
            int size = sizes[i];
            byte[] frame = frames.get(i);
            buffer.put((byte) (size >= 256 ? 0 : size));
            buffer.put((byte) (size >= 256 ? 0 : size));
            buffer.put((byte) 0);  // color palette
            buffer.put((byte) 0);  // reserved
            buffer.putShort((short) 1);  // color planes
            buffer.putShort((short) 32); // bits per pixel
            buffer.putInt(frame.length);
            buffer.putInt(offset);
            offset += frame.length;
        }

        for (byte[] frame : frames) {
            buffer.put(frame);
        }

        return buffer.array();
    }
}
