package com.compilequest.core;

import javax.imageio.ImageIO;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.awt.image.MultiResolutionImage;
import java.io.File;
import java.util.List;

/**
 * Developer check, enabled with -Dcq.selftest=out.png: runs the real window for a few seconds,
 * reports render times, saves a screen capture and exits.
 */
final class SelfTest {
    private final Game game;
    private int frames;
    private long total, worst, start;

    SelfTest(Game game) { this.game = game; }

    void frame(long renderNs) {
        frames++;
        if (frames == 60) start = System.nanoTime();
        if (frames == 100 && Boolean.getBoolean("cq.selftest.toggle")) game.setFullscreen(!game.save.fullscreen);
        if (frames > 60) {
            total += renderNs;
            worst = Math.max(worst, renderNs);
        }
        if (frames == 300) {
            double secs = (System.nanoTime() - start) / 1e9;
            try {
                Rectangle screen = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
                MultiResolutionImage shot = new Robot().createMultiResolutionScreenCapture(screen);
                List<Image> variants = shot.getResolutionVariants();
                Image best = variants.get(variants.size() - 1);
                BufferedImage img = new BufferedImage(best.getWidth(null), best.getHeight(null), BufferedImage.TYPE_INT_RGB);
                img.getGraphics().drawImage(best, 0, 0, null);
                ImageIO.write(img, "png", new File(System.getProperty("cq.selftest")));
                System.out.printf("capture %dx%d, %.1f fps, avg render %.2f ms, worst %.2f ms over %d frames%n",
                        img.getWidth(), img.getHeight(), (frames - 60) / secs, total / 1e6 / (frames - 60), worst / 1e6, frames - 60);
            } catch (Exception e) {
                e.printStackTrace();
            }
            System.exit(0);
        }
    }
}
