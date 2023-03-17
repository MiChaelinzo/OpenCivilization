import { Actor } from "./scene/Actor";
import { Scene } from "./scene/Scene";
import { GameImage } from "./Assets";
import { NetworkEvents } from "./network/Client";

export interface TextOptions {
  text: string;
  x?: number;
  y?: number;
  actor?: Actor;
  color?: string;
  font?: string;
  shadowColor?: string;
  lineWidth?: number;
  shadowBlur?: number;
}
export interface GameOptions {
  /**
   * List of NodeRequire[] objects
   */
  assetList: NodeRequire[];

  /**
   * Color of the canvas background
   */
  canvasColor?: string;
}

export class Game {
  private static canvas: HTMLCanvasElement;
  private static canvasContext: CanvasRenderingContext2D;
  private static scenes: Map<string, Scene>;
  private static currentScene: Scene;
  private static images = [];
  private static countedFrames: number = 0;
  private static lastTimeUpdate = Date.now();
  private static fps: number = 0;
  private static actors: Actor[] = [];
  private static measureQueue: string[];
  private static mouseX: number;
  private static mouseY: number;
  private static runGameLoop: boolean;

  private constructor() {}

  public static init(options: GameOptions, callback: () => void) {
    this.scenes = new Map<string, Scene>();
    //Initialize canvas
    this.canvas = document.getElementById("canvas") as HTMLCanvasElement;
    this.canvas.width = window.innerWidth;
    this.canvas.height = window.innerHeight;
    this.canvasContext = this.canvas.getContext("2d");
    this.canvasContext.fillStyle = options.canvasColor ?? "white";
    this.canvasContext.fillRect(0, 0, this.canvas.width, this.canvas.height);
    this.canvasContext.font = "12px Times new Roman";
    this.canvasContext.imageSmoothingEnabled = false;
    this.measureQueue = [];
    this.runGameLoop = true;

    //Initialize canvas listeners. TODO: Make this less redundant w/ a helper function
    this.canvas.addEventListener("mousemove", (event) => {
      if (this.currentScene) {
        this.currentScene.call("mousemove", {
          x: event.clientX,
          y: event.clientY,
        });
      }

      this.actors.forEach((actor) => {
        actor.call("mousemove", { x: event.clientX, y: event.clientY });
      });

      this.mouseX = event.clientX;
      this.mouseY = event.clientY;
    });

    this.canvas.addEventListener("mousedown", (event) => {
      if (this.currentScene) {
        this.currentScene.call("mousedown", {
          x: event.clientX,
          y: event.clientY,
        });
      }

      this.actors.forEach((actor) => {
        actor.call("mousedown", { x: event.clientX, y: event.clientY });
      });
    });

    this.canvas.addEventListener("mouseup", (event) => {
      if (this.currentScene) {
        this.currentScene.call("mouseup", {
          x: event.clientX,
          y: event.clientY,
        });
      }

      this.actors.forEach((actor) => {
        actor.call("mouseup", { x: event.clientX, y: event.clientY });
      });
    });

    this.canvas.addEventListener("mouseleave", (event) => {
      if (this.currentScene) {
        this.currentScene.call("mouseleave", {
          x: event.clientX,
          y: event.clientY,
        });
      }

      this.actors.forEach((actor) => {
        actor.call("mouseleave", { x: event.clientX, y: event.clientY });
      });
    });

    this.canvas.addEventListener("wheel", (event) => {
      if (this.currentScene) {
        this.currentScene.call("wheel", {
          x: event.offsetX,
          y: event.offsetY,
          deltaY: event.deltaY,
        });
      }

      this.actors.forEach((actor) => {
        actor.call("wheel", { deltaY: event.deltaY });
      });
    });

    document.body.addEventListener("keydown", (event) => {
      if (this.currentScene) {
        this.currentScene.call("keydown", { key: event.key });
      }

      this.actors.forEach((actor) => {
        actor.call("keydown", { key: event.key });
      });
    });

    document.body.addEventListener("keyup", (event) => {
      if (this.currentScene) {
        this.currentScene.call("keyup", { key: event.key });
      }

      this.actors.forEach((actor) => {
        actor.call("keyup", { key: event.key });
      });
    });
    document.addEventListener("contextmenu", (event) => event.preventDefault());

    let promise = this.loadAssetPromise(options.assetList);

    promise.then((res) => {
      console.log("All assets loaded...");

      //Update HTML & show canvas
      document.getElementById("loading_element").style.display = "none";
      document.getElementById("canvas").removeAttribute("hidden");
      window.requestAnimationFrame(() => {
        this.gameLoop();
      });

      // Call the callback loop, now we can progress with adding actors,scenes,ect.
      callback();
    });

    // Initialize our global network events
    NetworkEvents.on({
      eventName: "setScene",
      callback: (data) => {
        this.setScene(data["scene"]);
      },
      globalEvent: true,
    });
    NetworkEvents.on({
      eventName: "messageBox",
      callback: (data) => {
        //{"event":"messageBox","messageName":"gameInProgress","message":"Error: Game in progress!"}
        const message = data["message"];
        alert(message);
      },
      globalEvent: true,
    });
  }

  public static gameLoop() {
    if (!this.runGameLoop) return;

    this.canvasContext.fillRect(0, 0, this.canvas.width, this.canvas.height);

    if (Date.now() - this.lastTimeUpdate >= 1000) {
      this.fps = this.countedFrames;
      this.lastTimeUpdate = Date.now();
      this.countedFrames = 0;
    }

    // Call the gameloop for the current scene
    this.currentScene.gameLoop();

    this.drawText({
      text: "FPS: " + this.fps,
      x: 0,
      y: 10,
      color: "black",
      font: "12px sans",
    });

    this.countedFrames++;
    window.requestAnimationFrame(() => {
      this.gameLoop();
    });
  }

  public static loadAssetPromise(assetList): Promise<unknown> {
    let imagesLoaded: number = 0;

    const resultPromise = new Promise((resolve, reject) => {
      for (let index in assetList) {
        let image = new Image();
        image.onload = () => {
          imagesLoaded++;
          console.log("Loaded: " + assetList[index]);
          if (imagesLoaded == assetList.length) {
            // We loaded all images, resolve the promise
            resolve(0);
          }
        };
        image.src = assetList[index];
        this.images.push(image);
      }
    });

    return resultPromise;
  }

  public static addScene(sceneName: string, scene: Scene) {
    this.scenes.set(sceneName, scene);
  }

  public static setScene(sceneName: string) {
    this.actors = [];

    const newScene = this.scenes.get(sceneName);

    if (this.currentScene != null) {
      this.currentScene.onDestroyed(newScene);
    }

    this.currentScene = newScene;
    this.currentScene.onInitialize();
  }

  public static addActor(actor: Actor) {
    this.actors.push(actor);
    actor.onCreated();
  }

  public static removeActor(actor: Actor) {
    this.actors = this.actors.filter((element) => element !== actor);
    actor.onDestroyed();
  }

  public static drawImageFromActor(
    actor: Actor,
    context: CanvasRenderingContext2D
  ) {
    if (!actor.getImage()) {
      console.log(
        "Warning: Attempted to draw empty actor: " + actor.getWidth()
      );
      return;
    }

    const canvasContext = context;
    canvasContext.save();

    // Only apply camera to the Game's main canvas context.
    if (this.currentScene.getCamera() && canvasContext === this.canvasContext) {
      const zoom = this.currentScene.getCamera().getZoomAmount();
      const cameraX = this.currentScene.getCamera().getX();
      const cameraY = this.currentScene.getCamera().getY();
      canvasContext.setTransform(zoom, 0, 0, zoom, cameraX, cameraY);
    }

    if (actor.getSpriteRegion()) {
      const spriteX = parseInt(actor.getSpriteRegion().split(",")[0]) * 32;
      const spriteY = parseInt(actor.getSpriteRegion().split(",")[1]) * 32;
      canvasContext.drawImage(
        actor.getImage(),
        //TODO: Calculate sprite position
        spriteX,
        spriteY,
        32,
        32,
        actor.getX(),
        actor.getY(),
        actor.getWidth(),
        actor.getHeight()
      );
    } else {
      canvasContext.drawImage(
        actor.getImage(),
        actor.getX(),
        actor.getY(),
        actor.getWidth(),
        actor.getHeight()
      );
    }
    canvasContext.restore();
  }

  public static async waitUntilMeasureQueueIsEmpty(): Promise<void> {
    return new Promise((resolve) => {
      const interval = setInterval(() => {
        if (this.measureQueue.length < 1) {
          clearInterval(interval);
          resolve();
        }
      }, 10);
    });
  }

  public static async measureText(
    text: string,
    font: string
  ): Promise<[number, number]> {
    await this.waitUntilMeasureQueueIsEmpty(); // Wait for other measurements to complete, then continue..
    this.measureQueue.push(text);
    this.canvasContext.save();
    this.canvasContext.font = font ?? "24px serif";

    await document.fonts.ready; // Wait for the async function to complete, then measure text.s

    const metrics = this.canvasContext.measureText(text);
    let height =
      metrics.actualBoundingBoxAscent + metrics.actualBoundingBoxDescent;
    this.canvasContext.restore();
    //FIXME: This fails when we have a queue of the same text, support text & font simultaneously
    this.measureQueue = this.measureQueue.filter((element) => element !== text);

    return [metrics.width, height];
  }

  public static drawText(textOptions: TextOptions) {
    //FIXME: Use cache for meausring text..

    this.canvasContext.save();
    this.canvasContext.fillStyle = textOptions.color;
    this.canvasContext.font = textOptions.font ?? "24px sans-serif";
    this.canvasContext.shadowColor = textOptions.shadowColor ?? "white";
    //this.canvasContext.shadowBlur = textOptions.shadowBlur ?? 0; // FIXME: Find alternative that provides better performance
    this.canvasContext.lineWidth = textOptions.lineWidth ?? 0; // 4
    const xPos = textOptions.x;
    const yPos = textOptions.y;
    if (textOptions.lineWidth > 0) {
      this.canvasContext.strokeText(textOptions.text, xPos, yPos);
    }

    this.canvasContext.fillText(textOptions.text, xPos, yPos);

    this.canvasContext.restore();
  }

  public static drawRect({
    x,
    y,
    width,
    height,
    color,
    canvasContext,
  }: {
    x: number;
    y: number;
    width: number;
    height: number;
    color: string;
    canvasContext: CanvasRenderingContext2D;
  }) {
    canvasContext.save();
    canvasContext.fillStyle = color;
    canvasContext.fillRect(x, y, width, height);
    canvasContext.restore();
  }

  public static getImage(gameImage: GameImage) {
    return this.images[gameImage];
  }

  public static getHeight(): number {
    return this.canvas.height;
  }

  public static getWidth(): number {
    return this.canvas.width;
  }

  public static getCanvasContext() {
    return this.canvasContext;
  }

  public static getCanvas() {
    return this.canvas;
  }

  public static getCurrentScene() {
    return this.currentScene;
  }

  public static getMouseX() {
    return this.mouseX;
  }
  public static getMouseY() {
    return this.mouseY;
  }

  public static getRelativeMouseX() {
    return this.mouseX - (this.currentScene.getCamera()?.getX() ?? 0);
  }
  public static getRelativeMouseY() {
    return this.mouseY - (this.currentScene.getCamera()?.getY() ?? 0);
  }

  public static toggleGameLoop() {
    this.runGameLoop = !this.runGameLoop;
  }
}
