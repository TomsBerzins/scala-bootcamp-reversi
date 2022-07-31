import { useContext } from "react";
import Container from 'react-bootstrap/Container';
import Nav from 'react-bootstrap/Nav';
import Navbar from 'react-bootstrap/Navbar';
import { playerContext } from "../auth/PlayerProvider";


export default function Navigation({ children }: { children: any }) {

  const { player } = useContext(playerContext)

  function renderPlayerStatus() {
    if (player.name != null) {
      return <Navbar.Collapse className="justify-content-end">
        <Navbar.Text>
          Signed in as: {player.name}
        </Navbar.Text>
      </Navbar.Collapse>
    }

    return null;
  }

  return (
    <Navbar bg="light">
      <Container>
        <Navbar.Brand >Reversi online</Navbar.Brand>
        <Nav className="me-auto">
          {children}
        </Nav>
        {renderPlayerStatus()}
      </Container>
    </Navbar>
  )
}