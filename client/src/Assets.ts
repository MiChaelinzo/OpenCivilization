//TODO: Have different objects/functions for sounds,spritesheets,images.
//TODO: Preload all images into textures
//TODO: Handle spritesheet into textures
//TODO: Preload all sounds

export const spritehseetSize = 20; //20x20

export enum GameImage {
  BUTTON,
  BUTTON_HOVERED,
  ICON_BUTTON,
  ICON_BUTTON_HOVERED,
  SPRITESHEET,
  RIVER,
  POPUP_BOX,
  DEBUG,
}

export enum SpriteRegion {
  WARRIOR = "0,1",
  ARCHER = "0,0",
  BUILDER = "1,0",
  CAMEL_ARCHER = "2,0",
  CARAVAN = "3,0",
  CATAPULT = "5,0",
  COMPOSITE_BOWMAN = "7,0",
  CROSSBOWMAN = "8,0",
  HORSEMAN = "10,0",
  ROMAN_LEGION = "11,0",
  SETTLER = "16,0",
  BLANK_TILE = "9,8",
  SHALLOW_OCEAN = "16,6",
  OCEAN = "5,8",
  FRESHWATER = "7,7",
  GRASS = "3,6",
  GRASS_HILL = "4,6",
  MOUNTAIN = "14,6",
  DESERT = "10,5",
  DESERT_HILL = "11,5",
  PLAINS = "1,7",
  PLAINS_HILL = "2,7",
  TUNDRA = "15,7",
  TUNDRA_HILL = "16,7",
  SNOW = "3,8",
  SNOW_HILL = "4,8",
  JUNGLE = "10,6",
  FOREST = "17,5",
  FLOODPLAINS = "16,5",
  CATTLE = "1,5",
  SHEEP = "8,7",
  FISH = "14,5",
  CRAB = "8,5",
  WHALES = "1,8",
  TURTLES = "18,7",
  HORSES = "6,6",
  COPPER = "4,5",
  IRON = "8,6",
  COTTON = "6,5",
  CITRUS = "17,6",
  OLIVES = "14,7",
  STONE = "13,7",
  CITY = "8,8",
  STAR = "0,3",
  HOVERED_TILE = "6,8",
  UNIT_SELECTION_TILE = "7,8",
  DEBUG1 = "3,11",
  DEBUG2 = "14,13",
  DEBUG3 = "17,13",
  UI_STATUSBAR = "4,3",
  UNIT_SELECTION_CIRCLE = "1,3",
  SCIENCE_ICON = "12,11",
  CULTURE_ICON = "10,12",
  GOLD_ICON = "17,12",
  FAITH_ICON = "2,13",
  TRADE_ICON = "5,14",
  SETTLE_ICON = "11,11",
}

export const assetList = [
  require("../assets/images/ui_button.png"),
  require("../assets/images/ui_button_hovered.png"),
  require("../assets/images/ui_icon_button.png"),
  require("../assets/images/ui_icon_button_hovered.png"),
  require("../assets/images/spritesheet.png"),
  require("../assets/images/river.png"),
  require("../assets/images/ui_popup_box.png"),
  require("../assets/images/debug.png"),
  require("../assets/images/font.png"),
  require("../assets/images/logo.png"),
];
