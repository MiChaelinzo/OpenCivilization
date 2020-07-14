package me.rhin.openciv.server.game.map;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Random;

import org.java_websocket.WebSocket;

import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Json;

import me.rhin.openciv.server.Server;
import me.rhin.openciv.server.game.Game;
import me.rhin.openciv.server.game.map.tile.Tile;
import me.rhin.openciv.server.game.map.tile.Tile.TileTypeWrapper;
import me.rhin.openciv.server.game.map.tile.TileType;
import me.rhin.openciv.server.game.map.tile.TileType.TileLayer;
import me.rhin.openciv.server.game.map.tile.TileType.TileProperty;
import me.rhin.openciv.server.game.unit.Unit;
import me.rhin.openciv.server.listener.MapRequestListener;
import me.rhin.openciv.shared.packet.type.AddUnitPacket;
import me.rhin.openciv.shared.packet.type.FinishLoadingPacket;
import me.rhin.openciv.shared.packet.type.MapChunkPacket;
import me.rhin.openciv.shared.util.MathHelper;

public class GameMap implements MapRequestListener {
	public static final int WIDTH = 80; // Default: 104
	public static final int HEIGHT = 52; // Default: 64
	public static final int MAX_NODES = WIDTH * HEIGHT;
	private static final int CONTINENT_AMOUNT = 550; // Default: 780

	private Game game;
	private Tile[][] tiles;
	private ArrayList<Rectangle> mapPartition;

	private int[][] oddEdgeAxis = { { 0, -1 }, { 1, -1 }, { 1, 0 }, { 1, 1 }, { 0, 1 }, { -1, 0 } };
	private int[][] evenEdgeAxis = { { -1, -1 }, { 0, -1 }, { 1, 0 }, { 0, 1 }, { -1, 1 }, { -1, 0 } };

	public GameMap(Game game) {
		this.game = game;

		tiles = new Tile[WIDTH][HEIGHT];
		for (int x = 0; x < WIDTH; x++) {
			for (int y = 0; y < HEIGHT; y++) {
				Tile tile = new Tile(this, TileType.OCEAN, x, y);
				tiles[x][y] = tile;
			}
		}

		this.mapPartition = new ArrayList<>();

		initializeEdges();

		Server.getInstance().getEventManager().addListener(MapRequestListener.class, this);
	}

	@Override
	public void onMapRequest(WebSocket conn) {
		Json json = new Json();

		ArrayList<AddUnitPacket> addUnitPackets = new ArrayList<>();

		for (int x = 0; x < GameMap.WIDTH; x++) {
			for (int y = 0; y < GameMap.HEIGHT; y++) {
				if (x % 4 == 0 && y % 4 == 0) {
					MapChunkPacket mapChunkPacket = new MapChunkPacket();

					ArrayList<int[][]> chunkLayers = new ArrayList<>();
					for (int i = 0; i < 3; i++) {
						int[][] chunkLayer = new int[MapChunkPacket.CHUNK_SIZE][MapChunkPacket.CHUNK_SIZE];
						for (int a = 0; a < chunkLayer.length; a++)
							for (int b = 0; b < chunkLayer[a].length; b++)
								chunkLayer[a][b] = -1;
						chunkLayers.add(chunkLayer);
					}
					for (int i = 0; i < MapChunkPacket.CHUNK_SIZE; i++) {
						for (int j = 0; j < MapChunkPacket.CHUNK_SIZE; j++) {
							int tileX = x + i;
							int tileY = y + j;
							Tile tile = tiles[tileX][tileY];
							for (int k = 0; k < tile.getTileTypeWrappers().size(); k++)
								chunkLayers.get(k)[i][j] = ((TileTypeWrapper) tile.getTileTypeWrappers().toArray()[k])
										.getTileType().getID();

							for (Unit unit : tile.getUnits()) {
								AddUnitPacket addUnitPacket = new AddUnitPacket();
								String unitName = unit.getClass().getSimpleName().substring(0,
										unit.getClass().getSimpleName().indexOf("Unit"));
								addUnitPacket.setUnit(unit.getPlayerOwner().getName(), unitName, unit.getID(), tileX,
										tileY);
								addUnitPackets.add(addUnitPacket);
							}

						}
					}
					mapChunkPacket.setTileCunk(chunkLayers.get(0), chunkLayers.get(1), chunkLayers.get(2));
					mapChunkPacket.setChunkLocation(x, y);

					conn.send(json.toJson(mapChunkPacket));
				}
			}
		}

		for (AddUnitPacket packet : addUnitPackets)
			conn.send(json.toJson(packet));

		// NOTE: The packet sent below assumes that no other loading packets have been
		// sent
		conn.send(json.toJson(new FinishLoadingPacket()));
	}

	public void resetTerrain() {
		for (int x = 0; x < WIDTH; x++) {
			for (int y = 0; y < HEIGHT; y++) {
				tiles[x][y].setTileType(TileType.OCEAN);
			}
		}
	}

	public void generateTerrain() {
		Random rnd = new Random();

		splitMapPartition();

		for (int i = 0; i < CONTINENT_AMOUNT; i++) {
			int randomX = rnd.nextInt(WIDTH - 1);
			int randomY = rnd.nextInt(HEIGHT - 1);

			Tile tile = tiles[randomX][randomY];
			growTile(tile, rnd.nextInt(rnd.nextInt(5 - 3 + 1) + 5));
		}

		// Remove 1 tile ponds
		for (int x = 0; x < WIDTH; x++) {
			for (int y = 0; y < HEIGHT; y++) {
				Tile tile = tiles[x][y];
				if (!tile.containsTileType(TileType.OCEAN))
					continue;

				Tile targetTile = null;
				boolean adjOceanTile = false;
				for (Tile adjTile : tile.getAdjTiles()) {
					if (adjTile == null)
						continue;

					targetTile = adjTile;

					if (adjTile.containsTileType(TileType.OCEAN))
						adjOceanTile = true;
				}

				if (!adjOceanTile) {
					tile.setTileType(targetTile.getBaseTileType());
					continue;
				}

				// TODO: Add shallow sea tiles
			}
		}

		// Generate mountain chains
		Queue<Tile> mountainTiles = new LinkedList<>();

		for (int i = 0; i < 25; i++) {
			Tile tile = tiles[rnd.nextInt(WIDTH - 1)][rnd.nextInt(HEIGHT - 1)];
			System.out.println(isFlatTile(tile));
			if (isFlatTile(tile)) {
				tile.setTileType(TileType.MOUNTAIN);
				mountainTiles.add(tile);
			} else
				i--;
		}

		while (!mountainTiles.isEmpty()) {
			Tile tile = mountainTiles.remove();
			for (Tile adjTile : tile.getAdjTiles()) {
				if (rnd.nextInt(6) > 3 && isFlatTile(adjTile)) {
					tile.setTileType(TileType.MOUNTAIN);
					mountainTiles.add(adjTile);
					break;
				}
			}
		}

		// FIXME: These loops below are redundant
		// Generate forests
		Queue<Tile> forestTiles = new LinkedList<>();

		for (int i = 0; i < 100; i++) {
			Tile tile = tiles[rnd.nextInt(WIDTH - 1)][rnd.nextInt(HEIGHT - 1)];
			if (isFlatTile(tile)) {
				tile.setTileType(TileType.FOREST);
				forestTiles.add(tile);
			} else
				i--;
		}

		while (!forestTiles.isEmpty()) {
			Tile tile = forestTiles.remove();
			for (Tile adjTile : tile.getAdjTiles()) {
				if (rnd.nextInt(20) > 15 && isFlatTile(adjTile)) {
					tile.setTileType(TileType.FOREST);
					forestTiles.add(adjTile);
				}
			}
		}

		// Generate jungle
		Queue<Tile> jungleTiles = new LinkedList<>();

		for (int i = 0; i < 100; i++) {
			Tile tile = tiles[rnd.nextInt(WIDTH - 1)][rnd.nextInt(HEIGHT - 1)];
			if (isFlatTile(tile)) {
				tile.setTileType(TileType.JUNGLE);
				jungleTiles.add(tile);
			} else
				i--;
		}

		while (!jungleTiles.isEmpty()) {
			Tile tile = jungleTiles.remove();
			for (Tile adjTile : tile.getAdjTiles()) {
				if (rnd.nextInt(20) > 15 && isFlatTile(adjTile)) {
					tile.setTileType(TileType.JUNGLE);
					jungleTiles.add(adjTile);
				}
			}
		}

		// Generate hills
		Queue<Tile> hillTiles = new LinkedList<>();

		for (int i = 0; i < 250; i++) {
			Tile tile = tiles[rnd.nextInt(WIDTH - 1)][rnd.nextInt(HEIGHT - 1)];
			if (isFlatTile(tile)) {
				if (tile.containsTileType(TileType.GRASS))
					tile.setTileType(TileType.GRASS_HILL);
				else
					tile.setTileType(TileType.PLAINS_HILL);
				hillTiles.add(tile);
			} else
				i--;
		}

		while (!hillTiles.isEmpty()) {
			Tile tile = hillTiles.remove();
			for (Tile adjTile : tile.getAdjTiles()) {
				if (rnd.nextInt(20) > 17 && isFlatTile(adjTile)) {
					if (tile.containsTileType(TileType.GRASS))
						tile.setTileType(TileType.GRASS_HILL);
					else if (tile.containsTileType(TileType.PLAINS))
						tile.setTileType(TileType.PLAINS_HILL);
					hillTiles.add(adjTile);
				}
			}
		}

		generateResource(TileType.HORSES, game.getPlayers().size() * 4, TileType.GRASS, TileType.PLAINS);
		generateResource(TileType.IRON, game.getPlayers().size() * 60, TileType.GRASS, TileType.PLAINS,
				TileType.PLAINS_HILL, TileType.GRASS_HILL);
		generateResource(TileType.COPPER, game.getPlayers().size(), TileType.GRASS, TileType.PLAINS,
				TileType.PLAINS_HILL, TileType.GRASS_HILL);
		generateResource(TileType.COTTON, game.getPlayers().size(), TileType.GRASS, TileType.PLAINS);
		generateResource(TileType.GEMS, game.getPlayers().size(), TileType.GRASS, TileType.PLAINS);

	}

	private void generateResource(TileType tileType, int amount, TileType... exclusiveTiles) {
		Random rnd = new Random();
		while (amount > 0) {
			for (Rectangle rect : mapPartition) {
				while (true) {
					int rndX = rnd.nextInt((int) (rect.getX() + rect.getWidth() - 1) - (int) rect.getX() + 1)
							+ (int) rect.getX();

					int rndY = rnd.nextInt((int) (rect.getY() + rect.getHeight() - 1) - (int) rect.getY() + 1)
							+ (int) rect.getY();
					Tile tile = tiles[rndX][rndY];

					boolean isExclusiveTile = false;
					boolean containsResource = false;
					for (TileTypeWrapper tileWrapper : tile.getTileTypeWrappers())
						if (tileWrapper.getTileType().hasProperty(TileProperty.RESOURCE)) {
							containsResource = true;
						}

					if (!containsResource) {
						for (TileType exclusiveType : exclusiveTiles) {
							if (exclusiveType.getTileLayer() == TileLayer.BASE) {
								// If the exclusiveType is a base layer, make sure no other layers are there.
								isExclusiveTile = tile.onlyHasTileType(exclusiveType);
							} else if (exclusiveType.getTileLayer().ordinal() > 0)
								isExclusiveTile = tile.containsTileType(exclusiveType);
						}
					}

					if (exclusiveTiles.length < 1 || isExclusiveTile) {
						tile.setTileType(tileType);
						amount--;
						break;
					}
				}
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

	public Game getGame() {
		return game;
	}

	public ArrayList<Rectangle> getMapPartition() {
		return mapPartition;
	}

	private void splitMapPartition() {
		int playerSize = game.getPlayers().size();
		if (playerSize < 2) {
			mapPartition.add(new Rectangle(0, 0, WIDTH, HEIGHT));
			return;
		}

		int numRects = (playerSize % 2 == 0) ? playerSize : playerSize + 1;

		int columns = (int) Math.ceil(Math.sqrt(numRects));
		int fullRows = numRects / columns;

		int width = WIDTH / columns;
		int height = HEIGHT / fullRows;

		for (int y = 0; y < fullRows; ++y)
			for (int x = 0; x < columns; ++x)
				mapPartition.add(new Rectangle(x * width, y * height, width, height));
	}

	// FIXME: Rename to adjecent Tiles?
	private void initializeEdges() {
		// n^2 * 6
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

	private void growTile(Tile tile, int amount) {
		Random rnd = new Random();

		TileType tileType = null;

		if (tile.containsTileType(TileType.OCEAN)) {
			if (rnd.nextInt(10) > 1)
				tileType = TileType.GRASS;
			else
				tileType = TileType.PLAINS;
		} else
			tileType = tile.getBaseTileType();

		// #1
		// Set the initial tile the grass
		if (HEIGHT - tile.getGridY() < 10 || tile.getGridY() < 10 || WIDTH - tile.getGridX() < 10
				|| tile.getGridX() < 10)
			return;

		tile.setTileType(tileType);
		amount--;

		// #2

		int edgeFillIndex = 2;
		for (int edgeIndex = 0; edgeIndex < tile.getAdjTiles().length; edgeIndex++) {
			Tile currentTile = tile.getAdjTiles()[edgeIndex];
			for (int i = 0; i < amount; i++) {

				// FIXME: The chance of this happening should decrease as our growth increases.
				// Also, this chance increase the closer the x & y is to the center. (Should be
				// 100% of grass in the center of the map).
				if (rnd.nextInt(8) > 2)
					currentTile.setTileType(tileType);

				// Fill in the gap created
				if (amount > 1) {
					int fillAmount = amount - 1;
					Tile currentFillTile = currentTile;
					for (int j = 0; j < fillAmount; j++) {
						if (rnd.nextInt(8) > 2)
							currentFillTile.getAdjTiles()[edgeFillIndex].setTileType(tileType);
						currentFillTile = currentFillTile.getAdjTiles()[edgeFillIndex];
					}
				}

				currentTile = currentTile.getAdjTiles()[edgeIndex];
			}
			edgeFillIndex++;

			if (edgeFillIndex > 5)
				edgeFillIndex = 0;
		}
	}

	private boolean isFlatTile(Tile tile) {
		return tile.onlyHasTileType(TileType.GRASS) || tile.onlyHasTileType(TileType.PLAINS);
	}
}