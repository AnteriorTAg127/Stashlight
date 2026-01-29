package dev.strangequark.stashlight.gui;


import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.core.OwoUIDrawContext;
import io.wispforest.owo.ui.core.Sizing;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

public class QuantityLabel extends LabelComponent {
    private float scale = 1f;

    public QuantityLabel(Text text) {
        super(text);
    }

    public static QuantityLabel of(Text text) {
        return new QuantityLabel(text);
    }

    public QuantityLabel scale(float scale) {
        this.scale = scale;

        // Important: Scaling changes the effective size, so notify the parent to re-layout
        this.notifyParentIfMounted();
        return this;
    }

    // 5. Override draw() to apply scaling transformation
    @Override
    public void draw(OwoUIDrawContext context, int mouseX, int mouseY, float partialTicks, float delta) {
        var matrices = context.getMatrices();

        matrices.pushMatrix();

        // Apply scale transformation
        // We translate to the component's position, scale, and then translate back
        // to render the text at its intended location, scaled from the component's (x, y).
        matrices.translate(this.x, this.y);
        matrices.scale(this.scale, this.scale);
        matrices.translate(-this.x, -this.y);

        this.drawLegacy(context);

        matrices.popMatrix();
    }

    // Legacy draw method from owo 1.21.9
    public void drawLegacy(OwoUIDrawContext context) {
        var matrices = context.getMatrices();

        matrices.pushMatrix();
        matrices.translate(0, 1f / MinecraftClient.getInstance().getWindow().getScaleFactor());

        int x = this.x;
        int y = this.y;

        if (this.horizontalSizing.get().isContent()) {
            x += this.horizontalSizing.get().value;
        }
        if (this.verticalSizing.get().isContent()) {
            y += this.verticalSizing.get().value;
        }

        switch (this.verticalTextAlignment) {
            case CENTER -> y += (this.height - (this.textHeight())) / 2;
            case BOTTOM -> y += this.height - (this.textHeight());
        }

        final int lambdaX = x;
        final int lambdaY = y;

        for (int i = 0; i < this.wrappedText.size(); i++) {
            var renderText = this.wrappedText.get(i);
            int renderX = lambdaX;

            switch (this.horizontalTextAlignment) {
                case CENTER -> renderX += (this.width - this.textRenderer.getWidth(renderText)) / 2;
                case RIGHT -> renderX += this.width - this.textRenderer.getWidth(renderText);
            }

            int renderY = lambdaY + i * (this.lineHeight() + this.lineSpacing());
            renderY += this.lineHeight() - this.textRenderer.fontHeight;

            context.drawText(this.textRenderer, renderText, renderX, renderY, this.color.get().argb(), this.shadow);
        }

        matrices.popMatrix();
    }

    // 6. Override content sizing to account for scale
    @Override
    protected int determineHorizontalContentSize(Sizing sizing) {
        // Calculate the unscaled size first, then multiply by the scale.
        // We call super but pass the sizing, not 'this.horizontalSizing.get()', to avoid infinite recursion.
        // We need the unscaled size to correctly determine if wrapping is needed.
        return (int) (super.determineHorizontalContentSize(sizing) * this.scale);
    }

    @Override
    protected int determineVerticalContentSize(Sizing sizing) {
        // Calculate the unscaled size first, then multiply by the scale.
        return (int) (super.determineVerticalContentSize(sizing) * this.scale);
    }
}
