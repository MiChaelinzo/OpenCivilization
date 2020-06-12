package me.rhin.openciv.server.game.unit;

import me.rhin.openciv.server.game.Player;
import me.rhin.openciv.server.game.map.Tile;

public class Galley extends Unit {

	public Galley(Player player, Tile standingTile) {
		super(player, standingTile);
	}

	@Override
	public int getMovementCost(Tile tile) {
		if (!tile.getTileType().isWater())
			return 1000000;
		else
			return tile.getTileType().getMovementCost();
	}
}
