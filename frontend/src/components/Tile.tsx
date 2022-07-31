
import { useState } from "react";
import { SendJsonMessage } from "react-use-websocket/dist/lib/types";
import {Tile as TileObject} from "../domain/Game/GameBoard";

export default function Tile({ tile, sendJsonMessage, myStone }: { tile: TileObject, sendJsonMessage: SendJsonMessage, myStone: string }) {

    const [isHovered, setIsHovered] = useState<boolean>(false);

    function move() {
        let moveObje = {
            action: "move",
            position: {
                x: tile.position.x,
                y: tile.position.y
            }
        }

        sendJsonMessage(moveObje)
    }

    function render() {
        
        if (tile.stone === "black_stone") {
            return  <div onClick={move}  className="tile black stone"></div>
        } else if (tile.stone === "white_stone") {
            return  <div onClick={move} className="tile white stone"></div>
        } else {
            let emptyTileHoverClass = "";
            if (myStone === "black_stone" && isHovered) {
                emptyTileHoverClass = "stone-no-anim black"
            } else if (myStone === "white_stone" && isHovered) {
                emptyTileHoverClass = "stone-no-anim white"
            }
            return  <div onClick={move} className={`tile ${emptyTileHoverClass}`} onMouseEnter={() => setIsHovered(true)} onMouseLeave={() => setIsHovered(false)}></div>

        }
    }

    return (
        render()
    )
}