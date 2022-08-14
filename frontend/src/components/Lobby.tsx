import {
  useContext,
  useEffect,
  useState
} from 'react';
import ChatOutputMessage from "../domain/LobbyMessages/ChatOutput"
import { playerContext } from "../auth/PlayerProvider"
import useWebSocket from 'react-use-websocket';
import Game from '../domain/Game/Game';
import CreateGameOutput from '../domain/LobbyMessages/CreateGameOutput';
import Container from 'react-bootstrap/Container';
import Row from 'react-bootstrap/Row';
import Player from '../domain/Player';
import Badge from 'react-bootstrap/Badge';
import ChatBox from './ChatBox';
import Col from 'react-bootstrap/Col';
import { deserialize, deserializeArray } from 'class-transformer';
import GameList from './GameList';
import CreateRoomForm from './CreateRoomForm';
import Navigation from './Navigation';
import PlayerLeftLobby from '../domain/LobbyMessages/PlayerLeftLobby';
import PlayerJoinedLobby from '../domain/LobbyMessages/PlayerJoinedLobby';
import GeneralMessage from '../domain/LobbyMessages/GeneralMessage';
import { getWsBasePath } from '../util';

const Lobby = () => {

  const { player } = useContext(playerContext)
  const wsUrl =  `${getWsBasePath()}/lobby/${player.id}`

  const { sendJsonMessage, lastMessage, readyState} = useWebSocket(wsUrl);
  const [messageHistory, setMessageHistory] = useState<(ChatOutputMessage | GeneralMessage)[]>([]);
  const [games, setGames] = useState<Game[]>([]);
  const [playersInLobby, setPlayersInLobby] = useState<Player[]>([]);

  const playerHasRoom = (games: Game[]): boolean => games.some((game) => {
    return game.owner.id === player.id
  });


  useEffect(() => {
    if (lastMessage !== null) {
      let msg = JSON.parse(lastMessage.data)
      switch (msg.action) {
        case ChatOutputMessage.action:
          {
            setMessageHistory([...messageHistory, deserialize(ChatOutputMessage, JSON.stringify(msg))]);
            break;
          }
        case CreateGameOutput.action:
          {
            let gameCreatedMsg = deserialize(CreateGameOutput, JSON.stringify(msg));
      
            let gameCreatedNotification = new GeneralMessage(gameCreatedMsg.createdGame.owner.name + "created game")

            setGames(gameCreatedMsg.gameList)
            setMessageHistory((prev) => prev.concat(gameCreatedNotification));

            break;
          }
        case PlayerLeftLobby.action:
          {
            let playerLeft = deserialize(PlayerLeftLobby, JSON.stringify(msg))
            setMessageHistory((prev) => prev.concat(new GeneralMessage(playerLeft.player.name + " left the lobby")));

            setPlayersInLobby(playerLeft.playersInLobby)
            break;
          }
        case PlayerJoinedLobby.action: {
          let playerJoined = deserialize(PlayerJoinedLobby, JSON.stringify(msg));
          setMessageHistory((prev) => prev.concat(new GeneralMessage(playerJoined.player.name + " joined the lobby")));

          setPlayersInLobby(playerJoined.playersInLobby)
          break;
        }
        default:
          console.log("ERROR")

      }
    }
  }, [lastMessage]);

  useEffect(() => {
    if (readyState === 1) {
      fetch("/list-players-in-lobby")
      .then(res => res.json())
      .then(
        (result) => {
          setPlayersInLobby(deserializeArray(Player, JSON.stringify(result)))
        }
      )
    }
 
  },[readyState])

  useEffect(() => {
    fetch("/list-games")
      .then(res => res.json())
      .then(
        (result) => {
          setGames(deserializeArray(Game, JSON.stringify(result)))
        }
      )
  },
    [])



  return (
    <Container className='main-container'>
      <Row className="p-2 ">
        <Col xs={12} >
          <Navigation>{}</Navigation>
        </Col>
      </Row>
      <Row className="p-2 h-75">
        <Col xs={12} md={5} className="h-100 chat-outer" >
          <ChatBox messageHistory={messageHistory} sendJsonMessage={sendJsonMessage}></ChatBox>
        </Col>
        <Col xs={12} md={7} className="h-100 games-outer">
          <Row style={{ maxHeight: '25%' }}>
            <Col>
              <div className="text-center">
                Players in lobby
              </div>
              <div>
                {playersInLobby.map((player) => (
                  <Badge key={player.id} pill bg="info" className="m-1">
                    {player.name}
                  </Badge>
                ))}
              </div>
            </Col>
          </Row>
          <Row>
            <Col>
              <div className="text-center">
                Games list
              </div>
            </Col>
          </Row>
          <Row>
            {playerHasRoom(games) ? null : <CreateRoomForm sendJsonMessage={sendJsonMessage}  ></CreateRoomForm>}
          </Row>
          <GameList games={games}></GameList>
        </Col>
      </Row>
    </Container>
  )
}

export { Lobby }
