package me.rhin.openciv.game.unit;

import java.util.ArrayList;
import java.util.Arrays;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Action;
import com.badlogic.gdx.scenes.scene2d.Actor;

import me.rhin.openciv.Civilization;
import me.rhin.openciv.asset.TextureEnum;
import me.rhin.openciv.game.map.GameMap;
import me.rhin.openciv.game.map.tile.Tile;
import me.rhin.openciv.game.player.Player;
import me.rhin.openciv.listener.ShapeRenderListener;
import me.rhin.openciv.shared.packet.type.MapRequestPacket;
import me.rhin.openciv.shared.packet.type.MoveUnitPacket;
import me.rhin.openciv.ui.overlay.UnitOverlay;
import me.rhin.openciv.ui.screen.ScreenEnum;
import me.rhin.openciv.ui.screen.type.InGameScreen;

public abstract class Unit extends Actor implements ShapeRenderListener {

	// FIXME: This should be an arraylist of libGDX actions not buttons.
	protected ArrayList<Action> customActions;
	private Player playerOwner;
	private ArrayList<Vector2[]> pathVectors;
	private Tile standingTile, targetTile;
	private Sprite sprite, selectionSprite, targetSelectionSprite;
	private boolean selected;
	private int movement;
	private float health;

	public Unit(String unitName, Player playerOwner, Tile standingTile, TextureEnum assetEnum) {
		Civilization.getInstance().getEventManager().addListener(ShapeRenderListener.class, this);

		setName(unitName);
		this.playerOwner = playerOwner;
		this.customActions = new ArrayList<>();
		this.pathVectors = new ArrayList<>();
		this.standingTile = standingTile;
		this.sprite = assetEnum.sprite();
		this.selectionSprite = TextureEnum.UI_SELECTION.sprite();
		// TODO: Change this sprite to a different texture
		this.targetSelectionSprite = TextureEnum.UI_SELECTION.sprite();

		setPosition(standingTile.getVectors()[0].x - standingTile.getWidth() / 2, standingTile.getVectors()[0].y + 4);
		setSize(standingTile.getWidth(), standingTile.getHeight());

		this.movement = 3;
	}

	public Unit(UnitParameter unitParameter, TextureEnum assetEnum) {
		this(unitParameter.getUnitName(), unitParameter.getPlayerOwner(), unitParameter.getStandingTile(), assetEnum);
	}

	public abstract int getMovementCost(Tile tile);

	@Override
	public void act(float delta) {
		super.act(delta);
	}

	@Override
	public void draw(Batch batch, float parentAlpha) {
		if (selected)
			selectionSprite.draw(batch);

		if (targetTile != null && pathVectors.size() <= movement) {
			targetSelectionSprite.draw(batch);
		}

		sprite.draw(batch);

	}

	public boolean setTargetTile(Tile targetTile) {
		this.targetTile = null;
		if (targetTile == null)
			return false;

		if (targetTile.equals(this.targetTile))
			return false;

		pathVectors.clear();

		// TODO: Determine if the unit can walk on the tile.

		targetSelectionSprite.setPosition(targetTile.getVectors()[0].x - targetTile.getWidth() / 2,
				targetTile.getVectors()[0].y + 4);
		targetSelectionSprite.setSize(targetTile.getWidth(), targetTile.getHeight());

		GameMap map = targetTile.getMap();

		// Find the shortest path to the target tile.
		// Remember:
		/*
		 * EDGES are lines pointing to another node. (Stores the others nodes location
		 * or instance).
		 * 
		 * NODES or VERTICES are the nodes (in this case our hexes) on the map!.
		 */

		// https://en.wikipedia.org/wiki/A*_search_algorithm
		// https://www.youtube.com/watch?v=6TsL96NAZCo

		// FIXME: This should only be the size of our targetTileGrid X & Y.
		// Remember, stack is LIFO.

		// int aScore = costOfPath + hurestic of the target point.

		// Tile should store is hurestic, tileNode should store is aScore.

		// FIXME: This shouldn't be a static number. (E.g. account for hills, ect.)
		// SHOULD NEVERRRR OVERESTIMATE tough
		int h = 1; // Lowest possible cost to reach nearest tile. (Do we want to
					// overestimate
		// this?).
		ArrayList<Tile> openSet = new ArrayList<>();
		Tile[][] cameFrom = new Tile[GameMap.WIDTH][GameMap.HEIGHT];
		int[][] gScores = new int[GameMap.WIDTH][GameMap.HEIGHT];
		int[][] fScores = new int[GameMap.WIDTH][GameMap.HEIGHT];

		for (int[] gScore : gScores)
			Arrays.fill(gScore, GameMap.MAX_NODES);
		for (int[] fScore : fScores)
			Arrays.fill(fScore, GameMap.MAX_NODES);

		gScores[standingTile.getGridX()][standingTile.getGridY()] = 0;
		fScores[standingTile.getGridX()][standingTile.getGridY()] = h;
		openSet.add(standingTile); // h or 0 ???

		while (openSet.size() > 0) {

			// Get the current tileNode /w the lowest fScore[] value.
			// FIXME: This can be O(1) /w a proper priority queue.
			Tile current = removeSmallest(openSet, fScores);

			if (current.equals(targetTile))
				break;

			openSet.remove(current);
			for (Tile adjTile : current.getAdjTiles()) {
				if (adjTile == null)
					continue;

				// We can either use adjTile or current tile movement cost here, doesn't really
				// matter.

				int tenativeGScore = gScores[current.getGridX()][current.getGridY()] + getMovementCost(adjTile);

				if (tenativeGScore < gScores[adjTile.getGridX()][adjTile.getGridY()]) {

					cameFrom[adjTile.getGridX()][adjTile.getGridY()] = current;
					gScores[adjTile.getGridX()][adjTile.getGridY()] = tenativeGScore;

					int adjFScore = gScores[adjTile.getGridX()][adjTile.getGridY()] + h;
					fScores[adjTile.getGridX()][adjTile.getGridY()] = adjFScore;
					if (!openSet.contains(adjTile))
						openSet.add(adjTile);
				}
			}
		}

		// Iterate through the parent array to get back to the origin tile.

		Tile parentTile = cameFrom[targetTile.getGridX()][targetTile.getGridY()];

		// If it's moving to itself.
		if (parentTile == null)
			return false;

		int iterations = 0;
		while (parentTile != null) {
			Tile nextTile = cameFrom[parentTile.getGridX()][parentTile.getGridY()];

			if (nextTile != null) {
				Vector2[] tileVectors = new Vector2[2];
				tileVectors[0] = new Vector2(parentTile.getX() + parentTile.getWidth() / 2,
						parentTile.getY() + parentTile.getHeight() / 2 + 4);
				tileVectors[1] = new Vector2(nextTile.getX() + nextTile.getWidth() / 2,
						nextTile.getY() + nextTile.getHeight() / 2 + 4);
				pathVectors.add(tileVectors);
			}

			if (nextTile == null)
				nextTile = targetTile;

			if (parentTile.equals(targetTile))
				break;

			if (iterations >= GameMap.MAX_NODES) {
				Gdx.app.log(Civilization.LOG_TAG, "ERROR: Pathing iteration error");
				break;
			}

			parentTile = nextTile;
			iterations++;
		}

		if (movement < iterations) {
			this.targetTile = null;
			return false;
		}
		this.targetTile = targetTile;

		return true;
	}

	@Override
	public void onShapeRender(ShapeRenderer shapeRenderer) {
		shapeRenderer.setColor(Color.YELLOW);
		int index = pathVectors.size() - 1;
		for (Vector2[] vectors : pathVectors) {
			if (movement <= index)
				break;
			shapeRenderer.line(vectors[0], vectors[1]);
			index--;
		}
	}

	private Tile removeSmallest(ArrayList<Tile> queue, int fScore[][]) {
		int smallest = Integer.MAX_VALUE;
		Tile smallestTile = null;
		for (Tile tile : queue) {
			if (fScore[tile.getGridX()][tile.getGridY()] < smallest) {
				smallest = fScore[tile.getGridX()][tile.getGridY()];
				smallestTile = tile;
			}
		}

		queue.remove(smallestTile);
		return smallestTile;
	}

	public void moveToTargetTile() {
		if (targetTile == null)
			return;

		pathVectors.clear();

		standingTile.removeUnit(this);
		targetTile.addUnit(this);

		setPosition(targetTile.getVectors()[0].x - targetTile.getWidth() / 2, targetTile.getVectors()[0].y + 4);
		standingTile = targetTile;

		targetTile = null;
	}

	public void sendMovementPacket() {
		if (targetTile == null)
			return;

		MoveUnitPacket packet = new MoveUnitPacket();
		packet.setUnit(playerOwner.getName(), getClass().getSimpleName(), standingTile.getGridX(),
				standingTile.getGridY(), targetTile.getGridX(), targetTile.getGridY());
		Civilization.getInstance().getNetworkManager().sendPacket(packet);
	}

	public void setPosition(float x, float y) {
		sprite.setPosition(x, y);
		selectionSprite.setPosition(x, y);
		super.setPosition(x, y);
	}

	public void setSize(float width, float height) {
		sprite.setSize(width, height);
		selectionSprite.setSize(width, height);
	}

	public void setSelected(final boolean selected) {
		this.selected = selected;

		// Sometimes, due to lag the unit is unselected before it moves, we remove the
		// path vectors to account for that
		final Unit thisUnit = this;
		Gdx.app.postRunnable(new Runnable() {
			public void run() {
				if (!selected) {
					pathVectors.clear();
					targetTile = null;
					((InGameScreen) Civilization.getInstance().getCurrentScreen()).setUnitOverlay(null);
				} else {
					((InGameScreen) Civilization.getInstance().getCurrentScreen())
							.setUnitOverlay(new UnitOverlay(thisUnit));
				}
			}
		});

	}

	public Sprite getSprite() {
		return sprite;
	}

	public boolean isSelected() {
		return selected;
	}

	public Tile getTargetTile() {
		return targetTile;
	}

	public int getMovement() {
		return movement;
	}

	public Player getPlayerOwner() {
		return playerOwner;
	}

	public ArrayList<Action> getCustomActions() {
		return customActions;
	}

	public void setMovement(int movement) {
		this.movement = movement;
	}
}
