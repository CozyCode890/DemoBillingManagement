// =====================================================================
//  IconGen.java -- sinh icon cho hai launcher từ cùng một bản vẽ.
// ---------------------------------------------------------------------
//  Chạy: java IconGen <đường dẫn .ico> <đường dẫn .icns>
//        (build-launcher.ps1 / build-launcher.sh tự gọi hộ)
//
//  Vì sao vẽ bằng code thay vì để file ảnh trong repo?
//    - Icon phải trùng với icon cửa sổ của SetupApp (envsetup.ui.MainWindow
//      .createAppIcon vẽ ô vuông xanh chữ "RB"). Vẽ cùng một chỗ thì sửa
//      màu một lần là cả Windows, macOS và cửa sổ Java đổi theo.
//    - Không cần cài ImageMagick / iconutil: cả .ico và .icns chỉ là vỏ
//      bọc quanh vài file PNG, viết tay được bằng Java thuần. Nhờ vậy máy
//      Windows vẫn sinh được .icns cho macOS.
//
//  Hai định dạng, cùng một ruột PNG:
//    .ico  (Windows) -- số nguyên little-endian, hỗ trợ PNG từ Vista.
//    .icns (macOS)   -- số nguyên big-endian, mỗi khối có nhãn 4 chữ
//                       ('ic07' = 128px, 'ic08' = 256px...).
// =====================================================================

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.GlyphVector;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class IconGen {

    /** Trùng với UiHelper.BTN_BLUE, sáng/tối hơn một chút để tạo chuyển sắc. */
    private static final Color TOP = new Color(56, 140, 232);
    private static final Color BOTTOM = new Color(21, 86, 166);
    private static final String MARK = "RB";

    /** Kích thước Windows cần: 16 cho cây thư mục, 256 cho cửa sổ xem icon lớn. */
    private static final int[] ICO_SIZES = {16, 32, 48, 64, 128, 256};

    /** Nhãn khối ICNS -> kích thước. Đủ cho Finder, Dock và Cmd+Tab. */
    private static final Map<String, Integer> ICNS_ENTRIES = new LinkedHashMap<>();

    static {
        ICNS_ENTRIES.put("ic11", 32);   // 16pt @2x
        ICNS_ENTRIES.put("ic12", 64);   // 32pt @2x
        ICNS_ENTRIES.put("ic07", 128);
        ICNS_ENTRIES.put("ic08", 256);
        ICNS_ENTRIES.put("ic09", 512);
    }

    private IconGen() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            System.err.println("Cach dung: java IconGen <out.ico> <out.icns>");
            System.exit(2);
        }
        File ico = new File(args[0]);
        File icns = new File(args[1]);

        writeIco(ico);
        System.out.println("[OK] " + ico.getAbsolutePath() + " (" + ico.length() + " byte)");

        writeIcns(icns);
        System.out.println("[OK] " + icns.getAbsolutePath() + " (" + icns.length() + " byte)");
    }

    // ------------------------------------------------------------------
    //  Bản vẽ
    // ------------------------------------------------------------------

    private static BufferedImage render(int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // Nền: ô vuông bo góc, chuyển sắc xanh từ trên xuống.
        float arc = size * 0.22f;
        g.setPaint(new GradientPaint(0, 0, TOP, 0, size, BOTTOM));
        g.fill(new RoundRectangle2D.Float(0, 0, size, size, arc, arc));

        // Viền tối mảnh để icon không bị "chảy" vào nền sáng của Finder/Explorer.
        if (size >= 32) {
            float inset = Math.max(0.5f, size / 128f);
            g.setColor(new Color(0, 0, 0, 46));
            g.setStroke(new BasicStroke(Math.max(1f, size / 96f)));
            g.draw(new RoundRectangle2D.Float(inset, inset,
                size - 2 * inset, size - 2 * inset, arc, arc));
        }

        // Chữ "RB": ở cỡ nhỏ phải phóng to tỉ lệ, nếu không chỉ còn hai vệt mờ.
        double ratio = size <= 32 ? 0.54 : 0.46;
        g.setColor(Color.WHITE);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, (int) Math.round(size * ratio)));
        drawCentered(g, MARK, size);

        g.dispose();
        return img;
    }

    /**
     * Căn giữa theo hình dạng thật của chữ (visual bounds) chứ không theo
     * ascent/descent của font -- cách sau luôn lệch xuống dưới vì phần
     * descent rỗng của "RB" vẫn được tính vào chiều cao.
     */
    private static void drawCentered(Graphics2D g, String text, int size) {
        GlyphVector glyphs = g.getFont().createGlyphVector(g.getFontRenderContext(), text);
        Rectangle2D bounds = glyphs.getVisualBounds();
        float x = (float) ((size - bounds.getWidth()) / 2 - bounds.getX());
        float y = (float) ((size - bounds.getHeight()) / 2 - bounds.getY());
        g.drawString(text, x, y);
    }

    private static byte[] toPng(BufferedImage img) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        if (!ImageIO.write(img, "png", buffer)) {
            throw new IOException("ImageIO khong ghi duoc PNG");
        }
        return buffer.toByteArray();
    }

    // ------------------------------------------------------------------
    //  .ico -- Windows
    // ------------------------------------------------------------------

    private static void writeIco(File out) throws IOException {
        List<byte[]> images = new ArrayList<>();
        for (int size : ICO_SIZES) {
            images.add(toPng(render(size)));
        }

        mkdirs(out);
        try (OutputStream raw = Files.newOutputStream(out.toPath());
             BufferedOutputStream o = new BufferedOutputStream(raw)) {

            // ICONDIR: 2 byte dự trữ, 2 byte loại (1 = icon), 2 byte số ảnh.
            le16(o, 0);
            le16(o, 1);
            le16(o, ICO_SIZES.length);

            // ICONDIRENTRY: 16 byte mỗi ảnh, phần dữ liệu xếp ngay sau bảng.
            int offset = 6 + 16 * ICO_SIZES.length;
            for (int i = 0; i < ICO_SIZES.length; i++) {
                int size = ICO_SIZES[i];
                o.write(size >= 256 ? 0 : size); // 0 nghĩa là 256
                o.write(size >= 256 ? 0 : size);
                o.write(0); // số màu bảng màu -- 0 vì dùng 32-bit
                o.write(0); // dự trữ
                le16(o, 1);  // số mặt phẳng màu
                le16(o, 32); // bit mỗi điểm ảnh
                le32(o, images.get(i).length);
                le32(o, offset);
                offset += images.get(i).length;
            }

            for (byte[] png : images) {
                o.write(png);
            }
        }
    }

    // ------------------------------------------------------------------
    //  .icns -- macOS
    // ------------------------------------------------------------------

    private static void writeIcns(File out) throws IOException {
        List<byte[]> blocks = new ArrayList<>();
        int total = 8; // 4 byte nhãn 'icns' + 4 byte tổng độ dài

        for (Map.Entry<String, Integer> entry : ICNS_ENTRIES.entrySet()) {
            byte[] png = toPng(render(entry.getValue()));
            byte[] block = new byte[8 + png.length];
            byte[] type = entry.getKey().getBytes("US-ASCII");
            System.arraycopy(type, 0, block, 0, 4);
            putBe32(block, 4, block.length); // độ dài tính cả 8 byte đầu khối
            System.arraycopy(png, 0, block, 8, png.length);
            blocks.add(block);
            total += block.length;
        }

        mkdirs(out);
        try (OutputStream raw = Files.newOutputStream(out.toPath());
             BufferedOutputStream o = new BufferedOutputStream(raw)) {
            o.write("icns".getBytes("US-ASCII"));
            be32(o, total);
            for (byte[] block : blocks) {
                o.write(block);
            }
        }
    }

    // ------------------------------------------------------------------
    //  Tiện ích ghi số
    // ------------------------------------------------------------------

    private static void le16(OutputStream o, int value) throws IOException {
        o.write(value & 0xFF);
        o.write((value >>> 8) & 0xFF);
    }

    private static void le32(OutputStream o, int value) throws IOException {
        o.write(value & 0xFF);
        o.write((value >>> 8) & 0xFF);
        o.write((value >>> 16) & 0xFF);
        o.write((value >>> 24) & 0xFF);
    }

    private static void be32(OutputStream o, int value) throws IOException {
        o.write((value >>> 24) & 0xFF);
        o.write((value >>> 16) & 0xFF);
        o.write((value >>> 8) & 0xFF);
        o.write(value & 0xFF);
    }

    private static void putBe32(byte[] target, int at, int value) {
        target[at] = (byte) ((value >>> 24) & 0xFF);
        target[at + 1] = (byte) ((value >>> 16) & 0xFF);
        target[at + 2] = (byte) ((value >>> 8) & 0xFF);
        target[at + 3] = (byte) (value & 0xFF);
    }

    private static void mkdirs(File file) throws IOException {
        File parent = file.getAbsoluteFile().getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("Khong tao duoc thu muc: " + parent);
        }
    }
}
