package ksu.chat.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

public class PhotoScreen extends Screen {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final byte[] imageData;
    private DynamicTexture dynamicTexture;
    private ResourceLocation textureLocation;
    private int imageWidth;
    private int imageHeight;
    private boolean loaded = false;

    public static void open(byte[] data) {
        net.minecraft.client.Minecraft.getInstance().setScreen(new PhotoScreen(data));
    }

    public PhotoScreen(byte[] imageData) {
        super(Component.literal("Фото"));
        this.imageData = imageData;
    }

    @Override
    protected void init() {
        super.init();
        loadTexture();
    }

    private void loadTexture() {
        try {
            BufferedImage bimg = ImageIO.read(new ByteArrayInputStream(imageData));
            if (bimg == null) {
                LOGGER.warn("[KsuChat] ImageIO вернул null");
                return;
            }

            int w = bimg.getWidth();
            int h = bimg.getHeight();

            ByteArrayOutputStream pngOut = new ByteArrayOutputStream();
            ImageIO.write(bimg, "png", pngOut);
            byte[] pngBytes = pngOut.toByteArray();
            NativeImage nativeImage = NativeImage.read(new ByteArrayInputStream(pngBytes));

            imageWidth = nativeImage.getWidth();
            imageHeight = nativeImage.getHeight();

            if (textureLocation != null) {
                net.minecraft.client.Minecraft.getInstance().getTextureManager().release(textureLocation);
            }
            dynamicTexture = new DynamicTexture(nativeImage);
            textureLocation = ResourceLocation.fromNamespaceAndPath("ksuchat", "photo");
            net.minecraft.client.Minecraft.getInstance().getTextureManager().register(textureLocation, dynamicTexture);

            loaded = true;
            LOGGER.info("[KsuChat] Фото загружено: {}x{} (источник {}x{})", imageWidth, imageHeight, w, h);
        } catch (Exception e) {
            LOGGER.warn("[KsuChat] Ошибка загрузки фото: {}", e.getMessage(), e);
        }
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        guiGraphics.fill(0, 0, this.width, this.height, 0xFF000000);

        if (loaded && dynamicTexture != null) {
            int maxW = (int) (this.width * 0.9);
            int maxH = (int) (this.height * 0.9);
            float scale = Math.min((float) maxW / imageWidth, (float) maxH / imageHeight);
            int drawW = (int) (imageWidth * scale);
            int drawH = (int) (imageHeight * scale);
            int x = (this.width - drawW) / 2;
            int y = (this.height - drawH) / 2;

            com.mojang.blaze3d.vertex.PoseStack poseStack = guiGraphics.pose();
            poseStack.pushPose();
            poseStack.translate(x, y, 0);
            poseStack.scale(scale, scale, 1);
            RenderSystem.enableBlend();
            guiGraphics.blit(textureLocation, 0, 0, 0, 0, imageWidth, imageHeight, imageWidth, imageHeight);
            poseStack.popPose();
        } else {
            guiGraphics.drawCenteredString(this.font, "Загрузка...", this.width / 2, this.height / 2, 0xFFFFFF);
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }
}
