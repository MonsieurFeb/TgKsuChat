package ksu.chat.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Полноэкранный просмотр фотографии, полученной из Telegram.
 */
public class PhotoScreen extends GuiScreen {
    private static final Logger LOGGER = LogManager.getLogger("KsuChat");
    private static final AtomicInteger TEXTURE_SEQ = new AtomicInteger(0);

    private final byte[] imageData;
    private ResourceLocation textureLocation;
    private int imageWidth;
    private int imageHeight;
    private boolean loaded = false;

    public static void open(byte[] data) {
        Minecraft.getMinecraft().displayGuiScreen(new PhotoScreen(data));
    }

    public PhotoScreen(byte[] imageData) {
        this.imageData = imageData;
    }

    @Override
    public void initGui() {
        super.initGui();
        loadTexture();
    }

    private void loadTexture() {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageData));
            if (image == null) {
                LOGGER.warn("[KsuChat] ImageIO не смог прочитать изображение");
                return;
            }

            imageWidth = image.getWidth();
            imageHeight = image.getHeight();
            if (imageWidth <= 0 || imageHeight <= 0) {
                return;
            }

            if (textureLocation != null) {
                mc.getTextureManager().deleteTexture(textureLocation);
            }

            DynamicTexture texture = new DynamicTexture(image);
            textureLocation = new ResourceLocation("ksuchat",
                    "photo_" + TEXTURE_SEQ.incrementAndGet());
            mc.getTextureManager().loadTexture(textureLocation, texture);
            loaded = true;
            LOGGER.info("[KsuChat] Фото загружено: {}x{}", imageWidth, imageHeight);
        } catch (Exception e) {
            LOGGER.warn("[KsuChat] Ошибка загрузки фото: {}", e.getMessage(), e);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawRect(0, 0, width, height, 0xFF000000);

        if (loaded && textureLocation != null) {
            float maxW = width * 0.9F;
            float maxH = height * 0.9F;
            float scale = Math.min(maxW / imageWidth, maxH / imageHeight);
            int drawW = Math.max(1, (int) (imageWidth * scale));
            int drawH = Math.max(1, (int) (imageHeight * scale));
            int x = (width - drawW) / 2;
            int y = (height - drawH) / 2;

            GlStateManager.enableBlend();
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            mc.getTextureManager().bindTexture(textureLocation);
            Gui.drawModalRectWithCustomSizedTexture(x, y, 0, 0, drawW, drawH,
                    imageWidth, imageHeight);
            GlStateManager.disableBlend();
        } else {
            drawCenteredString(fontRenderer, "Загрузка...", width / 2, height / 2, 0xFFFFFF);
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        if (textureLocation != null) {
            mc.getTextureManager().deleteTexture(textureLocation);
            textureLocation = null;
        }
        loaded = false;
    }
}
