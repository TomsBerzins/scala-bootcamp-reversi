import { useContext, useEffect, useState } from "react";
import { Button, Col, Container, Nav, Row } from "react-bootstrap";
import { Link, useParams } from "react-router-dom";
import useWebSocket from "react-use-websocket";
import { playerContext } from "../auth/PlayerProvider";
import Navigation from "./Navigation";
import GameNotification from "./GameNotification";
import gameNotificationObject from "../domain/Game/GameNotification"
import { plainToInstance } from "class-transformer";
import Player from "../domain/Player";
import {Tile as TileObject } from "../domain/Game/GameBoard";
import GameBoard, { Position } from "../domain/Game/GameBoard";
import Tile from "./Tile";
import { PlayerStoneMap } from "../domain/Game/Game";
import Alert from 'react-bootstrap/Alert';
import Modal from 'react-bootstrap/Modal';
import {GameStarted, InvalidMove, WaitingForOpponent, GameEnded} from '../domain/GameMessages/GeneralMessages';
import PlayerJoinedGame from "../domain/GameMessages/PlayerJoinedGame";
import PlayerNextToMove from "../domain/GameMessages/PlayerNextToMove";
import PlayerMoved from "../domain/GameMessages/PlayerMoved";
import PlayerLeftGame from "../domain/GameMessages/PlayerLeftGame";
import { getWsBasePath } from "../util";


export default function Game() {

    const gameParams = useParams();

    function getStartingTiles() {
        var tiles: TileObject[] = []
        Array.from(Array(8).keys()).forEach((row) => {
            Array.from(Array(8).keys()).forEach((col) => {

                let stone = null;
                if ((row === 3 && col === 3) || (row === 4 && col === 4)) {
                    stone = "white_stone";
                } else if ((row === 3 && col === 4) || (row === 4 && col === 3)) {
                    stone = "black_stone";
                }
                tiles.push(new TileObject(new Position(row, col), stone))
            })
        })

        return tiles;
    }

    const { player } = useContext(playerContext)
    const [notifications, setNotifications] = useState<gameNotificationObject[]>([])
    const [tiles, setTiles] = useState<TileObject[]>(getStartingTiles())
    const [playerToMove, setPlayerToMove] = useState<[Player, string] | null>(null)
    const [myStone, setMyStone] = useState<string>("")
    const [showError, setShowError] = useState(false);
    const [gameEnded, setGameEnded] = useState(false);

    const wsUrl =  `${getWsBasePath()}/game/${gameParams.id}/${player.id}`
    const { sendJsonMessage, lastMessage } = useWebSocket(wsUrl, { onError: (e) => setShowError(true) });


    function renderScore() {
        let blackScore = 0;
        let whiteScore = 0;
        tiles.forEach((tile) => {
            if (tile.stone !== null) {
                if (tile.stone === "black_stone") {
                    blackScore = ++blackScore;
                } else {
                    whiteScore = ++whiteScore;
                }
            }
        })

        return (
            <>
                <Row>
                    <Col className="d-flex justify-content-center">
                        Points
                    </Col>
                </Row>
                <Row style={{ backgroundColor: "whitesmoke" }}>
                    <Col>
                        <Row>
                            <Col className="score-board-tile">
                                <div className="stone white tile" ></div>
                            </Col>
                            <Col className="score-board-points">
                                <div>{whiteScore}</div>
                            </Col>
                        </Row>
                    </Col>
                    <Col>
                        <Row>
                            <Col className="score-board-points">
                                <div>{blackScore}</div>
                            </Col>
                            <Col className="score-board-tile">
                                <div className="stone black tile" ></div>
                            </Col>
                        </Row>
                    </Col>
                </Row>
            </>
        )
    }

    function renderTile(x: number, y: number) {
        let tile = tiles.find((tile) => { return tile.position.x === x && tile.position.y === y });
        if (tile !== undefined) {
            return <Tile tile={tile} sendJsonMessage={sendJsonMessage} myStone={myStone}></Tile>
        } else {
            return null
        }
    }

    function renderCurrentMoveInformation() {

        let currentMoveText;
        let currentMoveAlertVariant = "dark";

        if (gameEnded) {
            currentMoveText = "Game has ended"
        } else if (playerToMove !== null) {
            if (playerToMove?.[0].id === player.id) {
                currentMoveText = "It's your move!"
                currentMoveAlertVariant="success"
            } else {
                currentMoveText = `It's your opponents, ${playerToMove?.[0].name} move`
            }
        } else {
            currentMoveText = "Game not started yet, waiting for other player to join"
        }
        return <Alert variant={currentMoveAlertVariant} className="text-center">{currentMoveText}</Alert>


    }

    function getPlayerStone(playerToStoneMap: PlayerStoneMap[], playerId: string) {
        let playerToMoveStone = playerToStoneMap.filter((playerToStone) => {
            return playerToStone.player.id === playerId
        })
        return playerToMoveStone.pop()?.stone || "";
    }

    useEffect(() => {
        if (lastMessage !== null) {
            let msg = JSON.parse(lastMessage.data);
            console.log(msg);
            switch (msg.action) {
                case PlayerJoinedGame.action:
                    {
                        let playerJoinedGameMsg = plainToInstance(PlayerJoinedGame, msg);
                        // @ts-ignore
                        let notification = new gameNotificationObject(`Player ${playerJoinedGameMsg.player.name} joined!`, "info");
                        setNotifications((prev) => prev.concat(notification));
                        break;
                    }
                case PlayerLeftGame.action:
                    {
                        let playerLeftGameMsg = plainToInstance(PlayerLeftGame, msg);
                        // @ts-ignore
                        let notification = new gameNotificationObject(`Player ${playerLeftGameMsg.player.name} left!`, "info");
                        setNotifications((prev) => prev.concat(notification));
                        break;
                    }    
                case GameStarted.action:
                case InvalidMove.action:
                    {
                        // @ts-ignore
                        let generalMessage = new gameNotificationObject(msg.message, "info");
                        setNotifications((prev) => prev.concat(generalMessage));
                        break;
                    }
                case WaitingForOpponent.action:
                    {
                        // @ts-ignore
                        let notification = new gameNotificationObject(msg.message, "info");
                        setNotifications((prev) => prev.concat(notification));
                        setPlayerToMove(null);
                        break;
                    }
                case PlayerNextToMove.action:
                    {
                        let playerNextToMoveMsg = plainToInstance(PlayerNextToMove, msg);
                        // @ts-ignore
                        setMyStone(getPlayerStone(playerNextToMoveMsg.gameBoard.playerToStoneMap, player.id));

                        // @ts-ignore
                        setTiles(playerNextToMoveMsg.gameBoard.board);

                        // @ts-ignore
                        setPlayerToMove([playerNextToMoveMsg.player, getPlayerStone(playerNextToMoveMsg.gameBoard.playerToStoneMap, playerNextToMoveMsg.player.id)]);
                        break;
                    }
                case PlayerMoved.action: {

                    let playerMoved = plainToInstance(PlayerMoved, msg);

                    // @ts-ignore
                    if (playerMoved.player.id !== player.id) {
                        // @ts-ignore
                        let notification = new gameNotificationObject(`Player ${playerMoved.player.name} moved`, "info");
                        setNotifications((prev) => prev.concat(notification));
                    }

                    // @ts-ignore
                    let playerToMove = playerMoved.gameBoard.playerToStoneMap.filter((playerToStone) => {
                        // @ts-ignore
                        return playerToStone.player.id !== playerMoved.player.id
                    }).pop()?.player

                    if (playerToMove !== undefined) {
                        // @ts-ignore
                        setPlayerToMove([playerToMove, getPlayerStone(playerMoved.gameBoard.playerToStoneMap, playerToMove.id)]);
                    }

                    // @ts-ignore
                    setTiles(playerMoved.gameBoard.board);
                    break;
                }
                case GameEnded.action: {
                    // @ts-ignore
                    let notification = new gameNotificationObject(msg.message, "info");
                    setNotifications((prev) => prev.concat(notification));
                    setGameEnded(true);
                    break;
                }
                default:
                    console.log("ERRRoR");
                    console.log(msg);
                    console.log("ERROR")

            }
        }
    }, [lastMessage, player]);

    return (
        <Container className="main-container">
            <Row className="p-2 ">
                <Col xs={12} >
                    <Navigation><Nav.Link href="/">Lobby</Nav.Link></Navigation>
                </Col>
            </Row>
            <Row>
                <Col>
                    <Modal show={showError} >
                        <Modal.Header >
                            <Modal.Title>Error</Modal.Title>
                        </Modal.Header>
                        <Modal.Body>This game doesnt exist or has already ended.</Modal.Body>
                        <Modal.Footer>
                            <Link to={"/"} style={{ textDecoration: 'none' }}><Button variant="light">Head back to lobby</Button></Link>
                        </Modal.Footer>
                    </Modal>
                </Col>
            </Row>
            <Row>
                <Col xs={{ offset: 2 }}>
                    <Row>
                        <Col className="p-3">
                            {renderScore()}
                        </Col>
                    </Row>
                    <Row>
                        <Col xs={{offset: 3, span: 6}}>
                        {renderCurrentMoveInformation()}
                        </Col>
                    </Row>
                    <div >
                        <table className="board-tiles board" style={{ borderColor: "#000" }} >
                            <tbody>
                                {[0, 1, 2, 3, 4, 5, 6, 7].map((y) => (
                                    <tr key={y}>
                                        {[0, 1, 2, 3, 4, 5, 6, 7].map((x) => (
                                            <td key={x}>{renderTile(x, y)}</td>
                                        ))}
                                    </tr>)
                                )}
                            </tbody>
                        </table>
                    </div>
                </Col>
                <Col xs={12} lg={3}>
                    {notifications.map((notification, idx) => (
                        <GameNotification key={idx} message={notification.message}></GameNotification>
                    ))}
                </Col>
            </Row>
            <Row>
                a
            </Row>
        </Container>
    )
}

