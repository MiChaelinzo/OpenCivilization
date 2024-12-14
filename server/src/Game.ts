import { Player } from "./Player";
import { State } from "./state/State";
import { WebSocket } from "ws";
import { ServerEvents } from "./Events";

/**
 * Game class is responsible for managing the state of the game, and players.
 */
export class Game {
  private static gameInstance: Game;

  private currentState: State;
  private states: Map<string, State>;
  private players: Map<string, Player>;

  private constructor() {
    this.states = new Map<string, State>();

    // Set up the listener for the "setState" event. Changes the game-state.
    ServerEvents.on({
      eventName: "setState",
      parentObject: this,
      callback: (data: JSON) => {
        this.setState(data["state"]);
      },
      globalEvent: true
    });

    // Set up the listener for the "connectedPlayers" event. Return all connected players in-game.
    ServerEvents.on({
      eventName: "connectedPlayers",
      parentObject: this,
      callback: (data: JSON, websocket) => {
        // Get all player names and the name of the requesting player.
        const requestingPlayerName = this.getPlayerFromWebsocket(websocket)?.getName();
        // Send the names to the requesting player.
        websocket.send(
          JSON.stringify({
            event: "connectedPlayers",
            players: this.getPlayerJSONS(),
            requestingName: requestingPlayerName
          })
        );
      },
      globalEvent: true
    });

    // Set up the listener for the "playerQuit" event.
    ServerEvents.on({
      eventName: "playerQuit",
      parentObject: this,
      callback: (data: JSON) => {
        // If only one player is remaining, set the state to "lobby".
        if (this.players.size <= 1) {
          this.setState("lobby");
        }
      },
      globalEvent: true
    });
  }

  public static getInstance() {
    return this.gameInstance;
  }

  /**
   * Initializes the game by setting up server event listeners for various events.
   */
  public static init() {
    this.gameInstance = new Game();
  }

  /**
   * Adds a state to the states map.
   * @param stateName - The name of the state.
   * @param state - The state object to add.
   */
  public addState(stateName: string, state: State) {
    this.states.set(stateName, state);
  }

  /**
   * Sets the current state of the game.
   * @param stateName - The name of the state to set.
   */
  public setState(stateName: string) {
    const newState = this.states.get(stateName) as State;

    if (this.currentState != null) {
      this.currentState.onDestroyed();
    }

    this.currentState = newState;
    this.currentState.onInitialize();
  }

  /**
   * Returns the map containing all the players in the game.
   * Creates a new map if it doesn't exist.
   */
  public getPlayers() {
    if (!this.players) {
      this.players = new Map<string, Player>();
    }

    return this.players;
  }

  /**
   * Returns the player object associated with a websocket.
   * @param websocket - The websocket to check.
   * @returns - The player object associated with the websocket or undefined if not found.
   */
  public getPlayerFromWebsocket(websocket: WebSocket) {
    for (const player of this.players.values()) {
      if (player.getWebsocket() === websocket) {
        return player;
      }
    }

    return undefined;
  }

  public getCurrentStateAs<T extends State>(): T {
    return this.currentState as T;
  }

  private getPlayerJSONS() {
    const playerJSONS = [];

    for (const player of this.players.values()) {
      playerJSONS.push(player.toJSON());
    }

    return playerJSONS;
  }
}
