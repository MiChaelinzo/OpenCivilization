import { Game } from "../../Game";
import { ServerEvents } from "../../Events";
import { Player } from "../../Player";
import { State } from "../State";
import fs from "fs";
import random from "random";
import YAML from "yaml";

let playerIndex = 1;

export class LobbyState extends State {
  private playableCivs: Record<string, any>[];

  public onInitialize() {
    console.log("Lobby state initialized");
    playerIndex = 1;

    // Load available civilizations from config file
    const civYAMLData = YAML.parse(fs.readFileSync("./config/civilizations.yml", "utf-8"));
    //Convert civsData from YAML to JSON:
    this.playableCivs = JSON.parse(JSON.stringify(civYAMLData.civilizations));

    ServerEvents.on({
      eventName: "connection",
      parentObject: this,
      callback: (data, websocket) => {
        // Initialize player name
        const playerName = "Player" + playerIndex;
        playerIndex++;

        console.log(playerName + " has joined the lobby");

        const newPlayer = new Player(playerName, websocket);
        Game.getInstance().getPlayers().set(playerName, newPlayer);

        // Send playerJoin data to other connected players
        for (const player of Array.from(Game.getInstance().getPlayers().values())) {
          player.sendNetworkEvent({
            event: "playerJoin",
            playerName: playerName
          });
        }

        newPlayer.sendNetworkEvent({ event: "setScene", scene: "lobby" });
      }
    });

    ServerEvents.on({
      eventName: "availableCivs",
      parentObject: this,
      callback: (_, websocket) => {
        const player = Game.getInstance().getPlayerFromWebsocket(websocket);
        const playableCivs = [];

        //Extract name and icon_name from playableCivs:
        for (const civ of this.playableCivs) {
          playableCivs.push({ name: civ.name, icon_name: civ.icon_name });
        }

        player.sendNetworkEvent({
          event: "availableCivs",
          civs: playableCivs
        });
      }
    });

    ServerEvents.on({
      eventName: "civInfo",
      parentObject: this,
      callback: (data, websocket) => {
        const player = Game.getInstance().getPlayerFromWebsocket(websocket);

        // Get civ from this.playerCivs JSON list:
        const civilization = this.getCivByName(data["name"]);

        if (civilization) {
          player.sendNetworkEvent({
            event: "civInfo",
            name: civilization.name,
            icon_name: civilization.icon_name,
            start_bias_desc: civilization.start_bias_desc,
            unique_unit_descs: civilization.unique_unit_descs,
            unique_building_descs: civilization.unique_building_descs,
            ability_descs: civilization.ability_descs
          });
        }
      }
    });

    ServerEvents.on({
      eventName: "selectCiv",
      parentObject: this,
      callback: (data, websocket) => {
        const player = Game.getInstance().getPlayerFromWebsocket(websocket);
        //TODO: Check if this civ is already selected.

        const civilization = this.getCivByName(data["name"]);
        player.setCivilizationData(civilization);

        Game.getInstance()
          .getPlayers()
          .forEach((gamePlayer) => {
            gamePlayer.sendNetworkEvent({
              event: "selectCiv",
              name: civilization.name,
              playerName: player.getName(),
              civData: civilization
            });
          });
      }
    });
  }

  public getCivByName(name: string) {
    let civilization = undefined;
    for (const civ of this.playableCivs) {
      if (civ.name === name) {
        civilization = civ;
      }
    }

    return civilization;
  }

  public onDestroyed() {
    //Assign players w/o a civ a non-assigned random civilization:
    Game.getInstance()
      .getPlayers()
      .forEach((player) => {
        if (!player.getCivilizationData()) {
          player.setCivilizationData(this.getRandomNonAssignedCiv());
        }
      });

    return super.onDestroyed();
  }

  private getRandomNonAssignedCiv(): Record<string, any> {
    const assignedCivs = [];
    Game.getInstance()
      .getPlayers()
      .forEach((player) => {
        if (player.getCivilizationData()) {
          assignedCivs.push(player.getCivilizationData());
        }
      });
    const nonAssignedCivs = this.playableCivs.filter((civ) => {
      return !assignedCivs.includes(civ);
    });

    //Pick random non-assigned civ:
    const randomIndex = random.int(0, nonAssignedCivs.length - 1);
    return nonAssignedCivs[randomIndex];
  }
}
