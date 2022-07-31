import Col from 'react-bootstrap/Col';
import Row from 'react-bootstrap/Row';
import Card from 'react-bootstrap/Card';
import { Link } from "react-router-dom";
import Button from 'react-bootstrap/Button';
import Game from '../domain/Game/Game';
import { useContext } from 'react';
import { playerContext } from '../auth/PlayerProvider';



export default function GameList({ games }: { games: Game[] }) {

    const { player } = useContext(playerContext)


    function renderJoinButtonIfPossible(game: Game) {
        let thisPlayerRegisteredForGame = game.players.some((playerStone) => { return playerStone.player.id === player.id })

        if (thisPlayerRegisteredForGame) {
            return <div className="text-center">
                <Link to={`/game/${game.id}`} style={{ textDecoration: 'none' }}><Button variant="light">Play</Button></Link>
            </div>
        } else if (game.players.length <= 1 && !thisPlayerRegisteredForGame) {
            return <div className="text-center">
                <Link to={`/game/${game.id}`} style={{ textDecoration: 'none' }}><Button variant="light">Join</Button></Link>
            </div>
        } else {
            return null;
        }
    }

    return (
        <Row className="h-75 overflow-auto" style={{ maxHeight: '75%' }}>
            {games.map((game) => (
                <Col xs={4} key={game.id} className="p-2">
                    <Card bg='primary'>
                        <Card.Body>
                            <Card.Title>{game.name}</Card.Title>
                            <Card.Text>{game.players.length}/2 players registered</Card.Text>
                            <div>
                                Players
                                {game.players.map((data) => (
                                    <div key={data.player.id}>
                                        {data.player.name} - {data.stone === "black_stone" ? <div className="black-stone-icon"></div> : <div className="white-stone-icon"></div>}
                                    </div>
                                ))}
                            </div>
                            <div>
                                {renderJoinButtonIfPossible(game)}
                            </div>
                        </Card.Body>
                    </Card>
                </Col>
            ))}
        </Row>
    )
}