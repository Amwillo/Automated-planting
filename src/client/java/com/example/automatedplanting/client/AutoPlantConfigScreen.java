package com.example.automatedplanting.client;

import com.example.automatedplanting.config.AutoPlantConfig;
import com.example.automatedplanting.config.AutoPlantSettings;
import com.example.automatedplanting.network.UpdateAutoPlantSettingsPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The in-game settings screen, opened with the config key (or from Mod Menu).
 *
 * <p>It edits a local copy and only sends it to the server when Apply is pressed. The
 * server validates and stores it, then replies with what it actually kept, so the screen
 * always ends up showing the truth even if a value was clamped.
 *
 * <p>Layout rule: a label sits to the <em>left</em> of every control. Cycle buttons draw
 * their own caption inside themselves, so nothing is ever drawn over them.
 */
public class AutoPlantConfigScreen extends Screen {
	/** Width of the label column on the left. */
	private static final int LABEL_WIDTH = 118;

	/** Width of the numeric control on the right (minus / value / plus). */
	private static final int CONTROL_WIDTH = 118;

	private static final int STEPPER_BUTTON = 20;
	private static final int ROW_HEIGHT = 24;
	private static final int BUTTON_HEIGHT = 20;

	/** Updated whenever the server tells us what it stored. */
	private static AutoPlantSettings serverSettings = AutoPlantSettings.defaults();

	private AutoPlantSettings draft;

	public AutoPlantConfigScreen() {
		super(Component.translatable("screen.automated_planting.title"));
		this.draft = serverSettings;
	}

	/** Called by the client packet receiver when the server reports its settings. */
	public static void acceptServerSettings(AutoPlantSettings settings) {
		serverSettings = settings;

		if (Minecraft.getInstance().screen instanceof AutoPlantConfigScreen open) {
			open.refreshFromServer();
		}
	}

	/** The values shown while nothing has been edited yet. */
	public static AutoPlantSettings lastKnownSettings() {
		return serverSettings;
	}

	private void refreshFromServer() {
		this.draft = serverSettings;
		rebuildWidgets();
	}

	@Override
	protected void init() {
		// Computed here rather than in the field initialisers: Screen's constructor calls
		// init(), which can run before a subclass's fields are assigned.
		this.labelX = this.width / 2 - (LABEL_WIDTH + CONTROL_WIDTH) / 2;
		this.controlX = labelX + LABEL_WIDTH;
		this.firstRowY = 46;

		int labelX = this.labelX;
		int controlX = this.controlX;
		int y = this.firstRowY;

		// Radius: label on the left, then minus / value / plus on the right.
		addRenderableWidget(Button.builder(Component.literal("-"), b -> setRadius(draft.radius() - 1))
				.bounds(controlX, y, STEPPER_BUTTON, BUTTON_HEIGHT).build());
		addRenderableWidget(Button.builder(Component.literal("+"), b -> setRadius(draft.radius() + 1))
				.bounds(controlX + CONTROL_WIDTH - STEPPER_BUTTON, y, STEPPER_BUTTON, BUTTON_HEIGHT).build());
		y += ROW_HEIGHT;

		// Shape. The cycle button carries its own label, so no separate text is drawn.
		addRenderableWidget(CycleButton.<AutoPlantConfig.Shape>builder(
						shape -> Component.literal(shape.name().toLowerCase()), draft.shape())
				.withValues(AutoPlantConfig.Shape.values())
				.create(controlX, y, CONTROL_WIDTH, BUTTON_HEIGHT,
						Component.translatable("option.automated_planting.shape"),
						(button, value) -> draft = withShape(value)));
		y += ROW_HEIGHT;

		// Interval.
		addRenderableWidget(Button.builder(Component.literal("-"), b -> setInterval(draft.intervalTicks() - 1))
				.bounds(controlX, y, STEPPER_BUTTON, BUTTON_HEIGHT).build());
		addRenderableWidget(Button.builder(Component.literal("+"), b -> setInterval(draft.intervalTicks() + 1))
				.bounds(controlX + CONTROL_WIDTH - STEPPER_BUTTON, y, STEPPER_BUTTON, BUTTON_HEIGHT).build());
		y += ROW_HEIGHT;

		// Plants per cycle.
		addRenderableWidget(Button.builder(Component.literal("-"), b -> setPlants(draft.plantsPerCycle() - 1))
				.bounds(controlX, y, STEPPER_BUTTON, BUTTON_HEIGHT).build());
		addRenderableWidget(Button.builder(Component.literal("+"), b -> setPlants(draft.plantsPerCycle() + 1))
				.bounds(controlX + CONTROL_WIDTH - STEPPER_BUTTON, y, STEPPER_BUTTON, BUTTON_HEIGHT).build());
		y += ROW_HEIGHT;

		// Seed source.
		addRenderableWidget(CycleButton.<AutoPlantConfig.SeedSource>builder(
						source -> Component.translatable("seedSource.automated_planting."
								+ source.name().toLowerCase()), draft.seedSource())
				.withValues(AutoPlantConfig.SeedSource.values())
				.create(controlX, y, CONTROL_WIDTH, BUTTON_HEIGHT,
						Component.translatable("option.automated_planting.seedSource"),
						(button, value) -> draft = withSeedSource(value)));
		y += ROW_HEIGHT;

		// Debug logging.
		addRenderableWidget(CycleButton.onOffBuilder(draft.debugLog())
				.create(controlX, y, CONTROL_WIDTH, BUTTON_HEIGHT,
						Component.translatable("option.automated_planting.debug"),
						(button, value) -> draft = new AutoPlantSettings(draft.radius(), draft.shape(),
								draft.intervalTicks(), draft.plantsPerCycle(), draft.seedSource(), value)));
		y += ROW_HEIGHT;

		// "Fastest" preset.
		addRenderableWidget(Button.builder(Component.translatable("option.automated_planting.fastest"), b -> {
			draft = new AutoPlantSettings(draft.radius(), draft.shape(), 1,
					AutoPlantConfig.MAX_PLANTS_PER_CYCLE, draft.seedSource(), draft.debugLog());
			rebuildWidgets();
		}).bounds(controlX, y, CONTROL_WIDTH, BUTTON_HEIGHT).build());
		y += ROW_HEIGHT + 12;

		// Done / Cancel, centred under the content.
		int actionWidth = 80;
		addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> apply())
				.bounds(controlX - (actionWidth + 10) / 2 + 1, y, actionWidth, BUTTON_HEIGHT).build());
		addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> this.onClose())
				.bounds(controlX + CONTROL_WIDTH / 2 + (10 / 2), y, actionWidth, BUTTON_HEIGHT).build());
	}

	private int labelX;
	private int controlX;
	private int firstRowY;

	private void setRadius(int value) {
		draft = new AutoPlantSettings(value, draft.shape(), draft.intervalTicks(),
				draft.plantsPerCycle(), draft.seedSource(), draft.debugLog());
		rebuildWidgets();
	}

	private void setInterval(int value) {
		draft = new AutoPlantSettings(draft.radius(), draft.shape(), value,
				draft.plantsPerCycle(), draft.seedSource(), draft.debugLog());
		rebuildWidgets();
	}

	private void setPlants(int value) {
		draft = new AutoPlantSettings(draft.radius(), draft.shape(), draft.intervalTicks(),
				value, draft.seedSource(), draft.debugLog());
		rebuildWidgets();
	}

	private AutoPlantSettings withShape(AutoPlantConfig.Shape shape) {
		return new AutoPlantSettings(draft.radius(), shape, draft.intervalTicks(),
				draft.plantsPerCycle(), draft.seedSource(), draft.debugLog());
	}

	private AutoPlantSettings withSeedSource(AutoPlantConfig.SeedSource source) {
		return new AutoPlantSettings(draft.radius(), draft.shape(), draft.intervalTicks(),
				draft.plantsPerCycle(), source, draft.debugLog());
	}

	private void apply() {
		AutoPlantSettings safe = draft.validated();

		if (ClientPlayNetworking.canSend(UpdateAutoPlantSettingsPayload.ID)) {
			ClientPlayNetworking.send(new UpdateAutoPlantSettingsPayload(safe));
		}

		// Show the clamped values straight away; the server's reply confirms them.
		this.draft = safe;
		this.onClose();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		// Let the screen draw the blurred background and its own widgets first. The
		// framework entry point (extractRenderStateWithTooltipAndSubtitles) must NOT be
		// called from here: it blurs the background, and blurring twice in one frame
		// throws "Can only blur once per frame".
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);

		graphics.centeredText(this.font, this.title, this.width / 2, 26, 0xFFFFFFFF);

		int valueCenter = controlX + CONTROL_WIDTH / 2;
		int y = firstRowY;

		// Radius: label on the left, the number between the two buttons.
		drawLabel(graphics, Component.translatable("option.automated_planting.radius"), y);
		drawValue(graphics, Integer.toString(draft.radius()), valueCenter, y);
		y += ROW_HEIGHT;

		// The shape row is a cycle button that draws its own caption and value.
		y += ROW_HEIGHT;

		drawLabel(graphics, Component.translatable("option.automated_planting.interval"), y);
		drawValue(graphics, Integer.toString(draft.intervalTicks()), valueCenter, y);
		y += ROW_HEIGHT;

		drawLabel(graphics, Component.translatable("option.automated_planting.perCycle"), y);
		drawValue(graphics, Integer.toString(draft.plantsPerCycle()), valueCenter, y);

		// The seed source, debug and preset rows are buttons with their own captions.
	}

	/** Draws a row label to the left of the controls. */
	private void drawLabel(GuiGraphicsExtractor graphics, Component label, int y) {
		graphics.text(this.font, label, labelX, y + 6, 0xFFFFFFFF);
	}

	/** Draws the current value centred between the minus and plus buttons. */
	private void drawValue(GuiGraphicsExtractor graphics, String value, int centerX, int y) {
		graphics.centeredText(this.font, Component.literal(value), centerX, y + 6, 0xFFFFE080);
	}
}
