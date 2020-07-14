package me.rhin.openciv.game.map;

import java.util.LinkedList;
import java.util.Queue;
import java.util.Random;

import com.badlogic.gdx.math.Vector2;

import me.rhin.openciv.Civilization;
import me.rhin.openciv.game.CivGame;
import me.rhin.openciv.game.map.tile.Tile;
import me.rhin.openciv.game.map.tile.TileType;
import me.rhin.openciv.listener.AddUnitListener;
import me.rhin.openciv.listener.ReceiveMapChunkListener;
import me.rhin.openciv.shared.packet.type.MapChunkPacket;
import me.rhin.openciv.util.MathHelper;

public class GameMap implements ReceiveMapChunkListener {

	public static final int WIDTH = 80; // Default: 104
	public static final int HEIGHT = 52; // Default: 64
	public static final int MAX_NODES = WIDTH * HEIGHT;
	private static final int CONTINENT_AMOUNT = 780; // Default: 780

	private Tile[][] tiles;

	private int[][] oddEdgeAxis = { { 0, -1 }, { 1, -1 }, { 1, 0 }, { 1, 1 }, { 0, 1 }, { -1, 0 } };
	private int[][] evenEdgeAxis = { { -1, -1 }, { 0, -1 }, { 1, 0 }, { 0, 1 }, { -1, 1 }, { -1, 0 } };

	public GameMap() {

		tiles = new Tile[WIDTH][HEIGHT];
		for (int x = 0; x < WIDTH; x++) {
			for (int y = 0; y < HEIGHT; y++) {
				Tile tile = new Tile(this, TileType.OCEAN, x, y);
				tiles[x][y] = tile;
			}
		}

		initializeEdges();
		// TODO: Check if game is singleplayer or multiplayer. e.g.
		// if(game.isSingleplayer()).
		// generateTerrain();

		Civilization.getInstance().getEventManager().addListener(ReceiveMapChunkListener.class, this);
	}

	@Override
	public void onReciveMapChunk(MapChunkPacket packet) {
		// Start from 0 and go up the Y axis.
		for (int i = 0; i < MapChunkPacket.CHUNK_SIZE; i++) {
			for (int j = 0; j < MapChunkPacket.CHUNK_SIZE; j++) {
				Tile tile = tiles[packet.getChunkX() + i][packet.getChunkY() + j];
				
				tile.setTileType(TileType.fromId(packet.getBaseTileChunk()[i][j]));
				tile.setTileType(TileType.fromId(packet.getLayeredTileChunk()[i][j]));
				tile.setTileType(TileType.fromId(packet.getLuxuryTileChunk()[i][j]));
				Civilization.getInstance().getScreenManager().getCurrentScreen().getStage().addActor(tile);
			}
		}
	}

	public Tile getTileFromLocation(float x, float y) {
		float width = tiles[0][0].getWidth();
		float height = tiles[0][0].getHeight();

		int gridY = (int) (y / height);
		int gridX;

		if (gridY % 2 == 0) {
			gridX = (int) (x / width);
		} else
			gridX = (int) ((x - (width / 2)) / width);

		if (gridX < 0 || gridX > WIDTH - 1 || gridY < 0 || gridY > HEIGHT - 1)
			return null;

		Tile nearTile = tiles[gridX][gridY];
		Tile[] tiles = nearTile.getAdjTiles();

		// Check if the mouse is inside the surrounding tiles.
		Vector2 mouseVector = new Vector2(x, y);
		Vector2 mouseExtremeVector = new Vector2(x + 1000, y);

		// FIXME: I kind of want to add the near tile to the adjTile Array. This is
		// redundant.

		Tile locatedTile = null;

		if (MathHelper.isInsidePolygon(nearTile.getVectors(), mouseVector, mouseExtremeVector)) {
			locatedTile = nearTile;
		} else
			for (Tile tile : tiles) {
				if (tile == null)
					continue;
				if (MathHelper.isInsidePolygon(tile.getVectors(), mouseVector, mouseExtremeVector)) {
					locatedTile = tile;
					break;
				}
			}

		return locatedTile;
	}

	public Tile[][] getTiles() {
		return tiles;
	}

	private void initializeEdges() {
		for (int x = 0; x < WIDTH; x++) {
			for (int y = 0; y < HEIGHT; y++) {
				// Set the 6 edges of the hexagon.

				int[][] edgeAxis;
				if (y % 2 == 0)
					edgeAxis = evenEdgeAxis;
				else
					edgeAxis = oddEdgeAxis;

				for (int i = 0; i < edgeAxis.length; i++) {

					int edgeX = x + edgeAxis[i][0];
					int edgeY = y + edgeAxis[i][1];

					if (edgeX == -1 || edgeY == -1 || edgeX > WIDTH - 1 || edgeY > HEIGHT - 1) {
						tiles[x][y].setEdge(i, null);
						continue;
					}

					tiles[x][y].setEdge(i, tiles[x + edgeAxis[i][0]][y + edgeAxis[i][1]]);
				}
			}
		}
	}
}
